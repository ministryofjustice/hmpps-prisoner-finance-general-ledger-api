package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import jakarta.validation.constraints.NotBlank
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import java.time.LocalDateTime
import java.util.UUID

class SubAccountResponse(
  @field:NotBlank
  val id: UUID,
  @field:NotBlank
  val reference: String,
  @field:NotBlank
  val parentAccountId: UUID,
  @field:NotBlank
  val createdBy: String,
  @field:NotBlank
  val createdAt: LocalDateTime,
) {

  companion object {
    fun fromEntity(subAccountEntity: SubAccountEntity): SubAccountResponse = SubAccountResponse(subAccountEntity.id, subAccountEntity.reference, subAccountEntity.parentAccountEntity.id, subAccountEntity.createdBy, subAccountEntity.createdAt)
  }
}
