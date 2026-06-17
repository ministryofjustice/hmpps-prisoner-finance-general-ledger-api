package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

@Schema(description = "A posting within a transaction")
data class SearchPostingResponse(
  @field:Schema(description = "A unique ID for the posting")
  val id: UUID,
  @field:Schema(description = "The user who created the transaction")
  val createdBy: String,
  @field:Schema(description = "The date/time of when the posting was created in UTC/Instant format")
  val createdAt: Instant,
  @field:Schema(description = "Enumeration of PostingType either CR or DR")
  val type: PostingType,
  @field:Schema(description = "The posting monetary amount in pence")
  val amount: Long,
  @field:Schema(description = "The ID of the sub-account associated with the posting")
  val subAccountID: UUID,
  @field:Schema(description = "The reference of the sub-account associated with the posting")
  val subAccountReference: String,
  @field:Schema(description = "The ID of the account associated with the posting")
  val accountID: UUID,
  @field:Schema(description = "The account type, either Prison or Prisoner")
  val accountType: AccountType,
  @field:Schema(description = "The reference of the account associated with the posting")
  val accountReference: String,
  @field:Schema(description = "The entry sequence of the posting")
  val entrySequence: Long,
) {
  companion object {
    fun fromEntity(postingEntity: PostingEntity): SearchPostingResponse = SearchPostingResponse(
      id = postingEntity.id,
      createdBy = postingEntity.createdBy,
      createdAt = postingEntity.createdAt,
      type = postingEntity.type,
      amount = postingEntity.amount,
      subAccountID = postingEntity.subAccountEntity.id,
      subAccountReference = postingEntity.subAccountEntity.reference,
      accountID = postingEntity.subAccountEntity.parentAccountEntity.id,
      accountReference = postingEntity.subAccountEntity.parentAccountEntity.reference,
      entrySequence = postingEntity.entrySequence,
      accountType = postingEntity.subAccountEntity.parentAccountEntity.type,
    )
  }
}
