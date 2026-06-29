package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import java.time.Instant

@Service
class CalculatedBalanceEventPublisher(
  private val messagePublisher: MessagePublisher,
  private val postingsDataRepository: PostingsDataRepository,
) {
  private fun requestProcessBalance(posting: PostingEntity, requestTime: Instant) {
    val requestTime = Instant.now()
    messagePublisher.sendMessage(
      payloadDataClass = ProcessBalanceRequest.fromPostingEntity(posting, requestTime),
      queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
      messageGroupId = posting.subAccountEntity.parentAccountEntity.id.toString(),
    )
    // TODO save requestTime in DB
  }

  fun requestCalculatedBalanceForTransaction(transactionEntity: TransactionEntity) {
    transactionEntity.postings.forEach { posting ->
      try {
        requestProcessBalance(posting = posting, requestTime = Instant.now())
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

      if (posting != null) {
        requestProcessBalance(posting = posting, requestTime = Instant.now())
      }
    } catch (e: Exception) {
      log.error("Failed send balanceCalculation to queue for Statement: ${statementBalanceEntity.id} ", e)
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
