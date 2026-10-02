package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.balances

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.TELEMETRY_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import java.time.Instant
import java.util.UUID

@Service
class PostingBalanceService(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val telemetryClient: TelemetryClient,
) {
  fun calculatePostingBalances(
    startingPosting: PostingEntity,
  ) {
    val accountId = startingPosting.subAccountEntity.parentAccountEntity.id

    val postingBalanceCalculator = PostingBalanceCalculator(
      statementBalanceDataRepository = statementBalanceDataRepository,
      postingBalancesRepository = postingBalanceDataRepository,
      startPosting = startingPosting,
    )

    val postings = postingsDataRepository.findAllPostingsAfter(
      accountId = accountId,
      timestamp = startingPosting.transactionEntity.timestamp,
      transSeq = startingPosting.transactionEntity.entrySequence,
      postSeq = startingPosting.entrySequence,
      id = startingPosting.id,
    )

    val postingBalances = mutableListOf<PostingBalanceEntity>()

    buildList {
      add(startingPosting)
      addAll(postings)
    }.forEach { posting ->
      val accountBalance = postingBalanceCalculator.calculate(posting)

      val updatedPostingBalance = posting.postingBalanceEntity ?: PostingBalanceEntity(
        postingEntity = posting,
        totalSubAccountBalance = 0,
        totalAccountBalance = 0,
      )

      updatedPostingBalance.totalSubAccountBalance = accountBalance.postingBalance.amount
      updatedPostingBalance.totalAccountBalance = accountBalance.accountBalance
      updatedPostingBalance.updatedAt = Instant.now()

      postingBalances.add(updatedPostingBalance)
    }

    postingBalanceDataRepository.saveAll(postingBalances)
  }

  private val log = LoggerFactory.getLogger(this::class.java)

  @Transactional(rollbackFor = [Exception::class, Error::class])
  fun processBalance(accountId: UUID) {
    var posting: PostingEntity? = postingsDataRepository.getFirstMissingPostingBalanceByAccountId(accountId)

    if (posting == null) {
      log.info("No balance to process for accountId: $accountId")
      return
    }

    log.debug("Processing postingBalances from posting: ${posting.id}")

    val startTime = Instant.now()

    calculatePostingBalances(startingPosting = posting)

    log.debug("Successfully processed postingBalances from posting: ${posting.id} for accountId: $accountId in ${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms")
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-calculated-balance-queue-posting-creation-time",
      mapOf(
        "postingId" to posting.id.toString(),
        "accountId" to accountId.toString(),
        "timeTaken" to "${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms",
      ),
      null,
    )
  }
}
