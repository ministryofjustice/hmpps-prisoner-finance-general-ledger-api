package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.referenceStringValidator

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [ReferenceStringValidator::class])
annotation class ValidReferenceString(
  val message: String = "Invalid Reference String",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)
