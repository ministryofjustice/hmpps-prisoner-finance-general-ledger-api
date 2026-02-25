package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.textFieldValidator

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class TextFieldValidator : ConstraintValidator<ValidText, String> {

  val textFieldRegex = Regex(
    """^[^\u0000]+$""",
  )

  override fun isValid(textField: String?, p1: ConstraintValidatorContext?): Boolean {
    if (textField.isNullOrEmpty()) return false
    if (!textFieldRegex.matches(textField)) return false
    return true
  }
}
