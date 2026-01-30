package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import kotlin.math.min

class TransactionValidator : ConstraintValidator<ValidTransactionRequest, CreateTransactionRequest> {
  override fun isValid(txn: CreateTransactionRequest?, p1: ConstraintValidatorContext?): Boolean {
    if (txn == null) return false

    val creditPostings = txn.postings.filter { it.type == PostingType.CR }
    val debitPostings = txn.postings.filter { it.type == PostingType.DR }

    val fewestPostings = min(creditPostings.size, debitPostings.size)
    if (fewestPostings != 1) return false

    val totalPostingCredits = creditPostings.fold(0L, { acc, it -> acc + it.amount })
    val totalPostingDebits = debitPostings.fold(0L, { acc, it -> acc + it.amount })

    if (totalPostingCredits != txn.amount) return false
    return totalPostingCredits == totalPostingDebits
  }
}
