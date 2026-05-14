package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import java.time.Instant
import java.util.UUID

@Service
class PostingBalanceService(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
) {
  enum class BalanceCalculationType {
    FirstPosting,
    FromPreviousStatementBalance,
    FromPreviousPostingBalance,
  }

  private fun applyPostingType(amount: Long, type: PostingType) = if (type == PostingType.CR) amount else -amount

  private fun compareTimestamps(previousPostingTimeStamp: Instant?, statementBalanceTimestamp: Instant?): BalanceCalculationType {
    if (previousPostingTimeStamp == null || statementBalanceTimestamp == null) {
      throw Exception("Unexpected pathway in balance calculation when comparing timestamps")
    }
    if (previousPostingTimeStamp > statementBalanceTimestamp) {
      return BalanceCalculationType.FromPreviousPostingBalance
    } else {
      return BalanceCalculationType.FromPreviousStatementBalance
    }
  }

  private fun calculateNewBalance(
    type: BalanceCalculationType,
    posting: PostingEntity,
    previousPostingBalance: PostingBalanceEntity? = null,
    previousStatementBalance: StatementBalanceEntity? = null,
  ): Long = when {
    type == BalanceCalculationType.FirstPosting -> {
      applyPostingType(posting.amount, posting.type)
    }
    type == BalanceCalculationType.FromPreviousStatementBalance && previousStatementBalance != null -> {
      previousStatementBalance.amount + applyPostingType(posting.amount, posting.type)
    }
    type == BalanceCalculationType.FromPreviousPostingBalance && previousPostingBalance != null -> {
      previousPostingBalance.totalSubAccountBalance + applyPostingType(posting.amount, posting.type)
    }
    else -> throw Exception("Unexpected pathway in calculateNewBalance")
  }

  private fun calculateStrategy(
    previousPostingBalance: PostingBalanceEntity? = null,
    previousStatementBalance: StatementBalanceEntity? = null,
  ): BalanceCalculationType = when {
    previousPostingBalance == null && previousStatementBalance == null -> BalanceCalculationType.FirstPosting
    previousPostingBalance != null && previousStatementBalance == null -> BalanceCalculationType.FromPreviousPostingBalance
    previousPostingBalance == null && previousStatementBalance != null -> BalanceCalculationType.FromPreviousStatementBalance
    else -> compareTimestamps(
      previousPostingBalance?.postingEntity?.transactionEntity?.timestamp,
      previousStatementBalance?.balanceDateTime,
    )
  }

  fun calculatePostingBalance(
    posting: PostingEntity,
  ) {
    val previousPostingBalance = postingBalanceDataRepository.getSubAccountBalanceOrDefault(
      posting.id,
      posting.subAccountEntity.id,
      posting.transactionEntity.timestamp,
    )
    val previousStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(
      posting.subAccountEntity.id,
      posting.transactionEntity.timestamp,
    )

    val balanceCalculationType = calculateStrategy(previousPostingBalance, previousStatementBalance)

    val newBalance = calculateNewBalance(
      type = balanceCalculationType,
      posting = posting,
      previousPostingBalance = previousPostingBalance,
      previousStatementBalance = previousStatementBalance,
    )

    val existingPostingBalance = postingBalanceDataRepository.findByPostingEntity(posting)

    if (existingPostingBalance != null) {
      existingPostingBalance.totalSubAccountBalance = newBalance
      existingPostingBalance.updatedAt = Instant.now()
    }

    postingBalanceDataRepository.save(
      existingPostingBalance ?: PostingBalanceEntity(
        postingEntity = posting,
        totalSubAccountBalance = newBalance,
      ),
    )
  }

  @Transactional
  fun processBalance(postingId: UUID): ProcessBalanceRequest? {
    val posting = postingsDataRepository.findById(postingId).orElseThrow { Exception("Posting not found") }
    calculatePostingBalance(posting = posting)
    return postingsDataRepository.getTheNextSubAccountPostingOrDefault(
      postingId = posting.id,
      subAccountId = posting.subAccountEntity.id,
      transactionTimestamp = posting.transactionEntity.timestamp,
      transactionEntrySequence = posting.transactionEntity.entrySequence,
      postingEntrySequence = posting.entrySequence,
    )?.let { ProcessBalanceRequest.fromPostingEntity(it) }
  }
}
