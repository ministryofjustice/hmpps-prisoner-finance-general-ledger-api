package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest

@Service
class CalculatedBalanceEventPublisher(
  private val messagePublisher: MessagePublisher,
  private val postingsDataRepository: PostingsDataRepository,
) {
  fun requestCalculatedBalanceForTransaction(transactionEntity: TransactionEntity) {
    transactionEntity.postings.forEach { posting ->
      try {
        messagePublisher.sendMessage(
          payloadDataClass = ProcessBalanceRequest.fromPostingEntity(posting),
          queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
        )
      } catch (e: Exception) {
        log.error("Failed send balanceCalculation to queue for Transaction: ${transactionEntity.id} Posting: ${posting.id}", e)
      }
    }
  }

  fun requestCalculatedBalanceForStatementBalance(statementBalanceEntity: StatementBalanceEntity) {
    try {
      val posting = postingsDataRepository.getFirstPostingForSubAccountIdAfterDateTime(
        subAccountId = statementBalanceEntity.subAccountEntity.id,
        dateTime = statementBalanceEntity.balanceDateTime,
      )

      if (posting != null) {
        messagePublisher.sendMessage(
          payloadDataClass = ProcessBalanceRequest.fromPostingEntity(posting),
          queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
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
