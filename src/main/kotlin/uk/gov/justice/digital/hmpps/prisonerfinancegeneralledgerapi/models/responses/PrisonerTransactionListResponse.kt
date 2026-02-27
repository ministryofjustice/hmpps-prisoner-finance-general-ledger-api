package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

data class PrisonerTransactionListResponse(
  @field:Schema(description = "A unique ID for the transaction")
  val id: UUID,
  @field:Schema(description = "A description for the transaction")
  val description: String,
  @field:Schema(description = "The time of when the transaction is created in UTC/Instant format")
  val timestamp: Instant,
  // @field:Valid
  @field:Schema(description = "A collection of postings for the transaction")
  val postings: List<PrisonerPostingListResponse>,
) {
  companion object {
    fun fromEntity(transactionEntity: TransactionEntity, prisonerAccountId: UUID): PrisonerTransactionListResponse = PrisonerTransactionListResponse(
      transactionEntity.id,
      transactionEntity.description,
      transactionEntity.timestamp,
      transactionEntity.postings
        .filter {
          it.subAccountEntity.parentAccountEntity.id == prisonerAccountId ||
            it.subAccountEntity.parentAccountEntity.type == AccountType.PRISON
        }
        .map {
          PrisonerPostingListResponse(
            it.id,
            it.type,
            it.amount,
            SubAccountListResponse(
              it.subAccountEntity.id,
              it.subAccountEntity.reference,
              ParentAccountListResponse(
                it.subAccountEntity.parentAccountEntity.id,
                it.subAccountEntity.parentAccountEntity.reference,
                it.subAccountEntity.parentAccountEntity.type,
              ),
            ),
          )
        },
    )
  }
}

data class PrisonerPostingListResponse(
  @field:Schema(description = "A unique ID for the posting")
  val id: UUID,
  @field:Schema(description = "Enumeration of PostingType either CR or DR")
  val type: PostingType,
  @field:Schema(description = "The posting monetary amount in pence")
  val amount: Long,
  // @field:Valid
  @field:Schema(description = "The sub account associated with the posting")
  val subAccount: SubAccountListResponse,
)

data class SubAccountListResponse(
  @field:Schema(description = "A unique ID for the sub-account")
  val id: UUID,
  @field:Schema(description = "A reference to identify the sub-account")
  val subAccountReference: String,
  // @field:Valid
  @field:Schema(description = "The parent account associated with the sub-account")
  val parentAccount: ParentAccountListResponse,
)

data class ParentAccountListResponse(
  @field:Schema(description = "A unique ID for the parent account")
  val id: UUID,
  @field:Schema(description = "A reference to identify the parent account")
  val reference: String,
  @field:Schema(description = "The type of parent account, either Prison or Prisoner (TODO rebase)")
  val type: AccountType,
)
