package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository

@Service
class AccountService(
  private val accountRepository: AccountRepository,
) {

  fun createAccount(reference: String, createdBy: String): Account {
    val createdAccount = accountRepository.save(Account(reference = reference, createdBy = createdBy))
    return createdAccount
  }

  fun readAccount(testAccountRef: String): Account? {
    val retrievedAccount = accountRepository.findByReference(testAccountRef)
    return retrievedAccount
  }
}
