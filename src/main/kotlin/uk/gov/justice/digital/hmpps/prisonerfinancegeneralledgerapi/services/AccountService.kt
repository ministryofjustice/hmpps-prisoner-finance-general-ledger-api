package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountBalanceResponse
import java.time.LocalDateTime
import java.util.*

@Service
class AccountService(
  private val accountDataRepository: AccountDataRepository,
  private val postingsDataRepository: PostingsDataRepository,

) {

  fun createAccount(reference: String, createdBy: String): AccountEntity {
    val createdAccountEntity = accountDataRepository.save(AccountEntity(reference = reference, createdBy = createdBy))
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

  fun calculateAccountBalance(accountId: UUID): AccountBalanceResponse? {
    val account = accountDataRepository.findAccountById(accountId)
    if (account == null) return null

    val balance = postingsDataRepository.getBalanceForAccount(accountId)

    return AccountBalanceResponse(accountId, LocalDateTime.now(), balance)
  }
}
