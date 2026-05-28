package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
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

class CalculatedBalanceIntegrationTest(
  @Autowired
  val postingBalanceDataRepository: PostingBalanceDataRepository,
) : IntegrationTestBase() {
  @Transactional
  @BeforeEach
  fun resetDB() {
    integrationTestHelpers.clearDB()

    accountPrisoner = integrationTestHelpers.createAccount("ABCXZ123", AccountType.PRISONER)
    subAccountPrisonerCash = integrationTestHelpers.createSubAccount(accountPrisoner.id, "CASH")
    subAccountPrisonerSpends = integrationTestHelpers.createSubAccount(accountPrisoner.id, "SPENDS")

    accountPrison = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)
    subAccountPrisonCanteen = integrationTestHelpers.createSubAccount(accountPrison.id, "CANT:1001")
  }

  lateinit var accountPrisoner: AccountResponse
  lateinit var subAccountPrisonerCash: SubAccountResponse
  lateinit var subAccountPrisonerSpends: SubAccountResponse
  lateinit var accountPrison: AccountResponse
  lateinit var subAccountPrisonCanteen: SubAccountResponse

  @Nested
  inner class AccountCalculatedBalance {

    @Test
    fun `Should calculate balances when multiple transactions are posted`() {
      val amountFirst = 77L
      val amountSecond = 27L

      // txn 1
      val createPostingRequestsFirst: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountFirst, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountFirst, entrySequence = 2),
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
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.DR, amount = amountSecond, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.CR, amount = amountSecond, entrySequence = 2),
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

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)

      assertThat(content[0].postingType).isEqualTo(PostingType.DR)
      assertThat(content[0].amount).isEqualTo(amountSecond)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst - amountSecond)
      assertThat(content[0].accountBalance).isEqualTo(amountFirst - amountSecond)

      assertThat(content[1].postingType).isEqualTo(PostingType.CR)
      assertThat(content[1].amount).isEqualTo(amountFirst)
      assertThat(content[1].subAccountBalance).isEqualTo(amountFirst)
      assertThat(content[1].accountBalance).isEqualTo(amountFirst)
    }

    @Test
    fun `Should calculate balances after a transaction is posted`() {
      val amount = 77L
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amount, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amount, entrySequence = 2),
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

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amount)
      assertThat(content[0].subAccountBalance).isEqualTo(amount)
      assertThat(content[0].accountBalance).isEqualTo(amount)
    }

    @Test
    fun `Should calculate balances after a transaction is posted and account for previous statement balance`() {
      val amount = 77L
      val statementBalanceAmount = 100L

      webTestClient.post()
        .uri("/sub-accounts/${subAccountPrisonerCash.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = statementBalanceAmount, balanceDateTime = Instant.now().minusSeconds(99)))
        .exchange()
        .expectStatus().isCreated
        .expectBody<StatementBalanceResponse>()
        .returnResult().responseBody!!

      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amount, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amount, entrySequence = 2),
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

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amount)
      assertThat(content[0].subAccountBalance).isEqualTo(statementBalanceAmount + amount)
      assertThat(content[0].accountBalance).isEqualTo(statementBalanceAmount + amount)
    }

    @Test
    fun `Should re-calculate balances when a posting is created in the past`() {
      val amountFirst = 77L
      val amountSecond = 27L

      // txn 1
      val createPostingRequestsFirst: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountFirst, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountFirst, entrySequence = 2),
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

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst)

      // txn 2 in the past
      val createPostingRequestsSecond: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountSecond, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountSecond, entrySequence = 2),
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

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst + amountSecond)
      assertThat(content[0].accountBalance).isEqualTo(amountFirst + amountSecond)

      assertThat(content[1].amount).isEqualTo(amountSecond)
      assertThat(content[1].subAccountBalance).isEqualTo(amountSecond)
      assertThat(content[1].accountBalance).isEqualTo(amountSecond)
    }

    @Test
    fun `Should re-calculate balances when a statement balance is created in the past`() {
      val amountFirst = 77L
      val amountStatementBalance = 27L

      // TXN
      val createPostingRequestsFirst: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountFirst, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountFirst, entrySequence = 2),
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

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst)
      assertThat(content[0].accountBalance).isEqualTo(amountFirst)

      // Insert statement balance
      val statementBalanceResponse = webTestClient.post()
        .uri("/sub-accounts/${subAccountPrisonerCash.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = amountStatementBalance, balanceDateTime = Instant.now().minusSeconds(120)))
        .exchange()
        .expectStatus().isCreated
        .expectBody<StatementBalanceResponse>()
        .returnResult().responseBody!!

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountStatementBalance + amountFirst)
      assertThat(content[0].accountBalance).isEqualTo(amountStatementBalance + amountFirst)
    }

    @Test
    fun `Should re-calculate all sub-account balances when a statement balance is created in the past for another sub-account`() {
      //  Cash first total is the posting amount
      //  Spends second total is the posting amount plus cash subAccount
      //
      //  Then I insert a migration for Spends before Cash
      //  I want to see Cash total balance update to include the new Spend balance

      // TXN CASH

      val transactionTimestamp = Instant.now()
      val amountCashTx = 10L
      val createPostingRequestsCashTransaction: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountCashTx, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountCashTx, entrySequence = 2),
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
            amount = amountCashTx,
            timestamp = transactionTimestamp,
            postings = createPostingRequestsCashTransaction,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      // TXN SPENDS
      val amountSpendsTx = 22L

      val createPostingRequestsSpendsTransaction: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerSpends.id, type = PostingType.CR, amount = amountSpendsTx, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountSpendsTx, entrySequence = 2),
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
            amount = amountSpendsTx,
            timestamp = transactionTimestamp.plusSeconds(20),
            postings = createPostingRequestsSpendsTransaction,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].subAccount.id).isEqualTo(subAccountPrisonerSpends.id)
      assertThat(content[0].amount).isEqualTo(amountSpendsTx)
      assertThat(content[0].subAccountBalance).isEqualTo(amountSpendsTx)
      assertThat(content[0].accountBalance).isEqualTo(amountSpendsTx + amountCashTx)

      assertThat(content[1].subAccount.id).isEqualTo(subAccountPrisonerCash.id)
      assertThat(content[1].amount).isEqualTo(amountCashTx)
      assertThat(content[1].subAccountBalance).isEqualTo(amountCashTx)
      assertThat(content[1].accountBalance).isEqualTo(amountCashTx)

      // Insert statement balance
      val amountStatementBalance = 27L
      val statementBalanceResponse = webTestClient.post()
        .uri("/sub-accounts/${subAccountPrisonerSpends.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = amountStatementBalance, balanceDateTime = Instant.now().minusSeconds(120)))
        .exchange()
        .expectStatus().isCreated
        .expectBody<StatementBalanceResponse>()
        .returnResult().responseBody!!

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].subAccount.id).isEqualTo(subAccountPrisonerSpends.id)
      assertThat(content[0].amount).isEqualTo(amountSpendsTx)
      assertThat(content[0].subAccountBalance).isEqualTo(amountSpendsTx + amountStatementBalance)
      assertThat(content[0].accountBalance).isEqualTo(amountSpendsTx + amountCashTx + amountStatementBalance)

      assertThat(content[1].subAccount.id).isEqualTo(subAccountPrisonerCash.id)
      assertThat(content[1].amount).isEqualTo(amountCashTx)
      assertThat(content[1].subAccountBalance).isEqualTo(amountCashTx)
      assertThat(content[1].accountBalance).isEqualTo(amountCashTx + amountStatementBalance)
    }

    @Test
    fun `Should not change total account balance when a subAccount transfer is posted`() {
      val amountCanteenTransaction = 77L
      var createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountCanteenTransaction, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountCanteenTransaction, entrySequence = 2),
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
            amount = amountCanteenTransaction,
            timestamp = Instant.now(),
            postings = createPostingRequests,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountCanteenTransaction)
      assertThat(content[0].subAccountBalance).isEqualTo(amountCanteenTransaction)
      assertThat(content[0].accountBalance).isEqualTo(amountCanteenTransaction)

      // sub acc transfer
      val amountSpendsToCash = 22L
      createPostingRequests = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerSpends.id, type = PostingType.DR, amount = amountSpendsToCash, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountSpendsToCash, entrySequence = 2),
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
            amount = amountSpendsToCash,
            timestamp = Instant.now(),
            postings = createPostingRequests,
            entrySequence = 1,
          ),
        )
        .exchange()
        .expectStatus().isCreated

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].postingType).isEqualTo(PostingType.CR)
      assertThat(content[0].amount).isEqualTo(amountSpendsToCash)
      assertThat(content[0].subAccountBalance).isEqualTo(amountCanteenTransaction + amountSpendsToCash)
      assertThat(content[0].accountBalance).isEqualTo(amountCanteenTransaction)

      assertThat(content[1].postingType).isEqualTo(PostingType.CR)
      assertThat(content[1].amount).isEqualTo(amountCanteenTransaction)
      assertThat(content[1].subAccountBalance).isEqualTo(amountCanteenTransaction)
      assertThat(content[1].accountBalance).isEqualTo(amountCanteenTransaction)
    }
  }

  @Nested
  inner class MigrateBalancesTest {

    @Test
    fun `should migrate the accounts balances`() {
      val amountFirst = 77L
      val amountSecond = 27L

      // txn 1
      val createPostingRequestsFirst: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountFirst, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountFirst, entrySequence = 2),
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
        CreatePostingRequest(subAccountId = subAccountPrisonerCash.id, type = PostingType.CR, amount = amountSecond, entrySequence = 1),
        CreatePostingRequest(subAccountId = subAccountPrisonCanteen.id, type = PostingType.DR, amount = amountSecond, entrySequence = 2),
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

      // waiting for sqs to empty and then clearing balances
      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)
      postingBalanceDataRepository.deleteAllInBatch()

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].subAccountBalance).isNull()
      assertThat(content[0].accountBalance).isNull()

      assertThat(content[1].subAccountBalance).isNull()
      assertThat(content[1].accountBalance).isNull()

      webTestClient.post()
        .uri("/migrate/subAccountBalances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .returnResult()

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${accountPrisoner.id}/statement?subAccountId=${subAccountPrisonerCash.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponse.content

      assertThat(content[0].amount).isEqualTo(amountSecond)
      assertThat(content[0].subAccountBalance).isEqualTo(amountSecond + amountFirst)
      assertThat(content[0].accountBalance).isEqualTo(amountSecond + amountFirst)

      assertThat(content[1].amount).isEqualTo(amountFirst)
      assertThat(content[1].subAccountBalance).isEqualTo(amountFirst)
      assertThat(content[1].accountBalance).isEqualTo(amountFirst)
    }
  }
}
