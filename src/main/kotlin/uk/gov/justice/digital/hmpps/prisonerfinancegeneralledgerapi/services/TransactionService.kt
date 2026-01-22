package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import java.util.UUID

@Service
class TransactionService(
  private val transactionDataRepository: TransactionDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val subAccountDataRepository: SubAccountDataRepository,
) {
  @Transactional()
  fun createTransaction(createTxReq: CreateTransactionRequest, createdBy: String): TransactionEntity {
    val transactionEntity = TransactionEntity(reference = createTxReq.reference, createdBy = createdBy, description = createTxReq.description, amount = createTxReq.amount, timestamp = createTxReq.timestamp)
    transactionDataRepository.save(transactionEntity)

    val postingEntities = createTxReq.postings.map {
      PostingEntity(
        createdBy = createdBy,
        createdAt = transactionEntity.createdAt,
        type = it.type,
        amount = it.amount,
        subAccountEntity = subAccountDataRepository.getReferenceById(it.subAccountId),
        transactionEntity = transactionEntity,
      )
    }
    postingsDataRepository.saveAll(postingEntities)

    transactionEntity.postings.addAll(postingEntities)

    return transactionEntity
  }

  fun readTransaction(transactionId: UUID): TransactionEntity? {
    val transactionEntity = transactionDataRepository.findTransactionById(transactionId)
    return transactionEntity
  }
}
