package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import java.math.BigInteger
import java.time.LocalDateTime

@Service
class TransactionService(
  private val transactionDataRepository: TransactionDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val subAccountDataRepository: SubAccountDataRepository,
) {
  @Transactional()
  fun createTransaction(reference: String, createdBy: String, description: String, amount: BigInteger, timestamp: LocalDateTime, postings: List<CreatePostingRequest>): TransactionEntity {

    val transactionEntity = TransactionEntity(reference = reference, createdBy = createdBy, description = description, amount = amount, timestamp = timestamp)
    transactionDataRepository.save(transactionEntity)

    val postingEntities = postings.map { PostingEntity(createdBy = createdBy, createdAt = transactionEntity.createdAt, type = it.type, amount = it.amount, subAccountEntity = subAccountDataRepository.getReferenceById(it.subAccountId),
      transactionEntity = TransactionEntity(id = transactionEntity.id)) }
    postingsDataRepository.saveAll(postingEntities)

    transactionEntity.postings.addAll(postingEntities)

    return transactionEntity
  }
}
