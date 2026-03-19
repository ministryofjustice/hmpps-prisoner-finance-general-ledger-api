package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
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
    fun fromEntity(sourcePostingEntity: PostingEntity) : PrisonerPostingsResponse {

      val oppositePosting = sourcePostingEntity.transactionEntity.postings.find { posting -> posting.type != sourcePostingEntity.type}
        ?: throw Exception("Unexpected exception not found opposite posting type")
      val locationOrNull = if (oppositePosting.subAccountEntity.parentAccountEntity.type == AccountType.PRISON)
        oppositePosting.subAccountEntity.parentAccountEntity.reference else null

      return PrisonerPostingsResponse(
        id = sourcePostingEntity.id,
        createdAt = sourcePostingEntity.createdAt,
        description = sourcePostingEntity.transactionEntity.description,
        sourcePosting = sourcePostingEntity,
        oppositePosting = oppositePosting,
        location = locationOrNull,
      )
    }
  }
}
