package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PrisonerPostingListResponse
import java.util.UUID

class PostingsIntegrationTest : IntegrationTestBase() {

  @Nested
  inner class GetPostings {
    @ParameterizedTest
    @ValueSource(
      strings = [
        ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO,
        ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW,
      ],
    )
    fun `return an empty list of postings when sent a valid account id that has no postings using either role`(role: String) {
      val accountId = UUID.randomUUID()

      val postingsListResponse = webTestClient.get()
        .uri("/accounts/$accountId/postings")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerPostingListResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(postingsListResponse).hasSize(0)
    }

  }
}
