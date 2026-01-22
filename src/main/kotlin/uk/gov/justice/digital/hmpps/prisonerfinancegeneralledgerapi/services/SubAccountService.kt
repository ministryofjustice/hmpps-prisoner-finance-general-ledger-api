package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import java.util.UUID

@Service
class SubAccountService(private val subAccountDataRepository: SubAccountDataRepository, private val accountDataRepository: AccountDataRepository) {
  fun createSubAccount(reference: String, createdBy: String, parentAccountId: UUID): SubAccountEntity {
    val parentAccount = accountDataRepository.getReferenceById(parentAccountId)

    val subAccountEntity = SubAccountEntity(parentAccountEntity = parentAccount, reference = reference, createdBy = createdBy)

    val createdSubAccount = subAccountDataRepository.save(subAccountEntity)

    return createdSubAccount
  }

  fun findSubAccounts(accountReference: String?, subAccountReference: String?): List<SubAccountEntity> {
    if (subAccountReference == null || accountReference == null) {
      throw CustomException(
        message = "Both reference and subAccount reference must be provided",
        status = HttpStatus.BAD_REQUEST,
      )
    }

    val retrievedSubAccount = subAccountDataRepository.findByParentAccountEntityReferenceAndReference(accountReference, subAccountReference)

    if (retrievedSubAccount == null) return emptyList()

    return listOf(retrievedSubAccount)
  }

  fun getSubAccountByID(subAccountID: UUID): SubAccountEntity? {
    val retrievedAccount = subAccountDataRepository.findSubAccountEntityById(subAccountID)

    return retrievedAccount
  }
}
