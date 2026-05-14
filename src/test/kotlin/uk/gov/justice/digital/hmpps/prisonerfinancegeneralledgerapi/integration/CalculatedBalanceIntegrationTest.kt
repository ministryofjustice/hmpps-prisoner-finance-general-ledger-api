package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.SqsQueues
import java.time.Instant
import java.util.UUID

class CalculatedBalanceIntegrationTest : IntegrationTestBase() {
  @Transactional
  @BeforeEach
  fun resetDB() {
    integrationTestHelpers.clearDB()
  }

  @Nested
  inner class SubAccountCalculatedBalance {

    var accounts: MutableList<AccountResponse> = mutableListOf()
    var subAccounts: MutableList<SubAccountResponse> = mutableListOf()

    @BeforeEach
    fun setup() {
      for (i in 0..2) {
        val accountResponseBody = integrationTestHelpers.createAccount("TEST_ACCOUNT_$i", AccountType.PRISONER)
        accounts.add(accountResponseBody)
      }

      for (account in accounts) {
        val subAccountResponseBody = integrationTestHelpers.createSubAccount(account.id, "TEST_SUB_ACCOUNT_$account.id")
        subAccounts.add(subAccountResponseBody)
      }
    }

    @Test
    fun `Should calculate balances when multiple transactions are posted`() {
      val amountFirst = 77L
      val amountSecond = 27L

      // txn 1
      val createPostingRequestsFirst: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = amountFirst, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = amountFirst, entrySequence = 2),
      )
      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = amountFirst,
            timestamp = Instant.now(),
            postings = createPostingRequestsFirst,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      // txn 2
      val createPostingRequestsSecond: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = amountSecond, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.CR, amount = amountSecond, entrySequence = 2),
      )
      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = amountSecond,
            timestamp = Instant.now(),
            postings = createPostingRequestsSecond,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE, hmppsQueueService)

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accounts[0].id}/statement?subAccountId=${subAccounts[0].id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].amount).isEqualTo(amountSecond)
      assertThat(content[0].postingBalance).isEqualTo(amountFirst - amountSecond)
      assertThat(content[1].amount).isEqualTo(amountFirst)
      assertThat(content[1].postingBalance).isEqualTo(amountFirst)
    }

    @Test
    fun `Should calculate balances after a transaction is posted`() {
      val amount = 77L
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = amount, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = amount, entrySequence = 2),
      )

      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = amount,
            timestamp = Instant.now(),
            postings = createPostingRequests,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE, hmppsQueueService)

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accounts[0].id}/statement?subAccountId=${subAccounts[0].id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amount)
      assertThat(content[0].postingBalance).isEqualTo(amount)
    }

    @Test
    fun `Should calculate balances after a transaction is posted and account for previous statement balance`() {
      val amount = 77L
      val statementBalanceAmount = 100L

      webTestClient.post()
        .uri("/sub-accounts/${subAccounts[0].id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = statementBalanceAmount, balanceDateTime = Instant.now().minusSeconds(99)))
        .exchange()
        .expectStatus().isCreated
        .expectBody<StatementBalanceResponse>()
        .returnResult().responseBody!!

      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = amount, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = amount, entrySequence = 2),
      )

      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = amount,
            timestamp = Instant.now(),
            postings = createPostingRequests,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE, hmppsQueueService)

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accounts[0].id}/statement?subAccountId=${subAccounts[0].id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amount)
      assertThat(content[0].postingBalance).isEqualTo(statementBalanceAmount + amount)
    }

    @Test
    fun `Should re-calculate balances when a posting is created in the past`() {
      val amountFirst = 77L
      val amountSecond = 27L

      // txn 1
      val createPostingRequestsFirst: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = amountFirst, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = amountFirst, entrySequence = 2),
      )
      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = amountFirst,
            timestamp = Instant.now(),
            postings = createPostingRequestsFirst,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE, hmppsQueueService)

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accounts[0].id}/statement?subAccountId=${subAccounts[0].id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].postingBalance).isEqualTo(amountFirst)

      // txn 2 in the past
      val createPostingRequestsSecond: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = amountSecond, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = amountSecond, entrySequence = 2),
      )
      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = amountSecond,
            timestamp = Instant.now().minusSeconds(120),
            postings = createPostingRequestsSecond,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE, hmppsQueueService)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accounts[0].id}/statement?subAccountId=${subAccounts[0].id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].postingBalance).isEqualTo(amountFirst + amountSecond)
      assertThat(content[1].amount).isEqualTo(amountSecond)
      assertThat(content[1].postingBalance).isEqualTo(amountSecond)
    }

    @Test
    fun `Should re-calculate balances when a statement balance is created in the past`() {
      val amountFirst = 77L
      val amountStatementBalance = 27L

      // TXN
      val createPostingRequestsFirst: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = amountFirst, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = amountFirst, entrySequence = 2),
      )
      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = amountFirst,
            timestamp = Instant.now(),
            postings = createPostingRequestsFirst,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE, hmppsQueueService)

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accounts[0].id}/statement?subAccountId=${subAccounts[0].id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].postingBalance).isEqualTo(amountFirst)

      // Insert statement balance
      val statementBalanceResponse = webTestClient.post()
        .uri("/sub-accounts/${subAccounts[0].id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = amountStatementBalance, balanceDateTime = Instant.now().minusSeconds(120)))
        .exchange()
        .expectStatus().isCreated
        .expectBody<StatementBalanceResponse>()
        .returnResult().responseBody!!

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE, hmppsQueueService)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accounts[0].id}/statement?subAccountId=${subAccounts[0].id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].postingBalance).isEqualTo(amountStatementBalance + amountFirst)
    }
  }
}
