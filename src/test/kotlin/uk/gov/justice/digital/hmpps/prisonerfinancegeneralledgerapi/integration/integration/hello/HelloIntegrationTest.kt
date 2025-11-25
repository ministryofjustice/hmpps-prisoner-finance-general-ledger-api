package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.integration.hello

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.NON_SENSITIVE_READ
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.integration.IntegrationTestBase

class HelloIntegrationTest : IntegrationTestBase() {

  @Test
  fun helloEndpointTest() {
    webTestClient.get()
      .uri("/hello")
      .headers(setAuthorisation(roles = listOf(NON_SENSITIVE_READ)))
      .exchange()
      .expectStatus().isOk
      .expectBody(String::class.java).isEqualTo("Hello World!")
  }

  @Test
  fun unauthorisedHelloEndpointTest() {
    webTestClient.get()
      .uri("/hello")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun incorrectScopeTest() {
    webTestClient.get()
      .uri("/hello")
      .headers(setAuthorisation(roles = listOf("ROLE_DATA_STEALER")))
      .exchange()
      .expectStatus().isForbidden
  }
}
