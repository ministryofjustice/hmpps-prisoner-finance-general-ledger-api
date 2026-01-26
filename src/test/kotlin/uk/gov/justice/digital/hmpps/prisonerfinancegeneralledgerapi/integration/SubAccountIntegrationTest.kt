package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import java.time.LocalDateTime
import java.util.UUID

class SubAccountIntegrationTest @Autowired constructor(
  var accountDataRepository: AccountDataRepository,
  var subAccountDataRepository: SubAccountDataRepository,
  var transactionDataRepository: TransactionDataRepository,
  var postingsDataRepository: PostingsDataRepository,
) : IntegrationTestBase() {

  lateinit var dummyParentAccountOne: AccountResponse

  @Transactional
  @BeforeEach
  fun resetDB() {
    postingsDataRepository.deleteAllInBatch()
    transactionDataRepository.deleteAllInBatch()
    subAccountDataRepository.deleteAllInBatch()
    accountDataRepository.deleteAllInBatch()
  }

  @Nested
  inner class CreateSubAccount {

    @BeforeEach
    fun seedParentAccount() {
      val responseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      dummyParentAccountOne = responseBody
    }

    @Test
    fun `should return 201 OK and created sub account if the account provided is valid`() {
      val responseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.id).isInstanceOf(UUID::class.java)
      assertThat(responseBody.reference).isEqualTo("TEST_SUB_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(responseBody.parentAccountId).isEqualTo(dummyParentAccountOne.id)
    }

    @Test
    fun `should return 201 and be able to create multiple sub accounts under a single account`() {
      val subAccountOne = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF_1"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(subAccountOne.id).isInstanceOf(UUID::class.java)
      assertThat(subAccountOne.reference).isEqualTo("TEST_SUB_ACCOUNT_REF_1")
      assertThat(subAccountOne.createdBy).isEqualTo("AUTH_ADM")
      assertThat(subAccountOne.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(subAccountOne.parentAccountId).isEqualTo(dummyParentAccountOne.id)

      val subAccountTwo = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF_2"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(subAccountTwo.id).isInstanceOf(UUID::class.java)
      assertThat(subAccountTwo.reference).isEqualTo("TEST_SUB_ACCOUNT_REF_2")
      assertThat(subAccountTwo.createdBy).isEqualTo("AUTH_ADM")
      assertThat(subAccountTwo.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(subAccountTwo.parentAccountId).isEqualTo(dummyParentAccountOne.id)

      webTestClient.get()
        .uri("/accounts/${dummyParentAccountOne.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
    }

    @Test
    fun `should return 201 when creating multiple sub account with same reference in different accounts`() {
      val dummyParentAccountTwo = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF_2"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      val subAccountOne = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      val subAccountTwo = webTestClient.post()
        .uri("/accounts/${dummyParentAccountTwo.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(subAccountTwo.id).isNotEqualTo(subAccountOne.id)
      assertThat(subAccountTwo.reference).isEqualTo(subAccountOne.reference)
      assertThat(subAccountTwo.parentAccountId).isNotEqualTo(subAccountOne.parentAccountId)
    }

    @Test
    fun `should return 400 bad request if sub account reference exists`() {
      val testSubAccountRef = "TEST_SUB_ACCOUNT_REF"

      webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRef))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()

      webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRef))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 bad request if sub account exists with same reference in a different casing within the same account`() {
      val testSubAccountRefUpper = "TEST_SUB_ACCOUNT_REF"
      val testSubAccountRefLower = "test_sub_account_ref"

      webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRefUpper))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()

      webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRefLower))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 401 when requesting account without authorisation headers`() {
      val testSubAccountRef = "TEST_SUB_ACCOUNT_REF"
      webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRef))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting account with incorrect role`() {
      val testSubAccountRef = "TEST_SUB_ACCOUNT_REF"
      webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRef))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 404 when creating a subaccount for an account that does not exist`() {
      webTestClient.post()
        .uri("/accounts/00000000-0000-0000-0000-000000000001/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isNotFound
    }
  }

  @Nested
  inner class FindSubAccounts {

    lateinit var dummySubAccountOne: SubAccountResponse

    lateinit var dummyParentAccountTwo: AccountResponse
    lateinit var dummySubAccountTwo: SubAccountResponse

    @BeforeEach
    fun seedParentAccountAndSubAccounts() {
      val dummyParentAccountOneResponseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      dummyParentAccountOne = dummyParentAccountOneResponseBody

      val subAccountOneResponseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF_1"))
        .exchange()
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      dummySubAccountOne = subAccountOneResponseBody

      val dummyParentAccountTwoResponseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF_2"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      dummyParentAccountTwo = dummyParentAccountTwoResponseBody

      val dummySubAccountTwoResponseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccountTwo.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF_1"))
        .exchange()
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      dummySubAccountTwo = dummySubAccountTwoResponseBody
    }

    @Test
    fun `Should return 200 and a list with only one subAccount matching the references provided`() {
      val responseBody = webTestClient.get()
        .uri("/sub-accounts?reference=${dummySubAccountOne.reference}&accountReference=${dummyParentAccountOne.reference}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<SubAccountResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody).hasSize(1)
      assertThat(responseBody.first().id).isEqualTo(dummySubAccountOne.id)
      assertThat(responseBody.first().reference).isEqualTo(dummySubAccountOne.reference)
      assertThat(responseBody.first().parentAccountId).isEqualTo(dummySubAccountOne.parentAccountId)
    }

    @Test
    fun `Should return 200 and an empty list if no subAccounts match the references provided`() {
      val responseBody = webTestClient.get()
        .uri("/sub-accounts?reference=NOT_A_MATCH&accountReference=${dummyParentAccountOne.reference}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<SubAccountResponse>>()
        .returnResult()
        .responseBody!!

      assert(responseBody.isEmpty())
    }

    @Test
    fun `Should return 200 and an empty list if the parent account reference does not match an existing account`() {
      val responseBody = webTestClient.get()
        .uri("/sub-accounts?reference=${dummySubAccountOne.reference}&accountReference=NOT_A_MATCH")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<SubAccountResponse>>()
        .returnResult()
        .responseBody!!

      assert(responseBody.isEmpty())
    }

    @Test
    fun `Should return 200 and the correct account even if the account and sub account references are in different cases`() {
      val responseBody = webTestClient.get()
        .uri("/sub-accounts?reference=${dummySubAccountOne.reference.lowercase()}&accountReference=${dummyParentAccountOne.reference.lowercase()}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<SubAccountResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody).hasSize(1)
      assertThat(responseBody.first().id).isEqualTo(dummySubAccountOne.id)
      assertThat(responseBody.first().reference).isEqualTo(dummySubAccountOne.reference)
      assertThat(responseBody.first().parentAccountId).isEqualTo(dummySubAccountOne.parentAccountId)
    }

    @Test
    fun `Should return 400 Bad Request if no query parameters are provided`() {
      webTestClient.get()
        .uri("/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `Should return 400 if only the subAccount reference is provided`() {
      webTestClient.get()
        .uri("/sub-accounts?reference=${dummySubAccountOne.reference}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `Should return 400 if only the account reference is provided`() {
      webTestClient.get()
        .uri("/sub-accounts?accountReference=${dummySubAccountOne.reference}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `Should return 401 if no authorisation headers are provided`() {
      webTestClient.get()
        .uri("/sub-accounts?reference=${dummySubAccountOne.reference}&accountReference=${dummyParentAccountOne.reference}")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `Should return 403 if the user does not have the correct role`() {
      webTestClient.get()
        .uri("/sub-accounts?reference=${dummySubAccountOne.reference}&accountReference=${dummyParentAccountOne.reference}")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class GetSubAccountById {
    lateinit var dummySubAccountOne: SubAccountResponse

    @BeforeEach
    fun seedAccountAndSubAccount() {
      val dummyParentAccountOneResponseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      dummyParentAccountOne = dummyParentAccountOneResponseBody

      val subAccountOneResponseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF_1"))
        .exchange()
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      dummySubAccountOne = subAccountOneResponseBody
    }

    @Test
    fun `Should return 200 and the correct subAccount`() {
      val responseBody = webTestClient.get()
        .uri("/sub-accounts/${dummySubAccountOne.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.id).isEqualTo(dummySubAccountOne.id)
      assertThat(responseBody.reference).isEqualTo(dummySubAccountOne.reference)
      assertThat(responseBody.parentAccountId).isEqualTo(dummySubAccountOne.parentAccountId)
    }

    @Test
    fun `Should return 404 if the subAccount does not exist`() {
      webTestClient.get()
        .uri("/sub-accounts/00000000-0000-0000-0000-000000000000")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `Should return 401 if no authorisation headers are provided`() {
      webTestClient.get()
        .uri("/sub-accounts/${dummySubAccountOne.id}")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `Should return 403 if the user does not have the correct role`() {
      webTestClient.get()
        .uri("/sub-accounts/${dummySubAccountOne.id}")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class GetSubAccountBalance {

    lateinit var dummyParentAccountOne: AccountResponse
    lateinit var dummySubAccountOne: SubAccountResponse

    lateinit var dummyParentAccountTwo: AccountResponse
    lateinit var dummySubAccountTwo: SubAccountResponse

    lateinit var dummyTransactionOne: TransactionResponse
    lateinit var dummyTransactionTwo: TransactionResponse

    @BeforeEach
    fun seedAccountAndSubAccount() {
//      The result of this set up should leave sub account one with a balance of 5
//      and sub account two with a balance of -5
      val dummyParentAccountOneResponseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF_1"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      dummyParentAccountOne = dummyParentAccountOneResponseBody

      val subAccountOneResponseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF_1"))
        .exchange()
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      dummySubAccountOne = subAccountOneResponseBody

      val dummyParentAccountTwoResponseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF_2"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      dummyParentAccountTwo = dummyParentAccountTwoResponseBody

      val subAccountTwoResponseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccountOne.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF_2"))
        .exchange()
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      dummySubAccountTwo = subAccountTwoResponseBody

//      Send 10 from subAccountTwo to subAccountOne
      val testPostingsOne = listOf(
        CreatePostingRequest(
          subAccountId = dummySubAccountOne.id,
          amount = 10,
          type = PostingType
            .CR,
        ),
        CreatePostingRequest(
          subAccountId = dummySubAccountTwo.id,
          amount = 10,
          type = PostingType
            .DR,
        ),
      )

//      Send 5 back from subAccountOne to subAccountTwo to create varied posting types one each
      val testPostingsTwo = listOf(
        CreatePostingRequest(
          subAccountId = dummySubAccountOne.id,
          amount = 5,
          type = PostingType
            .DR,
        ),
        CreatePostingRequest(
          subAccountId = dummySubAccountTwo.id,
          amount = 5,
          type = PostingType
            .CR,
        ),
      )

      val transactionPayloadOne = CreateTransactionRequest(
        reference = "TEST_TX_REF_1",
        description = "TESTING",
        timestamp = LocalDateTime.now(),
        amount = 10,
        postings = testPostingsOne,
      )

      val transactionOneResponseBody = webTestClient.post().uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .bodyValue(transactionPayloadOne)
        .exchange()
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      dummyTransactionOne = transactionOneResponseBody

      val transactionPayloadTwo = CreateTransactionRequest(
        reference = "TEST_TX_REF_2",
        description = "TESTING",
        timestamp = LocalDateTime.now(),
        amount = 5,
        postings = testPostingsTwo,
      )

      val transactionTwoResponseBody = webTestClient.post().uri("/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(transactionPayloadTwo)
        .exchange()
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult()
        .responseBody!!

      dummyTransactionTwo = transactionTwoResponseBody
    }

    @Test
    fun `Should return 200 ok and a balance object for the requested subaccount`() {
      val subAccountOneBalance = webTestClient.get()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SubAccountBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(subAccountOneBalance.amount).isEqualTo(5)
      assertThat(subAccountOneBalance.subAccountId).isEqualTo(dummySubAccountOne.id)
      assertThat(subAccountOneBalance.balanceDateTime).isInThePast
    }

    @Test
    fun `Should return 400 Bad Request if the subAccountId is not a valid UUID`() {
      webTestClient.get()
        .uri("/sub-accounts/NOT_A_UUID/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `Should return 403 Forbidden if the user does not have the correct role`() {
      webTestClient.get()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `Should return 404 Not Found if the subAccount id does not exist`() {
      val responseBody = webTestClient.get()
        .uri("/sub-accounts/${UUID.randomUUID()}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isNotFound
        .expectBody<ProblemDetail>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.properties?.get("userMessage")).isEqualTo("Sub Account not found")
    }
  }
}
