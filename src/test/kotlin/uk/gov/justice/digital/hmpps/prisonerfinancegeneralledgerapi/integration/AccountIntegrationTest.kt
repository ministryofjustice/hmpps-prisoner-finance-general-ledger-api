package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW

class AccountIntegrationTest : IntegrationTestBase() {

  @Test
  fun `should return 200 OK and the created account when the correct role is provided`() {
    webTestClient.post()
      .uri("/account")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(mapOf("accountReference" to "TEST_ACCOUNT_REF"))
      .exchange()
      .expectStatus().isOk
  }
}
