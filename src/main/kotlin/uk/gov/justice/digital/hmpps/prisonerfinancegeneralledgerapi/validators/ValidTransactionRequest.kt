package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [TransactionValidator::class])
annotation class ValidTransactionRequest (
  val message : String = "invalid transaction",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = []
)