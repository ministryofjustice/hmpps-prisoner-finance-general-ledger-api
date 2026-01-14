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

  // - 400 Subaccount Ref already exists within the account
  // - 400 Case insensitive to subaccount uniqueness within 1 account
  // - Standard 401/403

  lateinit var dummyParentAccount: AccountResponse

  @Transactional
  @BeforeEach
  fun resetDB() {
    subAccountRepository.deleteAllInBatch()
    subAccountRepository.flush()
    accountRepository.deleteAllInBatch()
    accountRepository.flush()
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
    fun `should be able to create multiple sub accounts under a single account`() {
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

      val parentAccount = webTestClient.get().uri("/accounts/${dummyParentAccount.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(parentAccount.subAccounts).hasSize(2)
      assertThat(parentAccount.subAccounts.get(0).id).isEqualTo(subAccountOne.id)
      assertThat(parentAccount.subAccounts.get(1).id).isEqualTo(subAccountTwo.id)
    }
  }
}
