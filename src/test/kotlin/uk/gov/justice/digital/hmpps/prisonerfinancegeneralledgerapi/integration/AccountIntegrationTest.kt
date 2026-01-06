package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import com.fasterxml.jackson.module.kotlin.jsonMapper
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest

class AccountIntegrationTest : IntegrationTestBase() {

  @Test
  fun `should return 201 OK and the created account when the correct role is provided`() {
    webTestClient.post()
      .uri("/account")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
      .exchange()
      .expectStatus().isCreated
  }

  @Test
  fun `createAccount should return 401 without authorisation headers`() {
    webTestClient.post()
      .uri("/account")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `createAccount should return 403 Forbidden when role is incorrect`() {
    webTestClient.post()
      .uri("/account")
      .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `createAccount should return 400 when sent a malformed body`() {
    webTestClient.post()
      .uri("/account")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(jsonMapper { "wrong_key" to "wrong_value" })
      .exchange()
      .expectStatus().isBadRequest
  }
}
