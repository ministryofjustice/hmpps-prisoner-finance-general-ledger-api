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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.SubAccountResponse
import java.time.LocalDateTime
import java.util.UUID

class SubAccountIntegrationTest @Autowired constructor(
  var accountRepository: AccountRepository,
  var subAccountRepository: SubAccountRepository,
) : IntegrationTestBase() {

  lateinit var dummyParentAccount: AccountResponse

  @Transactional
  @BeforeEach
  fun resetDB() {
    subAccountRepository.deleteAllInBatch()
    accountRepository.deleteAllInBatch()
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

      dummyParentAccount = responseBody
    }

    @Test
    fun `should return 201 OK and created sub account if the account provided is valid`() {
      val responseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
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
      assertThat(responseBody.parentAccountId).isEqualTo(dummyParentAccount.id)
    }

    @Test
    fun `should return 201 and be able to create multiple sub accounts under a single account`() {
      val subAccountOne = webTestClient.post()
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
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
      assertThat(subAccountOne.parentAccountId).isEqualTo(dummyParentAccount.id)

      val subAccountTwo = webTestClient.post()
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
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
      assertThat(subAccountTwo.parentAccountId).isEqualTo(dummyParentAccount.id)

//      val parentAccount = webTestClient.get().uri("/accounts/${dummyParentAccount.id}")
//        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
//        .exchange()
//        .expectBody<AccountResponse>()
//        .returnResult()
//        .responseBody!!
//
//      assertThat(parentAccount.subAccounts).hasSize(2)
//      assertThat(parentAccount.subAccounts.get(0).id).isEqualTo(subAccountOne.id)
//      assertThat(parentAccount.subAccounts.get(1).id).isEqualTo(subAccountTwo.id)
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
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
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
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRef))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()

      webTestClient.post()
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
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
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRefUpper))
        .exchange()
        .expectStatus().isCreated
        .expectBody<SubAccountResponse>()

      webTestClient.post()
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
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
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRef))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting account with incorrect role`() {
      val testSubAccountRef = "TEST_SUB_ACCOUNT_REF"
      webTestClient.post()
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest(testSubAccountRef))
        .exchange()
        .expectStatus().isForbidden
    }
  }
}
