package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import java.time.Instant
import java.util.UUID

@Schema(description = "A posting with enriched information")
data class PrisonerPostingsResponse(
  @field:Schema(description = "A unique ID for the posting")
  val id: UUID,
  @field:Schema(description = "The date/time of when the posting was created in UTC/Instant format")
  val createdAt: Instant,
  @field:Schema(description = "The description of the transaction")
  val description: String,
  @field:Schema(description = "This is the posting from the transaction source")
  val sourcePosting: PostingEntity,
  @field:Schema(description = "This is the opposite posting from the transaction")
  val oppositePosting: PostingEntity,
  @field:Schema(description = "This is the caseload of the prison account if the transaction is between prison and prisoner")
  val location: String?,
) {
  companion object {
    fun fromEntity(postingEntity: PostingEntity) = PrisonerPostingsResponse(
      id = postingEntity.id,
      createdAt = postingEntity.createdAt,
      description = "",
      sourcePosting = postingEntity,
      oppositePosting = postingEntity.transactionEntity.postings.find
        { posting -> posting.id != postingEntity.id }
        ?: throw Exception("Unexpected exception opposite posting not found"),
      location = "",
    )
  }
}
