package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.specifications

import org.springframework.data.jpa.domain.Specification
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import java.time.Instant
import java.util.UUID

object PostingsSpecification {

  fun byParentAccountId(accountId: UUID): Specification<PostingEntity> = Specification { root, _, cb ->

    val subAccount = root.join<PostingEntity, SubAccountEntity>("subAccountEntity")
    val parentAccount = subAccount.join<SubAccountEntity, AccountEntity>("parentAccountEntity")

    cb.equal(parentAccount.get<UUID>("id"), accountId)
  }

  fun createdAfter(startDate: Instant): Specification<PostingEntity> = Specification { root, _, cb ->
    cb.greaterThanOrEqualTo(root.get("createdAt"), startDate)
  }
}
