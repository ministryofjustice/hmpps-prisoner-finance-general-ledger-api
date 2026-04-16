package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingBalanceService

@Service
class CalculateBalanceEventListener(
  private val objectMapper: ObjectMapper,
  private val postingBalanceService: PostingBalanceService,
  private val messagePublisher: MessagePublisher,
) {

  @SqsListener(SqsQueues.CALCULATED_BALANCE, factory = "hmppsQueueContainerFactoryProxy")
  fun handleEvents(requestJson: String?) {
    try {
      val processBalanceRequest = objectMapper.readValue(requestJson, ProcessBalanceRequest::class.java)
      val nextPosting = postingBalanceService.calculatePostingBalance(processBalanceRequest.postingId)

      if (nextPosting != null) {
        messagePublisher.sendMessage(
          payloadDataClass = nextPosting,
          queueId = SqsQueues.CALCULATED_BALANCE,
        )
      }
    } catch (e: Exception) {
      log.error("Failed to process balance calculation.\n${e.message}\nMessage will be retried. Payload: $requestJson", e)
      throw e
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
