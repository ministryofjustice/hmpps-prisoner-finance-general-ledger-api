package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.balances

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import java.time.Instant
import java.util.UUID
import kotlin.collections.set

data class PostingBalanceData(
  val amount: Long,
  val timestamp: Instant,
)

data class Balance(
  val postingBalance: PostingBalanceData,
  val accountBalance: Long,
)

enum class BalanceCalculationStrategy {
  FirstPosting,
  FromPreviousStatementBalance,
  FromPreviousPostingBalance,
}

/*
*
* This class is responsible for re-calculating posting balances,
* It should be passed the initial posting the balance recalculation is starting from during initialisation.
*
* By calling PostingBalanceCalculator.calculate(posting) for each posting (including the one used for initialisation) the class
* returns the subAccount and overAll balance for the posting
*
* */

class PostingBalanceCalculator(
  statementBalanceDataRepository: StatementBalanceDataRepository,
  postingBalancesRepository: PostingBalanceDataRepository,
  private val startPosting: PostingEntity,
) {
  private val previousPostingBalances: List<PostingBalanceEntity>
  private val statementBalances: List<StatementBalanceEntity>

  private val emptyPostingBalanceData = PostingBalanceData(0, Instant.EPOCH)

  private val postingBalanceMap: MutableMap<UUID, PostingBalanceData> = mutableMapOf()

  init {
    /*
     * During initialization the class:
     *  - Gets the most recent posting balances (if there is one) for each sub account before the posting that initialises it
     *  - Gets all statement balances for all subAccounts
     *  - Initialize the internal postingBalanceMap used to calculate posting balances
     * */
    statementBalances =
      statementBalanceDataRepository.getStatementBalancesByAccountDescOrdered(startPosting.subAccountEntity.parentAccountEntity.id)
    previousPostingBalances = postingBalancesRepository.getPreviousPostingBalancesByAccount(
      postingId = startPosting.id,
      accountId = startPosting.subAccountEntity.parentAccountEntity.id,
      transactionTimestamp = startPosting.transactionEntity.timestamp,
      transactionEntrySequence = startPosting.transactionEntity.entrySequence,
      postingEntrySequence = startPosting.entrySequence,
    )

    startPosting.subAccountEntity.parentAccountEntity.subAccounts.forEach { subAccount ->
      val previousPostingBalance =
        previousPostingBalances.firstOrNull { it.postingEntity.subAccountEntity.id == subAccount.id }

      val previousStatementBalance = statementBalances.firstOrNull {
        it.subAccountEntity.id == subAccount.id && it.balanceDateTime <= startPosting.transactionEntity.timestamp
      }

      postingBalanceMap[subAccount.id] = calculatePostingBalanceMapData(
        previousPostingTimestamp = previousPostingBalance?.postingEntity?.transactionEntity?.timestamp,
        previousPostingBalanceAmount = previousPostingBalance?.totalSubAccountBalance,
        previousStatementBalance = previousStatementBalance,
      )
    }
  }

  fun calculate(posting: PostingEntity): Balance {
    /*
     * This method calculates the posting balance keeping in account any previous statement balance
     * */

    val subAccountId = posting.subAccountEntity.id

    val previousStatementBalance = statementBalances.firstOrNull {
      it.subAccountEntity.id == subAccountId && it.balanceDateTime <= posting.transactionEntity.timestamp
    }

    val currentMapValue = postingBalanceMap.getOrDefault(
      subAccountId,
      emptyPostingBalanceData,
    )

    val calculatedMap = calculatePostingBalanceMapData(
      previousPostingTimestamp = currentMapValue.timestamp,
      previousPostingBalanceAmount = currentMapValue.amount,
      previousStatementBalance = previousStatementBalance,
    )

    postingBalanceMap[posting.subAccountEntity.id] = PostingBalanceData(
      amount = calculatedMap.amount + applyPostingType(posting.amount, posting.type),
      timestamp = posting.transactionEntity.timestamp,
    )

    val accountBalance = postingBalanceMap.values.fold(0L) { sum, pbd -> sum + pbd.amount }

    return Balance(
      postingBalance = postingBalanceMap.getValue(posting.subAccountEntity.id),
      accountBalance = accountBalance,
    )
  }

  private fun calculatePostingBalanceMapData(
    previousPostingTimestamp: Instant?,
    previousPostingBalanceAmount: Long?,
    previousStatementBalance: StatementBalanceEntity?,
  ): PostingBalanceData {
    val strategy = balanceCalculationStrategy(
      previousPostingBalanceTimestamp = previousPostingTimestamp,
      previousStatementBalanceTimestamp = previousStatementBalance?.balanceDateTime,
    )
    return when {
      strategy == BalanceCalculationStrategy.FirstPosting -> emptyPostingBalanceData

      strategy == BalanceCalculationStrategy.FromPreviousStatementBalance && previousStatementBalance != null -> {
        PostingBalanceData(previousStatementBalance.amount, previousStatementBalance.balanceDateTime)
      }

      strategy == BalanceCalculationStrategy.FromPreviousPostingBalance && previousPostingTimestamp != null && previousPostingBalanceAmount != null ->
        PostingBalanceData(previousPostingBalanceAmount, previousPostingTimestamp)

      else -> throw Exception("Unexpected pathway in calculatePostingBalanceMapData")
    }
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
    previousPostingBalanceTimestamp: Instant? = null,
    previousStatementBalanceTimestamp: Instant? = null,
  ): BalanceCalculationStrategy = when {
    previousPostingBalanceTimestamp == null && previousStatementBalanceTimestamp == null -> BalanceCalculationStrategy.FirstPosting

    previousPostingBalanceTimestamp != null && previousStatementBalanceTimestamp == null -> BalanceCalculationStrategy.FromPreviousPostingBalance

    previousPostingBalanceTimestamp == null && previousStatementBalanceTimestamp != null -> BalanceCalculationStrategy.FromPreviousStatementBalance

    else -> compareTimestamps(
      previousPostingBalanceTimestamp,
      previousStatementBalanceTimestamp,
    )
  }
}
