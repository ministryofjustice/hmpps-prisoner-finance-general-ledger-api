package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import java.util.*

@Service
class AccountService(
  private val accountDataRepository: AccountDataRepository,
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
    val retrievedAccounts = accountDataRepository.findAccountsByReference(reference)
    return retrievedAccounts
  }
}
