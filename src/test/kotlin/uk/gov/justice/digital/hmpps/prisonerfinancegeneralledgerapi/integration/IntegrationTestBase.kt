package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.ContainersConfig
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.helpers.IntegrationTestHelpers
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Import(IntegrationTestHelpers::class, ContainersConfig::class)
abstract class IntegrationTestBase {

  @LocalServerPort
  private var port: Int = 0

  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @Autowired
  protected lateinit var integrationTestHelpers: IntegrationTestHelpers

  @BeforeEach
  fun initClients() {
    webTestClient = WebTestClient.bindToServer()
      .baseUrl("http://localhost:$port")
      .build()
    integrationTestHelpers.setWebClient(webTestClient)
  }

  fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
  }

  internal fun setIdempotencyKey(
    key: UUID,
  ): (HttpHeaders) -> Unit = { it.set("Idempotency-Key", key.toString()) }
}
