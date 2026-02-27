package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.helpers

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.IdempotencyKeyDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Instant
import java.util.UUID

@TestConfiguration
class IntegrationTestHelpers(

  private val jwtAuthHelper: JwtAuthorisationHelper,
  private val idempotencyKeyDataRepository: IdempotencyKeyDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val transactionDataRepository: TransactionDataRepository,
  private val subAccountDataRepository: SubAccountDataRepository,
  private val accountDataRepository: AccountDataRepository,
) {

  lateinit var webTestClient: WebTestClient

  fun setWebClient(webClient: WebTestClient) {
    webTestClient = webClient
  }

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  internal fun setIdempotencyKey(
    key: UUID,
  ): (HttpHeaders) -> Unit = { it.set("Idempotency-Key", key.toString()) }

  fun createAccount(reference: String, accountType: AccountType): AccountResponse {
    val account = webTestClient.post()
      .uri("/accounts")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CreateAccountRequest(reference, accountType))
      .exchange()
      .expectBody<AccountResponse>()
      .returnResult()
      .responseBody!!

    return account
  }

  fun createSubAccount(accountId: UUID, subAccountReference: String): SubAccountResponse {
    val subAccount = webTestClient.post()
      .uri("/accounts/$accountId/sub-accounts")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CreateSubAccountRequest(subAccountReference = subAccountReference))
      .exchange()
      .expectBody<SubAccountResponse>()
      .returnResult()
      .responseBody!!
    return subAccount
  }

  fun createOneToManyTransaction(amountToCreditEachAccount: Long, debitSubAccountId: UUID, creditSubAccountIds: List<UUID>, transactionReference: String, description: String = ""): TransactionResponse {
    val postings = mutableListOf<CreatePostingRequest>(
      CreatePostingRequest(
        subAccountId = debitSubAccountId,
        amount = amountToCreditEachAccount * creditSubAccountIds.size,
        type = PostingType
          .DR,
      ),
    )
    for (creditSubAccountId in creditSubAccountIds) {
      postings.add(
        CreatePostingRequest(
          subAccountId = creditSubAccountId,
          amount = amountToCreditEachAccount,
          type = PostingType
            .CR,
        ),
      )
    }

    val transactionPayload = CreateTransactionRequest(
      reference = transactionReference,
      description = description,
      timestamp = Instant.now(),
      amount = amountToCreditEachAccount * creditSubAccountIds.size,
      postings = postings,
    )

    val transactionResponse = webTestClient.post().uri("/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(transactionPayload)
      .exchange()
      .expectStatus().isCreated
      .expectBody<TransactionResponse>()
      .returnResult()
      .responseBody!!

    return transactionResponse
  }

  fun createOneToOneTransaction(amount: Long, debitSubAccountId: UUID, creditSubAccountId: UUID, transactionReference: String, description: String = ""): TransactionResponse {
    val postings = listOf(
      CreatePostingRequest(
        subAccountId = creditSubAccountId,
        amount = amount,
        type = PostingType
          .CR,
      ),
      CreatePostingRequest(
        subAccountId = debitSubAccountId,
        amount = amount,
        type = PostingType
          .DR,
      ),
    )
    val transactionPayload = CreateTransactionRequest(
      reference = transactionReference,
      description = description,
      timestamp = Instant.now(),
      amount = amount,
      postings = postings,
    )

    val transactionResponse = webTestClient.post().uri("/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .headers(setIdempotencyKey(UUID.randomUUID()))
      .bodyValue(transactionPayload)
      .exchange()
      .expectStatus().isCreated
      .expectBody<TransactionResponse>()
      .returnResult()
      .responseBody!!

    return transactionResponse
  }

  fun clearDB() {
    idempotencyKeyDataRepository.deleteAllInBatch()
    statementBalanceDataRepository.deleteAllInBatch()
    postingsDataRepository.deleteAllInBatch()
    transactionDataRepository.deleteAllInBatch()
    subAccountDataRepository.deleteAllInBatch()
    accountDataRepository.deleteAllInBatch()
  }
}
