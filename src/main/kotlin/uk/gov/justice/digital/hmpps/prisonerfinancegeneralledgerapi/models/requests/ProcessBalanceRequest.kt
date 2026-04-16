package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.PayloadDataClass
import java.time.Instant
import java.util.UUID

data class ProcessBalanceRequest(
  val postingId: UUID,
  val amount: Long,
  val type: PostingType,
  val transactionId: UUID,
  val transactionTimestamp: Instant,
  val transactionEntrySequence: Long,
  val postingEntrySequence: Long,
  val subAccountId: UUID,
  val parentAccountId: UUID,
  val parentAccountType: AccountType,
) : PayloadDataClass {
  companion object {
    fun fromPostingEntity(posting: PostingEntity): ProcessBalanceRequest = ProcessBalanceRequest(
      postingId = posting.id,
      amount = posting.amount,
      type = posting.type,
      transactionId = posting.transactionEntity.id,
      transactionTimestamp = posting.transactionEntity.timestamp,
      transactionEntrySequence = posting.transactionEntity.entrySequence,
      postingEntrySequence = posting.entrySequence,
      subAccountId = posting.subAccountEntity.id,
      parentAccountId = posting.subAccountEntity.parentAccountEntity.id,
      parentAccountType = posting.subAccountEntity.parentAccountEntity.type,
    )
  }
}
