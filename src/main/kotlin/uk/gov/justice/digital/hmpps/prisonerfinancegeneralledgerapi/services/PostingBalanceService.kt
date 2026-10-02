package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import java.time.Instant
import java.util.UUID

@Service
class PostingBalanceService(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
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


  private fun getBalanceMap(posting: PostingEntity): MutableMap<UUID, Long> {
    val accountId = posting.subAccountEntity.parentAccountEntity.id

    val previousPostingBalances = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
      postingId = posting.id,
      accountId = accountId,
      transactionTimestamp = posting.transactionEntity.timestamp,
      transactionEntrySequence = posting.transactionEntity.entrySequence,
      postingEntrySequence = posting.entrySequence,
    )

    val statementBalances = statementBalanceDataRepository.getStatementBalancesByAccount(
      accountId = accountId,
    )

    return posting.subAccountEntity.parentAccountEntity.subAccounts.associate { sa ->
      sa.id to SubAccountBalanceCalculator(
        latestPostingBalance = previousPostingBalances.filter {pb -> pb.postingEntity.subAccountEntity.id == sa.id }.firstOrNull(),
        // todo review if this requires filtering and ordering
        latestStatementBalance = statementBalances.filter { sb -> sb.subAccountEntity.id == sa.id }.firstOrNull(),
      ).calculate()
    }.toMutableMap()
  }

  fun calculatePostingBalances(
    startingPosting: PostingEntity
  ) {
    val accountId = startingPosting.subAccountEntity.parentAccountEntity.id

    val balanceMap = getBalanceMap(startingPosting)

    val postings = postingsDataRepository.findAllPostingsFrom(
      accountId = accountId,
      timestamp = startingPosting.transactionEntity.timestamp,
      transSeq = startingPosting.transactionEntity.entrySequence,
      postSeq = startingPosting.entrySequence,
      id = startingPosting.id
    )

    val postingBalances = mutableListOf<PostingBalanceEntity>()
    buildList {
      add(startingPosting)
      addAll(postings)
    }.forEach { posting ->
      val subAccountBalance = balanceMap.getValue(posting.subAccountEntity.id)
      val accountBalance = balanceMap.values.sum()

      val updatedPostingBalance = posting.postingBalanceEntity ?: PostingBalanceEntity(
        postingEntity = posting,
        totalSubAccountBalance = 0,
        totalAccountBalance = 0,
      )

      updatedPostingBalance.totalSubAccountBalance = subAccountBalance + applyPostingType(posting.amount, posting.type)
      updatedPostingBalance.totalAccountBalance = accountBalance + applyPostingType(posting.amount, posting.type)

      balanceMap[posting.subAccountEntity.id] = updatedPostingBalance.totalSubAccountBalance
      postingBalances.add(updatedPostingBalance)
    }

    postingBalanceDataRepository.saveAll(postingBalances)
  }

  fun calculatePostingBalancesOld(
    postings: List<PostingEntity>,
  ) {
    /*
    val parentAccountId = posting.subAccountEntity.parentAccountEntity.id

    val postingSubAccount = posting.subAccountEntity
    val previousPostingBalances = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
      postingId = posting.id,
      accountId = parentAccountId,
      transactionTimestamp = posting.transactionEntity.timestamp,
      transactionEntrySequence = posting.transactionEntity.entrySequence,
      postingEntrySequence = posting.entrySequence,
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

     */
  }
}
