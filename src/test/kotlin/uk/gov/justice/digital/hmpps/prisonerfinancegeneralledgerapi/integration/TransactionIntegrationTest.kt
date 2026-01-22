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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import java.time.LocalDateTime
import java.util.UUID

class TransactionIntegrationTest @Autowired constructor(
  var transactionDataRepository: TransactionDataRepository,
  var subAccountDataRepository: SubAccountDataRepository,
  var postingsDataRepository: PostingsDataRepository,
  var accountDataRepository: AccountDataRepository,
) : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun resetDB() {
    postingsDataRepository.deleteAllInBatch()
    transactionDataRepository.deleteAllInBatch()
    subAccountDataRepository.deleteAllInBatch()
    accountDataRepository.deleteAllInBatch()
  }

  @Nested
  inner class PostTransaction {

    var accounts: MutableList<AccountResponse> = mutableListOf()
    var subAccounts: MutableList<SubAccountResponse> = mutableListOf()

    @BeforeEach
    fun setup() {

      for (i in 3 downTo 0 step 1) {
        val accountResponseBody = webTestClient.post()
          .uri("/accounts")
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CreateAccountRequest("$i"))
          .exchange()
          .expectBody<AccountResponse>()
          .returnResult()
          .responseBody!!
        accounts.add(accountResponseBody)
      }

      for (account in accounts) {
        val subAccountResponseBody = webTestClient.post()
          .uri("/accounts/${account.id}/sub-accounts")
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CreateSubAccountRequest(account.reference))
          .exchange()
          .expectBody<SubAccountResponse>()
          .returnResult()
          .responseBody!!
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
    fun `return a 201 when sent a valid transaction with many to one postings`() {
      // createTransaction(reference: String, createdBy: String, description: String, amount: BigInteger, timestamp: LocalDateTime, postings: List<PostingRequest>): TransactionEntity

      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = 2L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[2].id, type = PostingType.CR, amount = 1L),
      )

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
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
    fun `return a 201 when sent a valid transaction with many to many postings`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[2].id, type = PostingType.DR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[3].id, type = PostingType.CR, amount = 1L),
      )

      val transactionResponseBody = webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
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
      assertThat(transactionResponseBody.postings[2].type).isEqualTo(PostingType.DR)
      assertThat(transactionResponseBody.postings[3].type).isEqualTo(PostingType.CR)
      assertThat(transactionResponseBody.postings[0].amount).isEqualTo(1L)
      assertThat(transactionResponseBody.postings[1].amount).isEqualTo(1L)
      assertThat(transactionResponseBody.postings[2].amount).isEqualTo(1L)
      assertThat(transactionResponseBody.postings[3].amount).isEqualTo(1L)
      assertThat(transactionResponseBody.amount).isEqualTo(2L)
    }

    @Test
    fun `should return 400 when sent a transaction with fewer than two postings`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.DR, amount = 1L),
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
    fun `should return 400 when transaction posting credits do not match transaction amount`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 100L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 100L),
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
    fun `should return 400 when transaction posting credits and posting debits do not balance`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 100L),
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
    fun `should return 400 when transaction amount is negative`() {
      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = -1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = -1L),
      )

      webTestClient.post()
        .uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
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

    var accounts : MutableList<AccountResponse> = mutableListOf()
    var subAccounts : MutableList<SubAccountResponse> = mutableListOf()
    lateinit var transaction : TransactionResponse

    @BeforeEach
    fun setUp() {
      for (i in 2 downTo 0 step 1) {
        val accountResponseBody = webTestClient.post()
          .uri("/accounts")
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CreateAccountRequest("$i"))
          .exchange()
          .expectBody<AccountResponse>()
          .returnResult()
          .responseBody!!
        accounts.add(accountResponseBody)
      }

      for (account in accounts) {
        val subAccountResponseBody = webTestClient.post()
          .uri("/accounts/${account.id}/sub-accounts")
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CreateSubAccountRequest(account.reference))
          .exchange()
          .expectBody<SubAccountResponse>()
          .returnResult()
          .responseBody!!
        subAccounts.add(subAccountResponseBody)
      }

      val createPostingRequests: List<CreatePostingRequest> = listOf(
        CreatePostingRequest(subAccountId = subAccounts[0].id, type = PostingType.CR, amount = 1L),
        CreatePostingRequest(subAccountId = subAccounts[1].id, type = PostingType.DR, amount = 1L),
      )

      transaction = webTestClient.post()
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
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!
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
        .uri("/transactions/${uuid}")
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