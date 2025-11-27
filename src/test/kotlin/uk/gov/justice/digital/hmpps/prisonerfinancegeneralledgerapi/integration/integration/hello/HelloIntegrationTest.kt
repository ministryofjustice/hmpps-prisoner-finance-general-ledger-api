package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.integration.hello

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_GENERAL_LEDGER__RO
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.integration.IntegrationTestBase

class HelloIntegrationTest : IntegrationTestBase() {

  @Test
  fun `should return 200 OK and 'Hello World when the correct role is provided `() {
    webTestClient.get()
      .uri("/hello")
      .headers(setAuthorisation(roles = listOf(ROLE_GENERAL_LEDGER__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody(String::class.java).isEqualTo("Hello World!")
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
}
