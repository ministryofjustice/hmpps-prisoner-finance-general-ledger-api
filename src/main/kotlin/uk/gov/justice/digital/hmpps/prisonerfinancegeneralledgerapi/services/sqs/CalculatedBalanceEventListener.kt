package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.LogSqsCalculatedBalances
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.LogSqsBalancesStatusType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.ProcessPostingBalanceService
import java.time.Instant

@Service
class CalculatedBalanceEventListener(
  private val objectMapper: ObjectMapper,
  private val processPostingBalanceService: ProcessPostingBalanceService,
  private val logSqsCalculatedBalanceService: LogSqsCalculatedBalanceService,
) {

  @SqsListener(
    SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "10",
    maxMessagesPerPoll = "10",
  )
  @Transactional
  fun handleEvents(requestJson: String?) {
    try {
      val processBalanceRequest = objectMapper.readValue(requestJson, ProcessBalanceRequest::class.java)
      processPostingBalanceService.processBalance(processBalanceRequest.accountId)

      logSqsCalculatedBalanceService.save(
        LogSqsCalculatedBalances(
          accountId = processBalanceRequest.accountId,
          postingId = processBalanceRequest.postingId,
          status = LogSqsBalancesStatusType.PROCESSED,
          timestamp = Instant.now(),
        ),
      )
    } catch (e: Exception) {
      log.error("Failed to process balance calculation.\n${e.message}\nMessage will be retried. Payload: $requestJson", e)
      throw e
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
