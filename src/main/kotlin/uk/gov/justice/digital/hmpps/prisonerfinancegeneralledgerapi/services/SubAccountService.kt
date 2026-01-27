package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountBalanceResponse
import java.time.LocalDateTime
import java.util.UUID

@Service
class SubAccountService(
  private val subAccountDataRepository: SubAccountDataRepository,
  private val accountDataRepository: AccountDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
) {
  fun createSubAccount(reference: String, createdBy: String, parentAccountId: UUID): SubAccountEntity {
    val parentAccount = accountDataRepository.getReferenceById(parentAccountId)

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

    val balance = postingsDataRepository.getBalanceForSubAccount(subAccountId)
    return SubAccountBalanceResponse(subAccountId, LocalDateTime.now(), balance)
  }
}
