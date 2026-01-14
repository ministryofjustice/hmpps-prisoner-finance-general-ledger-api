package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccount
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateSubAccountRequest
import java.time.LocalDateTime
import java.util.UUID

class SubAccountIntegrationTest : IntegrationTestBase() {

  // - 201 Creates a subaccount under an account
  // - 201 Creates 2 subaccounts with identical references across 2 accounts
  // - 400 Subaccount Ref already exists within the account
  // - 400 Case insensitive to subaccount uniqueness within 1 account
  // - Standard 401/403

  lateinit var dummyParentAccount: Account

  @Nested
  inner class CreateSubAccount {

    @Test
    fun `should return 201 OK and created sub account if the account provided is valid`() {
      val responseBody = webTestClient.post()
        .uri("/accounts/${dummyParentAccount.id}/sub-accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateSubAccountRequest("TEST_SUB_ACCOUNT_REF"))
        .exchange()
        .expectBody<SubAccount>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_SUB_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(responseBody.id).isInstanceOf(UUID::class.java)
      assertThat(responseBody.parentAccount.id).isEqualTo(dummyParentAccount.id)
    }
  }
}
