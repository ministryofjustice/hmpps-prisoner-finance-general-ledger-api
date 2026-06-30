package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.MessagePublisher
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.SqsQueues

@Service
class MigrateService(
  val postingsDataRepository: PostingsDataRepository,
  val postingBalanceService: PostingBalanceService,
  val messagePublisher: MessagePublisher,
) {
  fun migrateAllPostingBalances() {
    postingsDataRepository.getFirstPostingsForAllAccounts().forEach { postingId ->
      try {
        log.debug("getting next posting from $postingId")
        val nextPostingEntity = postingBalanceService.processBalance(postingId)
        log.debug("next posting is $nextPostingEntity")

        if (nextPostingEntity != null) {

          val processBalanceRequest = ProcessBalanceRequest.fromPostingEntity(posting = nextPostingEntity, source = "migrateAllPostingBalances", chainPosition = 1)

          messagePublisher.sendMessage(
            payloadDataClass = processBalanceRequest,
            queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
            messageGroupId = nextPostingEntity.subAccountEntity.parentAccountEntity.id.toString(),
          )
          log.debug("message sent to queue for posting: $processBalanceRequest")
        }
      } catch (e: Exception) {
        log.error("Failed send posting: ${postingId}\n${e.message}\n${e.stackTrace} ", e)
      }
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
