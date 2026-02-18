package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountBalanceResponse
import java.time.Instant
import java.util.UUID

@Service
class SubAccountService(
  private val subAccountDataRepository: SubAccountDataRepository,
  private val accountDataRepository: AccountDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val statementBalanceDataRepository: StatementBalanceDataRepository,
) {
  fun createSubAccount(reference: String, createdBy: String, parentAccountId: UUID): SubAccountEntity {
    val parentAccount = accountDataRepository.findAccountById(parentAccountId) ?: throw CustomException("Account not found", HttpStatus.NOT_FOUND)

    val subAccountEntity = SubAccountEntity(parentAccountEntity = parentAccount, reference = reference, createdBy = createdBy)

    val createdSubAccount = subAccountDataRepository.save(subAccountEntity)

    return createdSubAccount
  }

  fun findSubAccounts(accountReference: String, subAccountReference: String): List<SubAccountEntity> {
    val retrievedSubAccount = subAccountDataRepository.findByParentAccountEntityReferenceAndReference(accountReference, subAccountReference)

    if (retrievedSubAccount == null) return emptyList()

    return listOf(retrievedSubAccount)
  }

  fun getSubAccountByID(subAccountID: UUID): SubAccountEntity? {
    val retrievedAccount = subAccountDataRepository.getSubAccountEntityById(subAccountID)

    return retrievedAccount
  }

  fun getSubAccountBalance(subAccountId: UUID): SubAccountBalanceResponse? {
    val subAccount = subAccountDataRepository.getSubAccountEntityById(subAccountId)
    if (subAccount == null) return null

    val latestStatementBalanceEntity = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(subAccount.id)
    val latestStatementBalanceDatetime = latestStatementBalanceEntity?.balanceDateTime

    val postingsBalanceAfterStatementBalance = postingsDataRepository.getBalanceForSubAccount(subAccountId, latestStatementBalanceDatetime)
    val totalBalance = postingsBalanceAfterStatementBalance + (latestStatementBalanceEntity?.amount ?: 0L)

    return SubAccountBalanceResponse(subAccountId, Instant.now(), totalBalance)
  }

  fun createStatementBalance(subAccountID: UUID, amount: Long, balanceDateTime: Instant): StatementBalanceEntity? {
    val subAccount = subAccountDataRepository.getSubAccountEntityById(subAccountID)
    if (subAccount == null) return null
    val entity = StatementBalanceEntity(subAccountEntity = subAccount, amount = amount, balanceDateTime = balanceDateTime)
    return statementBalanceDataRepository.save(entity)
  }
}
