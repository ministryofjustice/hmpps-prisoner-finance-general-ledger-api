package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

@Schema(description = "A posting within a transaction")
class PostingResponse(
  @field:Schema(description = "A unique ID for the posting")
  val id: UUID,
  @field:Schema(description = "The user who created the transaction")
  val createdBy: String,
  @field:Schema(description = "The local date/time of when the posting was created")
  val createdAt: Instant,
  @field:Schema(description = "Enumeration of PostingType either CR or DR")
  val type: PostingType,
  @field:Schema(description = "The posting monetary amount in pence")
  val amount: Long,
  @field:Schema(description = "The ID of the sub-account associated with the posting")
  val subAccountID: UUID,
) {
  companion object {
    fun fromEntity(postingEntity: PostingEntity): PostingResponse = PostingResponse(postingEntity.id, postingEntity.createdBy, postingEntity.createdAt, postingEntity.type, postingEntity.amount, postingEntity.subAccountEntity.id)
  }
}
