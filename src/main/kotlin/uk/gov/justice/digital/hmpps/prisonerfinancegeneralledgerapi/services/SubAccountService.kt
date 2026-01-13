package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccount
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountRepository
import java.util.UUID

class SubAccountService(private val subAccountRepository: SubAccountRepository, private val accountRepository: AccountRepository) {
  fun createSubAccount(reference: String, createdBy: String, parentAccountId: UUID): SubAccount {
    val account = accountRepository.getReferenceById(parentAccountId)

    val subAccount = SubAccount(parentAccount = account, reference = reference, createdBy = createdBy)
    val createdSubAccount = subAccountRepository.save(subAccount)

    return createdSubAccount
  }
}
