package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.textFieldValidator

import org.junit.jupiter.api.Test

class TextFieldValidatorTest {

  private val validator = TextFieldValidator()

  @Test
  fun `Should pass when a string contains alpha numeric characters and supported punctuation`() {
    val text = "A B C 123!@#$%^&*()-_=+[]{};':\",./<>?"
    val ok = validator.isValid(text, null)
    assert(ok)
  }

  @Test
  fun `Should fail when a string is null`() {
    val text = null
    val ok = validator.isValid(text, null)
    assert(!ok)
  }

  @Test
  fun `Should fail when a string is empty`() {
    val text = ""
    val ok = validator.isValid(text, null)
    assert(!ok)
  }

  @Test
  fun `Should fail when a string contains a null byte`() {
    val text = "hello\u0000"
    val ok = validator.isValid(text, null)
    assert(!ok)
  }
}
