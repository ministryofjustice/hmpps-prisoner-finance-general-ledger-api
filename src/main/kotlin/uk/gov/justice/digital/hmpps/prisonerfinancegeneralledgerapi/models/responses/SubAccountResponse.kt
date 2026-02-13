package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import java.time.Instant
import java.util.UUID

@Schema(description = "A sub-account of an account")
class SubAccountResponse(
  @field:Schema(description = "The unique ID for the sub-account")
  val id: UUID,
  @field:Schema(description = "The reference of the sub-account this is unique within the parent account")
  val reference: String,
  @field:Schema(description = "The unique ID of the parent account")
  val parentAccountId: UUID,
  @field:Schema(description = "The principal users name when the sub-account was created")
  val createdBy: String,
  @field:Schema(description = "The date/time when the sub-account was created in UTC/Instant format")
  val createdAt: Instant,
) {

  companion object {
    fun fromEntity(subAccountEntity: SubAccountEntity): SubAccountResponse = SubAccountResponse(subAccountEntity.id, subAccountEntity.reference, subAccountEntity.parentAccountEntity.id, subAccountEntity.createdBy, subAccountEntity.createdAt)
  }
}
