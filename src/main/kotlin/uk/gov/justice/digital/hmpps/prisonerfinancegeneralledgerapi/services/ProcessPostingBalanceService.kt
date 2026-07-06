package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import com.microsoft.applicationinsights.TelemetryClient
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.TELEMETRY_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import java.time.Instant
import java.util.UUID

@Service
class ProcessPostingBalanceService(
  private val postingsDataRepository: PostingsDataRepository,
  private val postingBalanceService: PostingBalanceService,
  private val telemetryClient: TelemetryClient,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Transactional
  fun processBalance(accountId: UUID) {
    var posting: PostingEntity? = postingsDataRepository.getFirstMissingPostingBalanceByAccountId(accountId)

    log.debug("Processing posting: ${posting?.id}")
    while (posting != null) {
      val startTime = Instant.now()

      postingBalanceService.calculatePostingBalances(posting = posting)

      log.debug("Successfully processed posting: ${posting.id} for accountId: $accountId in ${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms")
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-calculated-balance-queue-posting-creation-time",
        mapOf(
          "postingId" to posting.id.toString(),
          "accountId" to accountId.toString(),
          "timeTaken" to "${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms",
        ),
        null,
      )
      posting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = posting.id,
        accountId = posting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = posting.transactionEntity.timestamp,
        transactionEntrySequence = posting.transactionEntity.entrySequence,
        postingEntrySequence = posting.entrySequence,
      )
      log.debug("Next posting to process: ${posting?.id}")
    }
  }
}
