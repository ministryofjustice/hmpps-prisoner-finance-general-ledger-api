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
import java.time.Instant

@Service
class PostingBalanceService(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
  private val postingDataRepository: PostingsDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
) {
  enum class BalanceCalculationType {
    FirstPosting,
    FromPreviousStatementBalance,
    FromPreviousPostingBalance,
  }

  private fun applyPostingType(amount: Long, type: PostingType) = if (type == PostingType.CR) amount else -amount

  private fun compareTimestamps(timestamp1: Instant?, timestamp2: Instant?): Int {
    if (timestamp1 == null || timestamp2 == null) return 0
    return if (timestamp1 > timestamp2) {
      1
    } else {
      -1
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
    compareTimestamps(
      previousPostingBalance?.postingEntity?.transactionEntity?.timestamp,
      previousStatementBalance?.balanceDateTime,
    ) == 1 -> BalanceCalculationType.FromPreviousPostingBalance
    compareTimestamps(
      previousPostingBalance?.postingEntity?.transactionEntity?.timestamp,
      previousStatementBalance?.balanceDateTime,
    ) == -1 -> BalanceCalculationType.FromPreviousStatementBalance
    else -> throw Exception("Unexpected pathway in balance calculation")
  }

  fun calculatePostingBalance(
    posting: PostingEntity,
  ) {
    val previousPostingBalance = postingBalanceDataRepository.getSubAccountBalanceOrDefault(
      posting.subAccountEntity.id,
      posting.transactionEntity.timestamp,
    )
    val previousStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(
      posting.subAccountEntity.id,
    )

    val balanceCalculationType = calculateStrategy(previousPostingBalance, previousStatementBalance)
    val newBalance = calculateNewBalance(
      type = balanceCalculationType,
      posting = posting,
      previousPostingBalance = previousPostingBalance,
      previousStatementBalance = previousStatementBalance,
    )

    val postingBalanceEntity = postingBalanceDataRepository.save(
      PostingBalanceEntity(
        postingEntity = posting,
        totalSubAccountBalance = newBalance,
      ),
    )

    posting.postingBalanceEntity = postingBalanceEntity
    postingDataRepository.save(posting)
  }

  @Transactional
  fun migrateAllPostingBalances() {
    postingDataRepository.getAllPostingsOrdered().forEach { posting ->
      calculatePostingBalance(posting)
    }
  }
}
