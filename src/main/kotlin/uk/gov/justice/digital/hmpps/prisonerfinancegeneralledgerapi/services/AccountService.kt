package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountBalanceResponse
import java.time.Instant
import java.util.UUID

@Service
class AccountService(
  private val accountDataRepository: AccountDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val subAccountService: SubAccountService,

) {

  fun createAccount(reference: String, createdBy: String, accountType: AccountType): AccountEntity {
    val createdAccountEntity = accountDataRepository.save(AccountEntity(reference = reference, createdBy = createdBy, type = accountType))
    return createdAccountEntity
  }

  fun readAccount(accountUUID: UUID): AccountEntity? {
    val retrievedAccount = accountDataRepository.findAccountById(accountUUID)
    return retrievedAccount
  }

  fun findAccounts(reference: String): List<AccountEntity> {
    val retrievedAccount = accountDataRepository.findAccountByReference(reference)
    val listOfAccounts: List<AccountEntity> = if (retrievedAccount != null) listOf(retrievedAccount) else emptyList()
    return listOfAccounts
  }

  fun searchAccounts(references: List<String>): List<AccountEntity> = accountDataRepository.findAccountEntitiesByReferenceIn(references)

  fun calculateAccountBalance(accountId: UUID): AccountBalanceResponse? {
    val account = accountDataRepository.findAccountById(accountId)
    if (account == null) return null

    var balance = 0L
    for (subAccount in account.subAccounts) {
      balance += subAccountService.getSubAccountBalance(subAccount.id)?.amount ?: 0
    }

    return AccountBalanceResponse(accountId, Instant.now(), balance)
  }
}
