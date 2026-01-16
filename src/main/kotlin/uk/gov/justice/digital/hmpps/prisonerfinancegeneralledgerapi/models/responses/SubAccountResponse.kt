package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import java.time.LocalDateTime
import java.util.UUID

class SubAccountResponse(
  val id: UUID,
  val reference: String,
  val parentAccountId: UUID,
  val createdBy: String,
  val createdAt: LocalDateTime,
) {

  companion object {
    fun fromEntity(subAccountEntity: SubAccountEntity): SubAccountResponse = SubAccountResponse(subAccountEntity.id, subAccountEntity.reference, subAccountEntity.parentAccountEntity.id, subAccountEntity.createdBy, subAccountEntity.createdAt)
  }
}
