package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.MessagePublisher
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.SqsQueues

@Service
class MigrateService(
  val postingsDataRepository: PostingsDataRepository,
  val postingBalanceService: PostingBalanceService,
  val messagePublisher: MessagePublisher,
) {
  fun migrateAllPostingBalances() {
    postingsDataRepository.getFirstPostingsForAllSubAccounts().forEach { postingId ->
      try {
        log.debug("getting next posting from $postingId")
        val nextPostingBalanceReq = postingBalanceService.processBalance(postingId)
        log.debug("next posting is $nextPostingBalanceReq")

        if (nextPostingBalanceReq != null) {
          messagePublisher.sendMessage(
            payloadDataClass = nextPostingBalanceReq,
            queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
            messageGroupId = nextPostingBalanceReq.subAccountId.toString(),
          )
          log.debug("message sent to queue for posting: $nextPostingBalanceReq")
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
