package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import java.time.LocalDateTime
import java.util.UUID

class TransactionResponse(
  val id: UUID,
  val createdBy: String,
  val createdAt: LocalDateTime,
  val reference: String,
  val description: String,
  val timestamp: LocalDateTime,
  val amount: Long,
  val postings: List<PostingResponse> = emptyList(),
) {
  companion object {
    fun fromEntity(transactionEntity: TransactionEntity): TransactionResponse = TransactionResponse(transactionEntity.id, transactionEntity.createdBy, transactionEntity.createdAt, transactionEntity.reference, transactionEntity.description, transactionEntity.timestamp, transactionEntity.amount, postings = transactionEntity.postings.map { PostingResponse.fromEntity(it) })
  }
}
