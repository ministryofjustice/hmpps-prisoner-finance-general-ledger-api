package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import java.time.Instant
import java.util.UUID

@Service
class PostingBalanceService(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
) {


  fun calculatePostingBalance(
    subAccountId: UUID,
    posting: PostingEntity,
  ){
    val previousPostingBalanceAmount = postingBalanceDataRepository.getSubAccountBalanceOrDefault(
      subAccountId, posting.transactionEntity.timestamp) ?: 0
  }
}