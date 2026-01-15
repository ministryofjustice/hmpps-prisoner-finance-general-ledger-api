package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountRepository
import java.util.UUID

@Service
class SubAccountService(private val subAccountRepository: SubAccountRepository, private val accountRepository: AccountRepository) {
  fun createSubAccount(reference: String, createdBy: String, parentAccountId: UUID): SubAccountEntity {
    val parentAccount = accountRepository.getReferenceById(parentAccountId)

    val subAccountEntity = SubAccountEntity(parentAccountEntity = parentAccount, reference = reference, createdBy = createdBy)

    val createdSubAccount = subAccountRepository.save(subAccountEntity)

    return createdSubAccount
  }
}
