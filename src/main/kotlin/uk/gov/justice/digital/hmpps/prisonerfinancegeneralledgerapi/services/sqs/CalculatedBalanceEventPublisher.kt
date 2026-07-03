package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest

@Service
class CalculatedBalanceEventPublisher(
  private val messagePublisher: MessagePublisher,
  private val postingsDataRepository: PostingsDataRepository,
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
) {

  fun requestCalculatedBalanceForTransaction(transactionEntity: TransactionEntity) {
    transactionEntity.postings.forEach { posting ->
      val accountId = posting.subAccountEntity.parentAccountEntity.id
      try {
        val payload = ProcessBalanceRequest.fromPostingEntity(posting, source = "requestCalculatedBalanceForTransaction", chainPosition = 0)

        messagePublisher.sendMessage(
          payloadDataClass = payload,
          queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
          messageGroupId = accountId.toString(),
        )
      } catch (e: Exception) {
        log.error("Failed send balanceCalculation to queue for Transaction: ${transactionEntity.id} Posting: ${posting.id}", e)
      }
    }
  }

  @Transactional
  fun requestCalculatedBalanceForStatementBalance(statementBalanceEntity: StatementBalanceEntity) {
    try {
      val posting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        accountId = statementBalanceEntity.subAccountEntity.parentAccountEntity.id,
        dateTime = statementBalanceEntity.balanceDateTime,
      )

      if (posting != null) {
        val payload = ProcessBalanceRequest.fromPostingEntity(posting = posting, source = "requestCalculatedBalanceForStatementBalance", chainPosition = 0)

        val pb = posting.postingBalanceEntity

        if (pb != null) {
          postingBalanceDataRepository.delete(pb)
          posting.postingBalanceEntity = null
        }

        messagePublisher.sendMessage(
          payloadDataClass = payload,
          queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
          messageGroupId = posting.subAccountEntity.parentAccountEntity.id.toString(),
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
