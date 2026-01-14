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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.SubAccountResponse
import java.time.LocalDateTime
import java.util.UUID

class SubAccountIntegrationTest @Autowired constructor(
  var accountRepository: AccountRepository,
) : IntegrationTestBase() {

  // - 201 Creates a subaccount under an account
  // - 201 Creates 2 subaccounts with identical references across 2 accounts
  // - 400 Subaccount Ref already exists within the account
  // - 400 Case insensitive to subaccount uniqueness within 1 account
  // - Standard 401/403

  lateinit var dummyParentAccount: AccountResponse

  @Transactional
  @BeforeEach
  fun resetDB() {
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
  }
}
