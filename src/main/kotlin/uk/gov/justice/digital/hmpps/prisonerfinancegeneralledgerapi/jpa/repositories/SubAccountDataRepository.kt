package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import java.util.UUID

@Repository
interface SubAccountDataRepository : JpaRepository<SubAccountEntity, UUID> {
  fun findByParentAccountEntityReferenceAndReference(
    accountReference: String,
    subAccountReference: String,
  ): SubAccountEntity?
}
