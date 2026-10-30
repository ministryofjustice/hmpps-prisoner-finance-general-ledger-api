package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.specifications

import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

object PostingsSpecification {

  fun bySubAccountIds(subAccountIds: List<UUID>): Specification<PostingEntity> = Specification { root, _, cb ->
    return@Specification root.get<SubAccountEntity>("subAccountEntity")
      .get<UUID>("id")
      .`in`(subAccountIds)
  }

  fun createdBetween(startDate: Instant?, endDate: Instant?): Specification<PostingEntity> = Specification { root, _, cb ->

    if (startDate == null && endDate == null) return@Specification null

    val transactionTimestamp = root.get<TransactionEntity>("transactionEntity").get<Instant>("timestamp")
    when {
      startDate != null && endDate != null ->
        cb.between(transactionTimestamp, startDate, endDate)

      startDate != null ->
        cb.greaterThanOrEqualTo(transactionTimestamp, startDate)

      else ->
        cb.lessThanOrEqualTo(transactionTimestamp, endDate)
    }
  }

  fun byPostingType(credit: Boolean, debit: Boolean): Specification<PostingEntity> = Specification { root, _, cb ->
    return@Specification when {
      credit && !debit -> cb.equal(root.get<PostingEntity>("type"), PostingType.CR)
      !credit && debit -> cb.equal(root.get<PostingEntity>("type"), PostingType.DR)
      else -> null
    }
  }

  fun byDescription(description: String?): Specification<PostingEntity> = Specification { root, _, cb ->
    description?.trim()?.takeIf { it.isNotBlank() }?.let { trimmedDescription ->
      cb.like(
        cb.lower(root.get<TransactionEntity>("transactionEntity").get("description")),
        "%${trimmedDescription.lowercase()}%",
      )
    }
  }
}
