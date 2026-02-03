package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.hello

import org.apache.http.HttpStatus
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.IntegrationTestBase

class HelloIntegrationTest : IntegrationTestBase() {

  @Test
  fun `should return 200 OK and 'Hello World when the correct role is provided `() {
    webTestClient.get()
      .uri("/hello")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .exchange()
      .expectStatus().isOk
      .expectBody(String::class.java).isEqualTo("Hello AUTH_ADM!")
  }

  @Test
  fun `should return 401 Unauthorized when no role or token is provided`() {
    webTestClient.get()
      .uri("/hello")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `should return 403 Forbidden when role is incorrect`() {
    webTestClient.get()
      .uri("/hello")
      .headers(setAuthorisation(roles = listOf("ROLE_DATA_STEALER")))
      .exchange()
      .expectStatus().isForbidden
  }

  @Test
  fun `should return 405 when method not allowed`() {
    webTestClient.post()
      .uri("/hello")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.SC_METHOD_NOT_ALLOWED)
  }
}
