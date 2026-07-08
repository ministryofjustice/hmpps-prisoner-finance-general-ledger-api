package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections.OppositePostingProjection
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections.StatementEntryProjection
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections.SubAccountWithParentProjection
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
  val oppositePostings: List<StatementEntryOppositePostingsResponse>,
  @field:Schema(description = "This is amount of the statement entry")
  val amount: Long,
  @field:Schema(description = "The posting type of the statement entry")
  val postingType: PostingType,
  @field:Schema(description = "The sub account of the statement entry")
  val subAccount: SubAccountWithParentResponse,
  @field:Schema(description = "The sub account balance at the time the posting was made")
  val subAccountBalance: Long? = null,
  @field:Schema(description = "The overall account balance at the time the posting was made")
  val accountBalance: Long? = null,
) {
  companion object {
    fun fromProjection(
      projection: StatementEntryProjection,
      oppositePostings: List<OppositePostingProjection>,
    ) = StatementEntryResponse(
      transactionId = projection.transactionId,
      postingCreatedAt = projection.postingCreatedAt,
      description = projection.description,
      oppositePostings = oppositePostings.map { StatementEntryOppositePostingsResponse.fromProjection(it) },
      amount = projection.amount,
      postingType = projection.postingType,
      transactionTimestamp = projection.transactionTimestamp,
      subAccount = SubAccountWithParentResponse.fromProjection(projection),
      subAccountBalance = projection.subAccountBalance,
      accountBalance = projection.accountBalance,
    )
  }
}

@Schema(description = "An account within the general ledger")
data class StatementEntryAccountResponse(
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
)

@Schema(description = "Posting response of statement entry")
data class StatementEntryOppositePostingsResponse(
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
    fun fromProjection(projection: OppositePostingProjection): StatementEntryOppositePostingsResponse = StatementEntryOppositePostingsResponse(
      id = projection.postingId,
      createdBy = projection.createdBy,
      createdAt = projection.createdAt,
      type = projection.type,
      amount = projection.amount,
      subAccount = SubAccountWithParentResponse.fromProjection(projection),
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
  val parentAccount: StatementEntryAccountResponse,
  @field:Schema(description = "The principal users name when the sub-account was created")
  val createdBy: String,
  @field:Schema(description = "The date/time when the sub-account was created in UTC/Instant format")
  val createdAt: Instant,
) {

  companion object {
    fun fromProjection(projection: SubAccountWithParentProjection): SubAccountWithParentResponse = SubAccountWithParentResponse(
      id = projection.subAccountId,
      reference = projection.subAccountReference,
      parentAccount = StatementEntryAccountResponse(
        id = projection.parentAccountId,
        reference = projection.parentAccountReference,
        createdBy = projection.parentAccountCreatedBy,
        createdAt = projection.parentAccountCreatedAt,
        type = projection.parentAccountType,
      ),
      createdBy = projection.subAccountCreatedBy,
      createdAt = projection.subAccountCreatedAt,
    )
  }
}
