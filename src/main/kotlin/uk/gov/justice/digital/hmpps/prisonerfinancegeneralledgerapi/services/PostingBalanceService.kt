package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository

@Service
class PostingBalanceService(
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
  ) {


  fun calculatePostingBalance(
    posting: PostingEntity,
  ){
    val previousPostingBalanceAmount = postingBalanceDataRepository.getSubAccountBalanceOrDefault(
      posting.subAccountEntity.id, posting.transactionEntity.timestamp)

    val previousStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(
      posting.subAccountEntity.id,)
  }
}