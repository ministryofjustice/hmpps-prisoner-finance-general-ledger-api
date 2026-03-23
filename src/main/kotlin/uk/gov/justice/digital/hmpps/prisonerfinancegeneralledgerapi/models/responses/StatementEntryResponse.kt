package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

@Schema(description = "A posting with enriched information")
data class StatementEntryResponse(
  @field:Schema(description = "A unique ID for the posting")
  val transactionId: UUID,
  @field:Schema(description = "The date/time of when the posting was created in UTC/Instant format")
  val postingCreatedAt: Instant,
  @field:Schema(description = "The transaction timestamp in UTC/Instant format")
  val transactionTimestamp: Instant,
  @field:Schema(description = "The description of the transaction")
  val description: String,
  @field:Schema(description = "This is the opposite postings from the statement entry")
  val oppositePostings: List<PostingWithAccountsResponse>,
  @field:Schema(description = "This is amount of the statement entry")
  val amount: Long,
  @field:Schema(description = "The posting type of the statement entry")
  val postingType: PostingType,
  @field:Schema(description = "The sub account of the statement entry")
  val subAccount: SubAccountWithParentResponse,
) {
  companion object {
    fun fromEntity(sourcePostingEntity: PostingEntity) = StatementEntryResponse(
      transactionId = sourcePostingEntity.transactionEntity.id,
      postingCreatedAt = sourcePostingEntity.createdAt,
      description = sourcePostingEntity.transactionEntity.description,
      oppositePostings = sourcePostingEntity.transactionEntity.postings
        .filter { posting -> posting.type != sourcePostingEntity.type }
        .map { posting -> PostingWithAccountsResponse.fromEntity(posting) },
      amount = sourcePostingEntity.amount,
      postingType = sourcePostingEntity.type,
      transactionTimestamp = sourcePostingEntity.transactionEntity.timestamp,
      subAccount = SubAccountWithParentResponse.fromEntity(sourcePostingEntity.subAccountEntity),
    )
  }
}

@Schema(description = "An account within the general ledger")
data class BaseAccountResponse(
  @field:Schema(description = "The unique ID for the account")
  val id: UUID,
  @field:Schema(description = "The unique reference for the account")
  val reference: String,
  @field:Schema(description = "The principal users name when the account was created")
  val createdBy: String,
  @field:Schema(description = "The date/time of when the account was created in UTC/Instant format")
  val createdAt: Instant,
  @field:Schema(description = "The type of account (PRISONER or PRISON)", example = "PRISONER", required = true)
  val type: AccountType,
) {
  companion object {
    fun fromEntity(accountEntity: AccountEntity): BaseAccountResponse = BaseAccountResponse(
      accountEntity.id,
      accountEntity.reference,
      accountEntity.createdBy,
      accountEntity.createdAt,
      accountEntity.type,
    )
  }
}

@Schema(description = "Posting response of statement entry")
data class PostingWithAccountsResponse(
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
  @field:Schema(description = "The sub-account with parent associated with the posting")
  val subAccount: SubAccountWithParentResponse,
) {
  companion object {
    fun fromEntity(postingEntity: PostingEntity): PostingWithAccountsResponse = PostingWithAccountsResponse(
      postingEntity.id,
      postingEntity.createdBy,
      postingEntity.createdAt,
      postingEntity.type,
      postingEntity.amount,
      SubAccountWithParentResponse.fromEntity(postingEntity.subAccountEntity),
    )
  }
}

@Schema(description = "A sub-account of an account")
data class SubAccountWithParentResponse(
  @field:Schema(description = "The unique ID for the sub-account")
  val id: UUID,
  @field:Schema(description = "The reference of the sub-account this is unique within the parent account")
  val reference: String,
  @field:Schema(description = "The parent account")
  val parentAccount: BaseAccountResponse,
  @field:Schema(description = "The principal users name when the sub-account was created")
  val createdBy: String,
  @field:Schema(description = "The date/time when the sub-account was created in UTC/Instant format")
  val createdAt: Instant,
) {

  companion object {
    fun fromEntity(subAccountEntity: SubAccountEntity): SubAccountWithParentResponse = SubAccountWithParentResponse(
      id = subAccountEntity.id,
      reference = subAccountEntity.reference,
      parentAccount = BaseAccountResponse.fromEntity(subAccountEntity.parentAccountEntity),
      createdBy = subAccountEntity.createdBy,
      createdAt = subAccountEntity.createdAt,
    )
  }
}
