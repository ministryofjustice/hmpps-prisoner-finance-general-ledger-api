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
        val nextPosting = postingBalanceService.processBalance(postingId)

        if (nextPosting != null) {
          messagePublisher.sendMessage(
            payloadDataClass = nextPosting,
            queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
          )
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
