package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.PostingRequest
import java.math.BigInteger
import java.time.LocalDateTime

@Service
class TransactionService(
  private val transactionDataRepository: TransactionDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
) {

  fun createTransaction(reference: String, createdBy: String, description: String, amount: BigInteger, timestamp: LocalDateTime, postings: List<PostingRequest>): TransactionEntity {
    val transactionEntity = TransactionEntity(reference = reference, createdBy = createdBy, description = description, amount = amount, timestamp = timestamp)
    val savedTransactionData = transactionDataRepository.save(transactionEntity)

    val postingEntities = postings.map { PostingEntity(createdBy = createdBy, createdAt = savedTransactionData.createdAt, type = it.type, amount = it.amount, subAccountEntity = SubAccountEntity(id = it.subAccountId), transactionEntity = TransactionEntity(id = savedTransactionData.id)) }
    postingsDataRepository.saveAll(postingEntities)

    return transactionEntity
  }
}
