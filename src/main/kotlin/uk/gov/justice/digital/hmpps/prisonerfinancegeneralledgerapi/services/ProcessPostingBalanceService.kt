package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import java.util.UUID

@Service
class ProcessPostingBalanceService(
  private val postingsDataRepository: PostingsDataRepository,
  private val postingBalanceService: PostingBalanceService,
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  @Transactional
  fun processBalance(accountId: UUID) {
    var posting: PostingEntity? = postingsDataRepository.getFirstMissingPostingBalanceByAccountId(accountId)

    log.debug("Processing posting: ${posting?.id}")
    while (posting != null) {
      postingBalanceService.calculatePostingBalances(posting = posting)

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
