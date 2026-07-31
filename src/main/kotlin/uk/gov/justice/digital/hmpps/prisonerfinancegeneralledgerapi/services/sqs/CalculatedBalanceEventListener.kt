package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.annotation.SqsListener
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.TELEMETRY_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.ProcessPostingBalanceService
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Service
class CalculatedBalanceEventListener(
  private val objectMapper: ObjectMapper,
  private val processPostingBalanceService: ProcessPostingBalanceService,
  private val telemetryClient: TelemetryClient,
) {

  @SqsListener(
    SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxMessagesPerPoll = "10",
    acknowledgementMode = "MANUAL",
  )
  fun handleEvents(messages: List<Message<String>>) {
    log.debug("Received ${messages.size} messages from calculated_balance queue")
    val parsedMessages = messages.map { message ->
      message to objectMapper.readValue(message.payload, ProcessBalanceRequest::class.java)
    }

    val groupedByAccount = parsedMessages.groupBy { it.second.accountId }
    val uniqueRequests = mutableListOf<Pair<Message<String>, ProcessBalanceRequest>>()
    val duplicatesToAck = mutableListOf<Message<String>>()

    groupedByAccount.values.forEach { group ->
      uniqueRequests.add(group.first())
      if (group.size > 1) duplicatesToAck.addAll(group.drop(1).map { it.first })
    }

    duplicatesToAck.forEach {
      log.debug("Duplicate message received. Message will be ignored. Payload: ${it.payload}")
      Acknowledgement.acknowledge(it)
    }

    val futures = uniqueRequests.map { (message, processBalanceRequest) ->
      CompletableFuture.runAsync {
        processMessage(processBalanceRequest)
        Acknowledgement.acknowledge(message)
      }
    }
    CompletableFuture.allOf(*futures.toTypedArray()).join()
  }

  private fun processMessage(processBalanceRequest: ProcessBalanceRequest) {
    try {
      val startTime = Instant.now()
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
      log.debug("Successfully processed balance calculation in ${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms. Payload: $processBalanceRequest")
    } catch (e: Exception) {
      log.error("Failed to process balance calculation.\n${e.message}\nMessage will be retried. Payload: $processBalanceRequest", e)
      throw e
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
