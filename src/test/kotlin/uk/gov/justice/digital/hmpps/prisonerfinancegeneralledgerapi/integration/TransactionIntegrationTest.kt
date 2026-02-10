package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import java.time.LocalDateTime
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
      for (i in 3 downTo 0 step 1) {
        val accountResponseBody = integrationTestHelpers.createAccount("TEST_ACCOUNT_$i")
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
            timestamp = LocalDateTime.now(),
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
        accounts.add(integrationTestHelpers.createAccount("$i"))
      }

      for (account in accounts) {
        subAccounts.add(integrationTestHelpers.createSubAccount(account.id, account.reference))
      }

      transaction = integrationTestHelpers.createOneToOneTransaction(amount = 1L, debitSubAccountId = subAccounts[1].id, creditSubAccountId = subAccounts[0].id, transactionReference = "TX", description = "DESCRIPTION")
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
}
