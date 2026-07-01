package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.LogSqsCalculatedBalances
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.LogSqsBalancesStatusType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import java.time.Instant
import java.util.UUID

@Service
class DeleteCalculatedBalanceHelper(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
) {

  @Transactional
  fun deleteFromTimestampByAccountIdTransaction(
    accountId: UUID,
    timestamp: Instant,
  ) {
    postingBalanceDataRepository.deleteFromTimestampByAccountId(
      accountId = accountId,
      timestamp = timestamp,
    )
  }
}

@Service
class CalculatedBalanceEventPublisher(
  private val messagePublisher: MessagePublisher,
  private val postingsDataRepository: PostingsDataRepository,
  private val logSqsCalculatedBalanceService: LogSqsCalculatedBalanceService,
  private val deleteCalculatedBalanceHelper: DeleteCalculatedBalanceHelper,
) {

  fun requestCalculatedBalanceForTransaction(transactionEntity: TransactionEntity) {
    transactionEntity.postings.forEach { posting ->
      try {
        val payload = ProcessBalanceRequest.fromPostingEntity(posting, source = "requestCalculatedBalanceForTransaction", chainPosition = 0)

        deleteCalculatedBalanceHelper.deleteFromTimestampByAccountIdTransaction(
          accountId = posting.subAccountEntity.parentAccountEntity.id,
          timestamp = transactionEntity.timestamp,
        )

        messagePublisher.sendMessage(
          payloadDataClass = payload,
          queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
          messageGroupId = posting.subAccountEntity.parentAccountEntity.id.toString(),
        )
        logSqsCalculatedBalanceService.save(
          LogSqsCalculatedBalances(
            accountId = payload.accountId,
            postingId = payload.postingId,
            status = LogSqsBalancesStatusType.ADDED,
            timestamp = Instant.now(),
          ),
        )
      } catch (e: Exception) {
        log.error("Failed send balanceCalculation to queue for Transaction: ${transactionEntity.id} Posting: ${posting.id}", e)
      }
    }
  }

  fun requestCalculatedBalanceForStatementBalance(statementBalanceEntity: StatementBalanceEntity) {
    try {
      val posting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        accountId = statementBalanceEntity.subAccountEntity.parentAccountEntity.id,
        dateTime = statementBalanceEntity.balanceDateTime,
      )

      deleteCalculatedBalanceHelper.deleteFromTimestampByAccountIdTransaction(
        accountId = statementBalanceEntity.subAccountEntity.parentAccountEntity.id,
        timestamp = statementBalanceEntity.balanceDateTime,
      )

      if (posting != null) {
        val payload = ProcessBalanceRequest.fromPostingEntity(posting = posting, source = "requestCalculatedBalanceForStatementBalance", chainPosition = 0)

        messagePublisher.sendMessage(
          payloadDataClass = payload,
          queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
          messageGroupId = posting.subAccountEntity.parentAccountEntity.id.toString(),
        )
        logSqsCalculatedBalanceService.save(
          LogSqsCalculatedBalances(
            accountId = payload.accountId,
            postingId = payload.postingId,
            status = LogSqsBalancesStatusType.ADDED,
            timestamp = Instant.now(),
          ),
        )
      }
    } catch (e: Exception) {
      log.error("Failed send balanceCalculation to queue for Statement: ${statementBalanceEntity.id} ", e)
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
