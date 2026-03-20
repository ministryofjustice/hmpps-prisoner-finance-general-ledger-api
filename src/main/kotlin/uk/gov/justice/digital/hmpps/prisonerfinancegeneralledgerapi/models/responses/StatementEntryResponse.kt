package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

@Schema(description = "A posting with enriched information")
data class StatementEntryResponse(
  @field:Schema(description = "A unique ID for the posting")
  val transactionId: UUID,
  @field:Schema(description = "The date/time of when the posting was created in UTC/Instant format")
  val postingCreatedAt: Instant,
  @field:Schema(description = "The description of the transaction")
  val description: String,
  @field:Schema(description = "This is the opposite postings from the statement entry")
  val oppositePostings: List<PostingEntity>,
  @field:Schema(description = "This is amount of the statement entry")
  val amount: Long,
  @field:Schema(description = "The posting type of the statement entry")
  val postingType: PostingType,
  @field:Schema(description = "The sub account of the statement entry")
  val subAccount: SubAccountEntity,
) {
  companion object {
    fun fromEntity(sourcePostingEntity: PostingEntity) = StatementEntryResponse(
      transactionId = sourcePostingEntity.transactionEntity.id,
      postingCreatedAt = sourcePostingEntity.createdAt,
      description = sourcePostingEntity.transactionEntity.description,
      oppositePostings = sourcePostingEntity.transactionEntity.postings
        .filter { posting -> posting.type != sourcePostingEntity.type },
      amount = sourcePostingEntity.amount,
      postingType = sourcePostingEntity.type,
      subAccount = sourcePostingEntity.subAccountEntity,
    )
  }
}
