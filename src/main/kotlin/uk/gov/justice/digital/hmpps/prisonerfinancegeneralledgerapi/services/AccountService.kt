package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import java.util.*

@Service
class AccountService(
  private val accountRepository: AccountRepository,
) {

  fun createAccount(reference: String, createdBy: String): AccountEntity {
    val createdAccountEntity = accountRepository.save(AccountEntity(reference = reference, createdBy = createdBy))
    return createdAccountEntity
  }

  fun readAccount(accountUUID: UUID): AccountEntity? {
    val retrievedAccount = accountRepository.findAccountById(accountUUID)
    return retrievedAccount
  }
}
