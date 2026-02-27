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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import java.time.Instant
import java.util.UUID

class TransactionIntegrationTest : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun resetDB() {
    integrationTestHelpers.clearDB()
  }

  @Nested
  inner class PostTransaction {

    var accounts: MutableList<AccountResponse> = mutableListOf()
    var subAccounts: MutableList<SubAccountResponse> = mutableListOf()

    @BeforeEach
    fun setup() {
      for (i in 0..3) {
        val accountResponseBody = integrationTestHelpers.createAccount("TEST_ACCOUNT_$i", AccountType.PRISONER)
        accounts.add(accountResponseBody)
      }

      for (account in accounts) {
        val subAccountResponseBody = integrationTestHelpers.createSubAccount(account.id, "TEST_SUB_ACCOUNT_$account.id")
        subAccounts.add(subAccountResponseBody)
      }
    }

    @Test
    fun `return a 201 when sent a valid transaction with one to one postings`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 1L),
      )

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      assertThat(transactionResponseBody.amount).isEqualTo(1L)
      assertThat(transactionResponseBody.reference).isEqualTo("TX")
      assertThat(transactionResponseBody.description).isEqualTo("DESCRIPTION")
      assertThat(transactionResponseBody.postings.size).isEqualTo(2)
      assertThat(transactionResponseBody.createdAt).isEqualTo(transactionResponseBody.postings[0].createdAt)
      assertThat(transactionResponseBody.postings[0].type).isEqualTo(PostingType.CR)
      assertThat(transactionResponseBody.postings[1].type).isEqualTo(PostingType.DR)
      assertThat(transactionResponseBody.postings[0].amount).isEqualTo(1L)
      assertThat(transactionResponseBody.postings[1].amount).isEqualTo(1L)
    }

    @Test
    fun `return a 201 when sent a valid transaction with many credits for one debit`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = 2L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[2].id, type = PostingType.CR, amount = 1L),
      )

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 2L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      assertThat(transactionResponseBody.postings[0].type).isEqualTo(PostingType.DR)
      assertThat(transactionResponseBody.postings[1].type).isEqualTo(PostingType.CR)
      assertThat(transactionResponseBody.postings[2].type).isEqualTo(PostingType.CR)
      assertThat(transactionResponseBody.postings[0].amount).isEqualTo(2L)
      assertThat(transactionResponseBody.postings[1].amount).isEqualTo(1L)
      assertThat(transactionResponseBody.postings[2].amount).isEqualTo(1L)
    }

    @Test
    fun `return a 201 when sent a valid transaction with one credit to many debits`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = 2L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[2].id, type = PostingType.CR, amount = 3L),
      )

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 3L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      assertThat(transactionResponseBody.postings[0].type).isEqualTo(PostingType.DR)
      assertThat(transactionResponseBody.postings[1].type).isEqualTo(PostingType.DR)
      assertThat(transactionResponseBody.postings[2].type).isEqualTo(PostingType.CR)
      assertThat(transactionResponseBody.postings[0].amount).isEqualTo(2L)
      assertThat(transactionResponseBody.postings[1].amount).isEqualTo(1L)
      assertThat(transactionResponseBody.postings[2].amount).isEqualTo(3L)
    }

    @Test
    fun `return a 200 and the corresponding transaction when the idempotency key has already been used`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 1L),
      )

      val idempotencyKey = UUID.randomUUID()

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      val repeatedTransactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isOk
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      assertThat(repeatedTransactionResponseBody.id).isEqualTo(transactionResponseBody.id)
    }

    @Test
    fun `Should return a 400 when no Idempotency Key header is sent`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 1L),
      )

      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `return a 400 when sent a valid transaction with many to many postings`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[2].id, type = PostingType.DR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[3].id, type = PostingType.CR, amount = 1L),
      )

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 2L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `return a 400 when sent a valid transaction with an invalid sub-account UUID`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.DR, amount = 1L),
      )

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when sent a transaction with fewer than two postings`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = 1L),
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
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when transaction posting credits do not match transaction amount`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 100L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 100L),
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
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when transaction posting credits and posting debits do not balance`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 100L),
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
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when transaction amount is negative`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = -1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = -1L),
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
            amount = -1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when transaction postings contain a negative amount`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 2L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.CR, amount = -1L),
        CreatePostingRequest(subAccountId = subAccounts[2].id, type = PostingType.DR, amount = 1L),
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
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 401 when requesting account without authorisation headers`() {
      webTestClient.post()
        .uri("/transactions")
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting account with incorrect role`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 1L),
      )

      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .headers(setIdempotencyKey(UUID.randomUUID()))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION",
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class GetTransaction {

    var accounts: MutableList<AccountResponse> = mutableListOf()
    var subAccounts: MutableList<SubAccountResponse> = mutableListOf()
    lateinit var transaction: TransactionResponse

    @BeforeEach
    fun setUp() {
      for (i in 2 downTo 0 step 1) {
        accounts.add(integrationTestHelpers.createAccount("$i", AccountType.PRISONER))
      }

      for (account in accounts) {
        subAccounts.add(integrationTestHelpers.createSubAccount(account.id, account.reference))
      }

      transaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = subAccounts[1].id,
        creditSubAccountId = subAccounts[0].id,
        transactionReference = "TX",
        description = "DESCRIPTION",
      )
    }

    @Test
    fun `should return 200 transaction with postings when sent a valid id`() {
      val transactionResponse = webTestClient.get()
        .uri("/transactions/${transaction.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      assertThat(transactionResponse.amount).isEqualTo(1L)
      assertThat(transactionResponse.description).isEqualTo("DESCRIPTION")
      assertThat(transactionResponse.reference).isEqualTo("TX")
      assertThat(transactionResponse.postings).hasSize(2)
      assertThat(transactionResponse.postings[0].amount).isEqualTo(1L)
      assertThat(transactionResponse.postings[0].subAccountID).isEqualTo(subAccounts[0].id)
      assertThat(transactionResponse.postings[0].type).isEqualTo(PostingType.CR)

      assertThat(transactionResponse.postings[1].amount).isEqualTo(1L)
      assertThat(transactionResponse.postings[1].subAccountID).isEqualTo(subAccounts[1].id)
      assertThat(transactionResponse.postings[1].type).isEqualTo(PostingType.DR)
    }

    @Test
    fun `should return 400 when invalid UUID is provided`() {
      webTestClient.get()
        .uri("/transactions/NOT_A_VALID_UUID")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `returns a 400 when the transaction description contains control characters`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 1L),
      )

      val idempotencyKey = UUID.randomUUID()

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .headers(setIdempotencyKey(idempotencyKey))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          CreateTransactionRequest(
            reference = "TX",
            description = "DESCRIPTION\u0000continued",
            amount = 1L,
            timestamp = Instant.now(),
            postings = createPostingRequests,
          ),
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 404 when sent a valid UUID that doesn't exist`() {
      val uuid = UUID.randomUUID()
      webTestClient.get()
        .uri("/transactions/$uuid")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return 401 when requesting transaction without authorisation headers`() {
      webTestClient.get()
        .uri("/transactions/${transaction.id}")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting transaction with incorrect role`() {
      webTestClient.get()
        .uri("/transactions/${transaction.id}")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class GetTransactionsForAccount {

    @Test
    fun `Should return 200 and an empty list when there are no transactions`() {
      val prisonerAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_PRISONER", AccountType.PRISONER)

      val responseBody = webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionListResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody).hasSize(0)
    }

    @Test
    fun `Should return 200 and a list of transactions`() {
      val prisonAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_PRISON", AccountType.PRISON)
      val prisonCanteen = integrationTestHelpers.createSubAccount(prisonAccount.id, "`1021:CANT")

      val prisonerAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_PRISONER", AccountType.PRISONER)
      val prisonerCash = integrationTestHelpers.createSubAccount(prisonerAccount.id, "CASH")

      val transaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 10L,
        debitSubAccountId = prisonerCash.id,
        creditSubAccountId = prisonCanteen.id,
        transactionReference = "VAPES",
        description = "test",
      )

      val responseBody = webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionListResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody).hasSize(1)
      assertThat(responseBody[0].id).isEqualTo(transaction.id)
      assertThat(responseBody[0].description).isEqualTo(transaction.description)
      responseBody[0].postings.forEach { posting ->
        if (posting.subAccount.id == prisonerCash.id) {
          assertThat(posting.amount).isEqualTo(transaction.amount)
        } else {
          assertThat(posting.amount).isEqualTo(transaction.amount)
        }
      }

      assertThat(responseBody[0].postings).hasSize(2)
      responseBody[0].postings.any { posting ->
        posting.type == PostingType.CR
      }
      responseBody[0].postings.any { posting ->
        posting.type == PostingType.DR
      }
    }

    @Test
    fun `Should return 200 and a list of transactions only for that prisoner order by timestamp descending`() {
      val prisonAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_PRISON", AccountType.PRISON)
      val prisonCanteen = integrationTestHelpers.createSubAccount(prisonAccount.id, "`1021:CANT")

      val prisonerOneAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_PRISONER_ONE", AccountType.PRISONER)
      val prisonerOneCash = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")

      val prisonerTwoAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_PRISONER_TWO", AccountType.PRISONER)
      val prisonerTwoCash = integrationTestHelpers.createSubAccount(prisonerTwoAccount.id, "CASH")

      val oneToOneTransaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 10L,
        debitSubAccountId = prisonerOneCash.id,
        creditSubAccountId = prisonCanteen.id,
        transactionReference = "VAPES",
        description = "BLUEBERRY",
      )

      val oneToManyTransaction = integrationTestHelpers.createOneToManyTransaction(
        10L,
        prisonCanteen.id,
        listOf(prisonerOneCash.id, prisonerTwoCash.id),
        "CANTEEN_REFUND",
      )

      val responseBody = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionListResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody).hasSize(2)
      assertThat(responseBody[0].id).isEqualTo(oneToManyTransaction.id)
      assertThat(responseBody[0].description).isEqualTo(oneToManyTransaction.description)

      val returnedOneToManyTransaction = responseBody[0]

      assertThat(returnedOneToManyTransaction.postings).hasSize(2)

      returnedOneToManyTransaction.postings.forEach { posting ->
        if (posting.subAccount.id == prisonerOneCash.id) {
          assertThat(posting.type).isEqualTo(PostingType.CR)
          assertThat(posting.amount).isEqualTo(10L)
        } else {
          assertThat(posting.type).isEqualTo(PostingType.DR)
          assertThat(posting.amount).isEqualTo(20L)
        }
      }

      assertThat(responseBody[1].id).isEqualTo(oneToOneTransaction.id)
      assertThat(responseBody[1].description).isEqualTo(oneToOneTransaction.description)
    }

    @Test
    fun `should return 400 when invalid UUID is provided`() {
      webTestClient.get()
        .uri("/accounts/NOT_A_VALID_UUID/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 403 when requesting transaction with incorrect role`() {
      webTestClient.get()
        .uri("/accounts/${UUID.randomUUID()}/transactions")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 404 when sent a valid UUID that doesn't exist`() {
      val uuid = UUID.randomUUID()
      webTestClient.get()
        .uri("/accounts/$uuid/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isNotFound
    }
  }
}
