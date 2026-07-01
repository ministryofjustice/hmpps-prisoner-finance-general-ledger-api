package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import java.time.Instant

@Service
class PostingBalanceService(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
) {
  enum class BalanceCalculationStrategy {
    FirstPosting,
    FromPreviousStatementBalance,
    FromPreviousPostingBalance,
  }

  private fun applyPostingType(amount: Long, type: PostingType) = if (type == PostingType.CR) amount else -amount

  private fun compareTimestamps(previousPostingTimeStamp: Instant?, statementBalanceTimestamp: Instant?): BalanceCalculationStrategy {
    if (previousPostingTimeStamp == null || statementBalanceTimestamp == null) {
      throw Exception("Unexpected pathway in balance calculation when comparing timestamps")
    }
    if (previousPostingTimeStamp > statementBalanceTimestamp) {
      return BalanceCalculationStrategy.FromPreviousPostingBalance
    } else {
      return BalanceCalculationStrategy.FromPreviousStatementBalance
    }
  }

  private fun balanceCalculationStrategy(
    previousPostingBalance: PostingBalanceEntity? = null,
    previousStatementBalance: StatementBalanceEntity? = null,
  ): BalanceCalculationStrategy = when {
    previousPostingBalance == null && previousStatementBalance == null -> BalanceCalculationStrategy.FirstPosting

    previousPostingBalance != null && previousStatementBalance == null -> BalanceCalculationStrategy.FromPreviousPostingBalance

    previousPostingBalance == null && previousStatementBalance != null -> BalanceCalculationStrategy.FromPreviousStatementBalance

    else -> compareTimestamps(
      previousPostingBalance?.postingEntity?.transactionEntity?.timestamp,
      previousStatementBalance?.balanceDateTime,
    )
  }

  inner class SubAccountBalanceCalculator(
    val latestPostingBalance: PostingBalanceEntity?,
    val latestStatementBalance: StatementBalanceEntity?,
  ) {
    fun calculate(): Long {
      val strategy = balanceCalculationStrategy(
        previousPostingBalance = this.latestPostingBalance,
        previousStatementBalance = this.latestStatementBalance,
      )
      return when {
        strategy == BalanceCalculationStrategy.FirstPosting -> 0

        strategy == BalanceCalculationStrategy.FromPreviousStatementBalance && this.latestStatementBalance != null -> {
          latestStatementBalance.amount
        }

        strategy == BalanceCalculationStrategy.FromPreviousPostingBalance && latestPostingBalance != null -> {
          latestPostingBalance.totalSubAccountBalance
        }
        else -> throw Exception("Unexpected pathway in calculateNewBalance")
      }
    }
  }

  private fun updateOrCreatePostingBalance(
    posting: PostingEntity,
    newSubAccountBalance: Long,
    newTotalBalance: Long,
  ) {
    val existingPostingBalance = postingBalanceDataRepository.findByPostingEntity(posting)

    var postingBalanceToSave: PostingBalanceEntity
    if (existingPostingBalance != null) {
      existingPostingBalance.totalSubAccountBalance = newSubAccountBalance
      existingPostingBalance.totalAccountBalance = newTotalBalance
      existingPostingBalance.updatedAt = Instant.now()
      postingBalanceToSave = existingPostingBalance
    } else {
      postingBalanceToSave = PostingBalanceEntity(
        postingEntity = posting,
        totalSubAccountBalance = newSubAccountBalance,
        totalAccountBalance = newTotalBalance,
      )
    }

    postingBalanceDataRepository.save(postingBalanceToSave)
  }

  @Transactional
  fun calculatePostingBalances(
    posting: PostingEntity,
  ) {
    val parentAccountId = posting.subAccountEntity.parentAccountEntity.id

    val postingSubAccount = posting.subAccountEntity
    val previousPostingBalances = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
      postingId = posting.id,
      accountId = parentAccountId,
      transactionTimestamp = posting.transactionEntity.timestamp,
      transactionEntrySequence = posting.transactionEntity.entrySequence,
      postingEntrySequence = posting.entrySequence,
    )
    val previousStatementBalances = statementBalanceDataRepository.getLatestStatementBalancesForAccountId(
      accountId = parentAccountId,
      fromTimestamp = posting.transactionEntity.timestamp,
    )

    val subAccountBalanceCalculators = postingSubAccount.parentAccountEntity.subAccounts.associateWith {
      SubAccountBalanceCalculator(
        latestPostingBalance = previousPostingBalances.firstOrNull { pb -> pb.postingEntity.subAccountEntity.id == it.id },
        latestStatementBalance = previousStatementBalances.firstOrNull { sb -> sb.subAccountEntity.id == it.id },
      )
    }

    val postingSubAccountResource = subAccountBalanceCalculators.getValue(postingSubAccount)

    val newSubAccountBalance =
      applyPostingType(posting.amount, posting.type) + postingSubAccountResource.calculate()

    val newTotalBalance =
      applyPostingType(posting.amount, posting.type) + subAccountBalanceCalculators.values.sumOf { it.calculate() }

    updateOrCreatePostingBalance(
      posting = posting,
      newSubAccountBalance = newSubAccountBalance,
      newTotalBalance = newTotalBalance,
    )
  }
}
