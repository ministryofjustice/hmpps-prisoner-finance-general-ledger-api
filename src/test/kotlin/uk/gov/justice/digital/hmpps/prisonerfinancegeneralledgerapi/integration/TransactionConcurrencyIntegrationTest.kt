package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.ExchangeResult
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class TransactionConcurrencyIntegrationTest(
  @Autowired
  val transactionDataRepository: TransactionDataRepository,
) : IntegrationTestBase() {

/**
   * Helper to run two tasks in parallel starting at the exact same moment.
   * Handles thread cleanup and propagates exceptions to the main test thread.
   */
  private fun executeInParallel(task1: () -> ExchangeResult, task2: () -> ExchangeResult): List<ExchangeResult> {
    val executor = Executors.newFixedThreadPool(2)
    val latch = CountDownLatch(1)

    return try {
      val futures = listOf(task1, task2).map { task ->
        CompletableFuture.supplyAsync({
          try {
            latch.await() // Wait for signal
          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Thread interrupted", e)
          }
          task() // The last expression is returned
        }, executor)
      }

      latch.countDown() // Start both threads

      // join() waits for the future to complete and returns its result
      futures.map { it.join() }
    } finally {
      executor.shutdown()
    }
  }

  private fun postTransaction(request: CreateTransactionRequest, idempotencyKey: UUID): ExchangeResult = webTestClient.post()
    .uri("/transactions")
    .contentType(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
    .headers(setIdempotencyKey(idempotencyKey))
    .bodyValue(request)
    .exchange()
    .expectBody(String::class.java)
    .returnResult()

  lateinit var prisonAccount: AccountResponse
  lateinit var prisonSubAccount: SubAccountResponse

  lateinit var prisonerAccount: AccountResponse
  lateinit var prisonerSubAccount: SubAccountResponse

  @BeforeEach
  fun setup() {
    prisonAccount = integrationTestHelpers.createAccount(
      reference = "LEI",
      accountType = AccountType.PRISON,
    )

    prisonSubAccount = integrationTestHelpers.createSubAccount(
      accountId = prisonAccount.id,
      subAccountReference = "CANT:1001",
    )

    prisonerAccount = integrationTestHelpers.createAccount(
      reference = "ABCXZ123",
      accountType = AccountType.PRISONER,
    )

    prisonerSubAccount = integrationTestHelpers.createSubAccount(
      accountId = prisonerAccount.id,
      subAccountReference = "CASH",
    )
  }

  @Test
  fun `Should return 200 AND 201 when 2 transactions with the same Idempotency Key are posted at the exact same time`() {
    val idempotencyKey = UUID.randomUUID()
    val txRequest = CreateTransactionRequest(
      reference = "TEST",
      description = "TEST",
      timestamp = Instant.now(),
      amount = 10,
      entrySequence = 1,
      postings = listOf(
        CreatePostingRequest(
          subAccountId = prisonSubAccount.id,
          type = PostingType.DR,
          amount = 10,
          entrySequence = 1,
        ),
        CreatePostingRequest(
          subAccountId = prisonerSubAccount.id,
          type = PostingType.CR,
          amount = 10,
          entrySequence = 2,
        ),
      ),
    )

    val responses = executeInParallel(
      { postTransaction(txRequest, idempotencyKey) },
      { postTransaction(txRequest, idempotencyKey) },
    )

    val statusCodes = responses.map { it.status }
    assertThat(statusCodes).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.OK)

    val createdResponse = responses.single { it.status == HttpStatus.CREATED }
    val conflictResponse = responses.single { it.status == HttpStatus.OK }

    val createdResponseBody = createdResponse.responseBodyContent!!
    val createdJsonString = String(createdResponseBody, Charsets.UTF_8)
    val createdId = JsonPath.read<String>(createdJsonString, "$.id")

    val conflictResponseBody = conflictResponse.responseBodyContent!!
    val conflictJsonString = String(conflictResponseBody, Charsets.UTF_8)
    val conflictId = JsonPath.read<String>(conflictJsonString, "$.id")

    assertThat(createdId).isEqualTo(conflictId)

    val transactions = transactionDataRepository.findAll()
    assertThat(transactions).hasSize(1)
  }
}
