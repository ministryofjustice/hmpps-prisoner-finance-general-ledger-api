package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.PostingRequest
import java.math.BigInteger

@Service
class TransactionService(
  private val transactionDataRepository: TransactionDataRepository,
) {

  fun createTransaction(reference: String, createdBy: String, description: String, amount: BigInteger, postings: List<PostingRequest>): TransactionEntity {
    val transactionEntity = TransactionEntity()
    return transactionEntity
  }
}
