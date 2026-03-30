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
  @field:Schema(description = "A collection of postings for the transaction")
  val postings: List<PrisonerPostingListResponse>,
) {
  companion object {
    fun fromEntity(transactionEntity: TransactionEntity, prisonerAccountId: UUID): PrisonerTransactionListResponse = PrisonerTransactionListResponse(
      id = transactionEntity.id,
      description = transactionEntity.description,
      timestamp = transactionEntity.timestamp,
      postings = transactionEntity.postings
        .filter {
          it.subAccountEntity.parentAccountEntity.id == prisonerAccountId ||
            it.subAccountEntity.parentAccountEntity.type == AccountType.PRISON
        }
        .map {
          PrisonerPostingListResponse(
            id = it.id,
            type = it.type,
            amount = it.amount,
            subAccount = SubAccountListResponse(
              id = it.subAccountEntity.id,
              subAccountReference = it.subAccountEntity.reference,
              parentAccount = ParentAccountResponse(
                id = it.subAccountEntity.parentAccountEntity.id,
                reference = it.subAccountEntity.parentAccountEntity.reference,
                type = it.subAccountEntity.parentAccountEntity.type,
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
  @field:Schema(description = "The sub account associated with the posting")
  val subAccount: SubAccountListResponse,
)

data class SubAccountListResponse(
  @field:Schema(description = "A unique ID for the sub-account")
  val id: UUID,
  @field:Schema(description = "A reference to identify the sub-account")
  val subAccountReference: String,
  @field:Schema(description = "The parent account associated with the sub-account")
  val parentAccount: ParentAccountResponse,
)

data class ParentAccountResponse(
  @field:Schema(description = "A unique ID for the parent account")
  val id: UUID,
  @field:Schema(description = "A reference to identify the parent account")
  val reference: String,
  @field:Schema(description = "The type of parent account, either Prison or Prisoner (TODO rebase)")
  val type: AccountType,
)
