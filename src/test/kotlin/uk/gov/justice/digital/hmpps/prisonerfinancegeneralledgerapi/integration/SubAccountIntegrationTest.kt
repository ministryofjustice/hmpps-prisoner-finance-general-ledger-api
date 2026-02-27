package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import tools.jackson.module.kotlin.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class SubAccountIntegrationTest : IntegrationTestBase() {

  lateinit var dummyParentAccountOne: AccountResponse

  @Transactional
  @BeforeEach
  fun resetDB() {
    integrationTestHelpers.clearDB()
  }

  @Nested
  inner class CreateSubAccount {

    @BeforeEach
    fun seedParentAccount() {
      dummyParentAccountOne = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF", AccountType.PRISONER)
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
      assertThat(responseBody.createdAt).isInstanceOf(Instant::class.java)
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
      assertThat(subAccountOne.createdAt).isInstanceOf(Instant::class.java)
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
      assertThat(subAccountTwo.createdAt).isInstanceOf(Instant::class.java)
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
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF_2", AccountType.PRISONER))
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
    fun `should return 409 conflict if sub account reference exists`() {
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
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `should return 409 conflict if sub account exists with same reference in a different casing within the same account`() {
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
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
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
    fun seedSubAccountsToFind() {
      dummyParentAccountOne = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_1", AccountType.PRISONER)
      dummySubAccountOne = integrationTestHelpers.createSubAccount(dummyParentAccountOne.id, "TEST_SUB_ACCOUNT_REF_1")

      dummyParentAccountTwo = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_2", AccountType.PRISONER)
      dummySubAccountTwo = integrationTestHelpers.createSubAccount(dummyParentAccountTwo.id, "TEST_SUB_ACCOUNT_REF_1")
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
      dummyParentAccountOne = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF", AccountType.PRISONER)
      dummySubAccountOne = integrationTestHelpers.createSubAccount(dummyParentAccountOne.id, "TEST_SUB_ACCOUNT_REF_1")
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

      dummyParentAccountOne = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_1", AccountType.PRISONER)
      dummySubAccountOne = integrationTestHelpers.createSubAccount(dummyParentAccountOne.id, "TEST_SUB_ACCOUNT_REF_1")

      dummyParentAccountTwo = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_2", AccountType.PRISONER)
      dummySubAccountTwo = integrationTestHelpers.createSubAccount(dummyParentAccountTwo.id, "TEST_SUB_ACCOUNT_REF_2")

//      Send 10 from subAccountTwo to subAccountOne
      dummyTransactionOne = integrationTestHelpers.createOneToOneTransaction(
        amount = 10,
        debitSubAccountId = dummySubAccountTwo.id,
        creditSubAccountId = dummySubAccountOne.id,
        transactionReference = "TEST_TX_REF_1",
        description = "TESTING",
      )

//      Send 5 back from subAccountOne to subAccountTwo to create varied posting types one each
      dummyTransactionTwo = integrationTestHelpers.createOneToOneTransaction(
        amount = 5,
        debitSubAccountId = dummySubAccountOne.id,
        creditSubAccountId = dummySubAccountTwo.id,
        transactionReference = "TEST_TX_REF_2",
        description = "TESTING",
      )
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
    fun `Should have balance overwritten when a new balance is posted`() {
      val subAccountOneOriginalBalance = webTestClient.get()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SubAccountBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(subAccountOneOriginalBalance.amount).isEqualTo(5)
      assertThat(subAccountOneOriginalBalance.subAccountId).isEqualTo(dummySubAccountOne.id)
      assertThat(subAccountOneOriginalBalance.balanceDateTime).isInThePast

      val balanceDateTimeTomorrow = LocalDateTime.now().plusDays(1).toInstant(java.time.ZoneOffset.UTC)
      val statementBalanceResponse = webTestClient.post()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = 10, balanceDateTime = balanceDateTimeTomorrow))
        .exchange()
        .expectStatus().isCreated
        .expectBody<StatementBalanceResponse>()
        .returnResult().responseBody!!

      assertThat(statementBalanceResponse.amount).isEqualTo(10)
      assertThat(statementBalanceResponse.subAccountId).isEqualTo(dummySubAccountOne.id)
      assertThat(statementBalanceResponse.balanceDateTime).isEqualTo(balanceDateTimeTomorrow)

      val subAccountOneCurrentBalance = webTestClient.get()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SubAccountBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(subAccountOneCurrentBalance.amount).isEqualTo(10)
      assertThat(subAccountOneCurrentBalance.subAccountId).isEqualTo(dummySubAccountOne.id)
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
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Sub Account not found")
    }
  }

  @Nested
  inner class PostStatementBalance {
    lateinit var dummySubAccountOne: SubAccountResponse

    @BeforeEach
    fun seedAccountAndSubAccount() {
      dummyParentAccountOne = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF", AccountType.PRISONER)
      dummySubAccountOne = integrationTestHelpers.createSubAccount(dummyParentAccountOne.id, "TEST_SUB_ACCOUNT_REF_1")
    }

    @Test
    fun `should return 201 and the created statement balance`() {
      val balanceDateTime = Instant.now()
      val statementBalanceResponse = webTestClient.post()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = 10, balanceDateTime = balanceDateTime))
        .exchange()
        .expectStatus().isCreated
        .expectBody<StatementBalanceResponse>()
        .returnResult().responseBody!!

      assertThat(statementBalanceResponse.amount).isEqualTo(10)
      assertThat(statementBalanceResponse.subAccountId).isEqualTo(dummySubAccountOne.id)
      assertThat(statementBalanceResponse.balanceDateTime).isEqualTo(balanceDateTime)
    }

    @Test
    fun `should return 400 when given a malformed body`() {
      webTestClient.post()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when post body are incorrect types`() {
      webTestClient.post()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
          jsonMapper {
            "amount" to 2.2
            "balanceDateTime" to "Tuesday"
          },
        )
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 403 when called with incorrect role`() {
      webTestClient.post()
        .uri("/sub-accounts/${dummySubAccountOne.id}/balance")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = 10, balanceDateTime = Instant.now()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 404 when sub-account does not exist`() {
      webTestClient.post()
        .uri("/sub-accounts/${UUID.randomUUID()}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateStatementBalanceRequest(amount = 10, balanceDateTime = Instant.now()))
        .exchange()
        .expectStatus().isNotFound
    }
  }
}
