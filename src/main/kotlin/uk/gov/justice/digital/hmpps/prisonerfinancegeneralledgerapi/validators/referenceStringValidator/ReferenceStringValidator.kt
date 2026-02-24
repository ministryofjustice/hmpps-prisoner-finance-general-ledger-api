package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.referenceStringValidator

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class ReferenceStringValidator : ConstraintValidator<ValidReferenceString, String> {

  val refStringRegex = Regex("^[A-Za-z0-9:_]+$")
  override fun isValid(refString: String?, p1: ConstraintValidatorContext?): Boolean {
    if (refString == null) return true
    if (!refStringRegex.matches(refString)) return false
    return true
  }
}
