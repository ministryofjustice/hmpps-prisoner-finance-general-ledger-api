package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import tools.jackson.module.kotlin.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Instant
import java.util.*

class AccountIntegrationTest : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun resetDB() {
    integrationTestHelpers.clearDB()
  }

  @Nested
  inner class CreateAccount {

    @Test
    fun `should return 201 OK and the created account when the correct role is provided`() {
      val responseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(Instant::class.java)
      assertThat(responseBody.id).isInstanceOf(UUID::class.java)
      assertThat(responseBody.subAccounts).isInstanceOf(List::class.java)
      assertThat(responseBody.subAccounts).isEmpty()
    }

    @Test
    fun `should return 400 Bad Request if the reference submitted already has an associated account`() {
      val responseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(Instant::class.java)
      assertThat(responseBody.id).isInstanceOf(UUID::class.java)

      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 Bad Request if the reference submitted already has an associated account in a different casing`() {
      val responseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(Instant::class.java)
      assertThat(responseBody.id).isInstanceOf(UUID::class.java)

      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("test_account_ref"))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `createAccount should return 401 without authorisation headers`() {
      webTestClient.post()
        .uri("/accounts")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `createAccount should return 403 Forbidden when role is incorrect`() {
      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `createAccount should return 400 when sent a malformed body`() {
      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(jsonMapper { "wrong_key" to "wrong_value" })
        .exchange()
        .expectStatus().isBadRequest
    }
  }

  @Nested
  inner class GetAccount {

    @Test
    fun `should return 200 OK and the correct account`() {
      val dummyAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      val responseBody = webTestClient.get()
        .uri("/accounts/${dummyAccount.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(Instant::class.java)
      assertThat(responseBody.id).isEqualTo(dummyAccount.id)
    }

    @Test
    fun `should return 200 OK and any associated subaccounts`() {
      val dummyAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      val subAccount = webTestClient.post()
        .uri("/accounts/${dummyAccount.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      val responseBody = webTestClient.get()
        .uri("/accounts/${dummyAccount.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.subAccounts).hasSize(1)
      assertThat(responseBody.subAccounts.first().id).isEqualTo(subAccount.id)
      assertThat(responseBody.subAccounts.first().parentAccountId).isEqualTo(dummyAccount.id)
    }

    @Test
    fun `should return 404 Not Found when the account does not exist`() {
      val incorrectUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
      webTestClient.get()
        .uri("/accounts/$incorrectUUID")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return 400 Bad Request when passed an invalid UUID`() {
      webTestClient.get()
        .uri("/accounts/not-a-uuid")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 401 when requesting account without authorisation headers`() {
      val dummyAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      webTestClient.get()
        .uri("/accounts/${dummyAccount.id}")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting account with incorrect role`() {
      val dummyAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      webTestClient.get()
        .uri("/accounts/${dummyAccount.id}")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class FindAccounts {

    @Test
    fun `should return 200 OK if reference query matches an account`() {
      val dummyAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      val responseBody = webTestClient.get()
        .uri("/accounts?reference=TEST_ACCOUNT_REF")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<AccountResponse>>()
        .returnResult()
        .responseBody!!
      assertThat(responseBody).hasSize(1)
      assertThat(responseBody[0].reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody[0].createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody[0].createdAt).isInstanceOf(Instant::class.java)
      assertThat(responseBody[0].id).isEqualTo(dummyAccount.id)
    }

    @Test
    fun `should return 200 OK and empty list if reference does not match any accounts`() {
      integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      val responseBody = webTestClient.get()
        .uri("/accounts?reference=NOT_A_MATCH")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<AccountResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody).hasSize(0)
      assertThat(responseBody).isInstanceOf(List::class.java)
    }

    @Test
    fun `should return 200 OK and any associated subaccounts`() {
      val dummyAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      val subAccount = webTestClient.post()
        .uri("/accounts/${dummyAccount.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

      val responseBody = webTestClient.get()
        .uri("/accounts?reference=${dummyAccount.reference}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<AccountResponse>>()
        .returnResult()
        .responseBody!!.first()

      assertThat(responseBody.subAccounts).hasSize(1)
      assertThat(responseBody.subAccounts.first().id).isEqualTo(subAccount.id)
      assertThat(responseBody.subAccounts.first().parentAccountId).isEqualTo(dummyAccount.id)
    }

    @Test
    fun `should return 200 OK if the reference submitted has an associated account in a different casing`() {
      val dummyAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      val responseBody = webTestClient.get()
        .uri("/accounts?reference=${dummyAccount.reference.lowercase()}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<AccountResponse>>()
        .returnResult()
        .responseBody!!
      assertThat(responseBody).hasSize(1)
      assertThat(responseBody[0].reference).isEqualTo(dummyAccount.reference)
      assertThat(responseBody[0].id).isEqualTo(dummyAccount.id)
    }

    @Test
    fun `should return 400 Bad request if no query parameters are provided`() {
      val responseBody = webTestClient.get()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Query parameters must be provided")
    }

    @Test
    fun `should return 400 Bad request if query parameters is an empty string`() {
      val responseBody = webTestClient.get()
        .uri("/accounts?reference=")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody<ErrorResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.userMessage).isEqualTo("Query parameters must be provided")
    }

    @Test
    fun `should return 401 when requesting accounts without authorisation headers`() {
      integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      webTestClient.get()
        .uri("/accounts?reference=TEST_ACCOUNT_REF")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting account with incorrect role`() {
      integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      webTestClient.get()
        .uri("/accounts?reference=TEST_ACCOUNT_REF")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }
  }

  @Nested
  inner class GetBalanceForAccount {
    @Test
    fun `Should return 200 and a balance of 0 for an account with no postings`() {
      val seededAccount = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")

      val responseBody = webTestClient.get()
        .uri("/accounts/${seededAccount.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.accountId).isEqualTo(seededAccount.id)
      assertThat(responseBody.amount).isEqualTo(0)
      assertThat(responseBody.balanceDateTime).isInThePast
    }

    @Test
    fun `Should return 200 and a balance where an account has postings`() {
      val accountOne = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_ONE")
      val accountOneSubAccount = integrationTestHelpers.createSubAccount(accountOne.id, "TEST_SUB_ACCOUNT_REF_ONE")

      val accountTwo = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF_TWO")
      val accountTwoSubAccount = integrationTestHelpers.createSubAccount(accountTwo.id, "TEST_SUB_ACCOUNT_REF_TWO")

      val transactionAmount = 1L

      integrationTestHelpers.createOneToOneTransaction(
        debitSubAccountId = accountOneSubAccount.id,
        creditSubAccountId = accountTwoSubAccount.id,
        amount = transactionAmount,
        transactionReference = "VAPES",
      )

      val responseBody = webTestClient.get()
        .uri("/accounts/${accountTwo.id}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.accountId).isEqualTo(accountTwo.id)
      assertThat(responseBody.amount).isEqualTo(transactionAmount)
    }

    @Test
    fun `Should return a 200 and a zero balance for a prisoner when no transactions exist between the queried prison and the prisoner`() {
      val prisonA = integrationTestHelpers.createAccount("AAA")
      val prisonASubAccount = integrationTestHelpers.createSubAccount(prisonA.id, "AAA:CANTEEN")

      val prisonB = integrationTestHelpers.createAccount("BBB")
      val prisonBSubAccount = integrationTestHelpers.createSubAccount(prisonB.id, "BBB:CATALOGUE")

      val prisoner = integrationTestHelpers.createAccount("123456")
      val prisonerSubAccount = integrationTestHelpers.createSubAccount(prisoner.id, "SPENDS")

      val transactionAmount = 1L

      integrationTestHelpers.createOneToOneTransaction(
        debitSubAccountId = prisonerSubAccount.id,
        creditSubAccountId = prisonBSubAccount.id,
        amount = transactionAmount,
        transactionReference = "VAPES",
      )

      val responseBody = webTestClient.get()
        .uri("/accounts/${prisoner.id}/balance?prisonRef=${prisonA.reference}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.accountId).isEqualTo(prisoner.id)
      assertThat(responseBody.amount).isEqualTo(0)
    }

    @Test
    fun `Should return a 200 and a balance for a prisoner at a single prison when transactions exist between the prisoner and multiple prisons`() {
      val prisonA = integrationTestHelpers.createAccount("AAA")
      val prisonASubAccount = integrationTestHelpers.createSubAccount(prisonA.id, "AAA:CANTEEN")

      val prisonB = integrationTestHelpers.createAccount("BBB")
      val prisonBSubAccount = integrationTestHelpers.createSubAccount(prisonB.id, "BBB:CATALOGUE")

      val prisoner = integrationTestHelpers.createAccount("123456")
      val prisonerSubAccount = integrationTestHelpers.createSubAccount(prisoner.id, "SPENDS")

      integrationTestHelpers.createOneToOneTransaction(
        debitSubAccountId = prisonerSubAccount.id,
        creditSubAccountId = prisonASubAccount.id,
        amount = 1L,
        transactionReference = "VAPES",
      )

      integrationTestHelpers.createOneToOneTransaction(
        debitSubAccountId = prisonerSubAccount.id,
        creditSubAccountId = prisonBSubAccount.id,
        amount = 2L,
        transactionReference = "VAPES",
      )

      val responseBody = webTestClient.get()
        .uri("/accounts/${prisoner.id}/balance?prisonRef=${prisonA.reference}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountBalanceResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.accountId).isEqualTo(prisoner.id)
      assertThat(responseBody.amount).isEqualTo(-1L)
    }

    @Test
    fun `Should return 400 Bad Request if the id is not a valid UUID`() {
      webTestClient.get()
        .uri("/accounts/not-a-uuid/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `Should return 403 Forbidden if wrong role provided for authorisation`() {
      val account = integrationTestHelpers.createAccount("TEST_ACCOUNT_REF")
      webTestClient.get().uri("/accounts/${account.id}/balance")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `Should return 404 for a valid UUID that does not have an associated account`() {
      webTestClient.get()
        .uri("/accounts/${UUID.randomUUID()}/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isNotFound
    }
  }
}
