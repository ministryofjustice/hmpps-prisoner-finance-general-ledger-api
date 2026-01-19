package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository

@Service
class TransactionService(
  private val transactionDataRepository: TransactionDataRepository,
) {

  fun createTransaction(reference: String, createdBy: String): TransactionEntity {
    val transactionEntity = TransactionEntity()
    return transactionEntity
  }
}
