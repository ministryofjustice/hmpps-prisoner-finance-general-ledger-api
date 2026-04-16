package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
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
    )
  }
}
