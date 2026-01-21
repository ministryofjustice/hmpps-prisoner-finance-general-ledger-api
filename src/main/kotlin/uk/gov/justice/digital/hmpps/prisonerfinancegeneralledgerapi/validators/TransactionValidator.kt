package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest

class TransactionValidator : ConstraintValidator<ValidTransactionRequest, CreateTransactionRequest> {
  override fun isValid(txn: CreateTransactionRequest?, p1: ConstraintValidatorContext?): Boolean {
    if (txn == null) return false
    if (txn.postings.size < 2) return false

    val totalPostingCredits = txn.postings.filter { it.type == PostingType.CR }.fold(0L, { acc, it -> acc + it.amount })
    val totalPostingDebits = txn.postings.filter { it.type == PostingType.DR }.fold(0L, { acc, it -> acc + it.amount })

    if (totalPostingCredits != txn.amount) return false
    return totalPostingCredits == totalPostingDebits
  }
}
