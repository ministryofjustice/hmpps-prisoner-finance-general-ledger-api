package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.textFieldValidator

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class TextFieldValidator : ConstraintValidator<ValidTextField, String> {

  val textFieldRegex = Regex(
    """^[^\x00-\x1F\x7F]+$""",
  )

  override fun isValid(textField: String?, p1: ConstraintValidatorContext?): Boolean {
    if (textField.isNullOrEmpty()) return false
    if (!textFieldRegex.matches(textField)) return false
    return true
  }
}
