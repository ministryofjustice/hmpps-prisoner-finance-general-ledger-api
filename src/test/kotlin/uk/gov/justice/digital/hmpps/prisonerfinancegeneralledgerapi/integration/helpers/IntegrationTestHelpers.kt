package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.helpers

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.util.UUID

class IntegrationTestHelpers(
  private val webTestClient: WebTestClient,
  private val jwtAuthHelper: JwtAuthorisationHelper,
) {

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  fun createAccount(reference: String): AccountResponse {
    val account = webTestClient.post()
      .uri("/accounts")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CreateAccountRequest(reference))
      .exchange()
      .expectBody<AccountResponse>()
      .returnResult()
      .responseBody!!

    return account
  }

  fun createSubAccount(accountId: UUID, subAccountReference: String): SubAccountResponse {
    val subAccount = webTestClient.post()
      .uri("/accounts/$accountId/sub-accounts")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CreateSubAccountRequest(subAccountReference = subAccountReference))
      .exchange()
      .expectBody<SubAccountResponse>()
      .returnResult()
      .responseBody!!
    return subAccount
  }
}

@TestConfiguration
class IntegrationTestHelpersTestConfig {
  @Bean
  fun integrationTestHelpers(
    webTestClient: WebTestClient,
    jwtAuthHelper: JwtAuthorisationHelper,
  ): IntegrationTestHelpers = IntegrationTestHelpers(webTestClient, jwtAuthHelper)
}
