package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.referenceStringValidator

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class ReferenceStringValidatorTest {

  private val validator = ReferenceStringValidator()

  @Test
  fun `Should pass when the string contains only valid characters`() {
    val refString = "ABC:123_-"
    val ok = validator.isValid(refString, null)
    Assertions.assertThat(ok).isTrue()
  }

  @Test
  fun `Should pass when the string is null`() {
    val refString = null
    val ok = validator.isValid(refString, null)
    Assertions.assertThat(ok).isTrue()
  }

  @Test
  fun `Should fail when the string is empty`() {
    val refString = ""
    val ok = validator.isValid(refString, null)
    Assertions.assertThat(ok).isFalse()
  }

  @Test
  fun `Should fail when there are null bytes in the string`() {
    val refString = "ABC:123\u0000"
    val ok = validator.isValid(refString, null)
    Assertions.assertThat(ok).isFalse()
  }

  @Test
  fun `Should fail when the string contains non-alphanumeric characters`() {
    val refString = "ABC:123!"
    val ok = validator.isValid(refString, null)
    Assertions.assertThat(ok).isFalse()
  }
}
