package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.specifications

import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

object PostingsSpecification {

  fun byParentAccountId(accountId: UUID): Specification<PostingEntity> = Specification { root, _, cb ->

    val subAccount = root.join<PostingEntity, SubAccountEntity>("subAccountEntity")
    val parentAccount = subAccount.join<SubAccountEntity, AccountEntity>("parentAccountEntity")

    cb.equal(parentAccount.get<UUID>("id"), accountId)
  }

  fun bySubAccountId(subAccountId: UUID?): Specification<PostingEntity> = Specification { root, _, cb ->
    if (subAccountId == null) return@Specification null

    val subAccount = root.join<PostingEntity, SubAccountEntity>("subAccountEntity")

    return@Specification cb.equal(subAccount.get<UUID>("id"), subAccountId)
  }

  fun createdBetween(startDate: Instant?, endDate: Instant?): Specification<PostingEntity> = Specification { root, _, cb ->

    if (startDate == null && endDate == null) return@Specification null

    val parentTransaction = root.join<PostingEntity, TransactionEntity>("transactionEntity")

    when {
      startDate != null && endDate != null ->
        cb.between(parentTransaction.get("timestamp"), startDate, endDate)

      startDate != null ->
        cb.greaterThanOrEqualTo(parentTransaction.get("timestamp"), startDate)

      else ->
        cb.lessThanOrEqualTo(parentTransaction.get("timestamp"), endDate)
    }
  }

  fun byPostingType(credit: Boolean, debit: Boolean): Specification<PostingEntity> = Specification { root, _, cb ->
    return@Specification when {
      credit && !debit -> cb.equal(root.get<PostingEntity>("type"), PostingType.CR)
      !credit && debit -> cb.equal(root.get<PostingEntity>("type"), PostingType.DR)
      else -> null
    }
  }
}
