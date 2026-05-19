package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SqsException
import uk.gov.justice.hmpps.sqs.HmppsQueueService

// Add this interface to dataclasses that need to be published to SQS
interface PayloadDataClass

@Service
class MessagePublisher(
  private val hmppsQueueService: HmppsQueueService,
  private val objectMapper: ObjectMapper,
) {
  fun <PayloadDataClass> sendMessage(payloadDataClass: PayloadDataClass, queueId: String) {
    val queue = hmppsQueueService.findByQueueId(queueId)
      ?: throw NoSuchElementException("Queue with id $queueId not found")

    val json = objectMapper.writeValueAsString(payloadDataClass)

    queue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queue.queueUrl)
        .messageBody(json)
        .build(),
    ).whenComplete { response, throwable ->
      if (throwable != null) {
        val actualError = throwable.cause ?: throwable

        log.error("ERROR: Failed to send message to SQS.\nError Message: ${actualError.message}")

        if (actualError is SqsException) {
          log.error("AWS Error Code: ${actualError.awsErrorDetails().errorCode()}")
          log.error("Request ID: ${actualError.requestId()}")
        }
      } else {
        log.debug("SUCCESS: Message sent. ID: ${response.messageId()}")
      }
    }
  }
  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
