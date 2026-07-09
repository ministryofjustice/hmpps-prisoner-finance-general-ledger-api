package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.TELEMETRY_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.ProcessPostingBalanceService
import java.time.Instant

@Service
class CalculatedBalanceEventListener(
  private val objectMapper: ObjectMapper,
  private val processPostingBalanceService: ProcessPostingBalanceService,
  private val telemetryClient: TelemetryClient,
) {

  @SqsListener(
    SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "10",
    maxMessagesPerPoll = "1",
  )
  fun handleEvents(requestJson: String?) {
    try {
      val startTime = Instant.now()
      val processBalanceRequest = objectMapper.readValue(requestJson, ProcessBalanceRequest::class.java)
      processPostingBalanceService.processBalance(processBalanceRequest.accountId)
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-calculated-balance-queue-account-creation-time",
        mapOf(
          "postingId" to processBalanceRequest.postingId.toString(),
          "accountId" to processBalanceRequest.accountId.toString(),
          "timeTaken" to "${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms",
        ),
        null,
      )
      log.debug("Successfully processed balance calculation in ${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms. Payload: $requestJson")
    } catch (e: Exception) {
      log.error("Failed to process balance calculation.\n${e.message}\nMessage will be retried. Payload: $requestJson", e)
      throw e
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
