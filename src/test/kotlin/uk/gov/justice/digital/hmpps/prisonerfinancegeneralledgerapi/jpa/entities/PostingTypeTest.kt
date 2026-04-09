package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.oppositePostingType

class PostingTypeTest {
  @ParameterizedTest
  @CsvSource("CR,DR", "DR,CR")
  fun `should get opposite posting type from posting`(postingType: PostingType, oppositePostingType: PostingType) {
    assertThat(postingType.oppositePostingType()).isEqualTo(oppositePostingType)
  }
}
