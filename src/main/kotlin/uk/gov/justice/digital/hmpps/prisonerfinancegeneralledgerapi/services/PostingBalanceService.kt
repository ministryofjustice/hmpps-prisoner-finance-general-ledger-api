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

  inner class SubAccountBalanceResources(
    val latestPostingBalance: PostingBalanceEntity?,
    val latestStatementBalance: StatementBalanceEntity?,
  ) {
    fun calculateSubAccountBalance(): Long {
      val type = calculateStrategy(
        previousPostingBalance = this.latestPostingBalance,
        previousStatementBalance = this.latestStatementBalance,
      )
      return when {
        type == BalanceCalculationType.FirstPosting -> 0

        type == BalanceCalculationType.FromPreviousStatementBalance && this.latestStatementBalance != null -> {
          latestStatementBalance.amount
        }

        type == BalanceCalculationType.FromPreviousPostingBalance && latestPostingBalance != null -> {
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

  fun calculatePostingBalances(
    posting: PostingEntity,
  ) {
    val parentAccountId = posting.subAccountEntity.parentAccountEntity.id

    val postingSubAccount = posting.subAccountEntity
    val previousPostingBalances = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
      postingId = posting.id,
      accountId = parentAccountId,
      transactionTimestamp = posting.transactionEntity.timestamp,
    )
    val previousStatementBalances = statementBalanceDataRepository.getLatestStatementBalancesForAccountId(
      accountId = parentAccountId,
      fromTimestamp = posting.transactionEntity.timestamp,
    )

    val subAccountBalances = postingSubAccount.parentAccountEntity.subAccounts.associateWith {
      SubAccountBalanceResources(
        latestPostingBalance = previousPostingBalances.firstOrNull { pb -> pb.postingEntity.subAccountEntity.id == it.id },
        latestStatementBalance = previousStatementBalances.firstOrNull { sb -> sb.subAccountEntity.id == it.id },
      )
    }

    val postingSubAccountResource = subAccountBalances.getValue(postingSubAccount)

    val newSubAccountBalance =
      applyPostingType(posting.amount, posting.type) + postingSubAccountResource.calculateSubAccountBalance()
    val newTotalBalance =
      applyPostingType(posting.amount, posting.type) + subAccountBalances.mapValues { (_, v) -> v.calculateSubAccountBalance() }.values.sum()

    updateOrCreatePostingBalance(
      posting = posting,
      newSubAccountBalance = newSubAccountBalance,
      newTotalBalance = newTotalBalance,
    )
  }

  @Transactional
  fun processBalance(postingId: UUID): ProcessBalanceRequest? {
    val posting = postingsDataRepository.findById(postingId).orElseThrow { Exception("Posting not found") }
    calculatePostingBalances(posting = posting)
    return postingsDataRepository.getTheNextAccountPostingOrNull(
      postingId = posting.id,
      accountId = posting.subAccountEntity.id,
      transactionTimestamp = posting.transactionEntity.timestamp,
      transactionEntrySequence = posting.transactionEntity.entrySequence,
      postingEntrySequence = posting.entrySequence,
    )?.let { ProcessBalanceRequest.fromPostingEntity(it) }
  }
}
