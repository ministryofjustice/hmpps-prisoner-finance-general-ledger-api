package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import java.time.Instant
import java.util.UUID

@Schema(description = "A transaction within the general ledger")
class TransactionResponse(
  @field:Schema(description = "A unique ID for the transaction")
  val id: UUID,
  @field:Schema(description = "The user who created the transaction")
  val createdBy: String,
  @field:Schema(description = "The local date/time of when the transaction record was created in the ledger")
  val createdAt: Instant,
  @field:Schema(description = "A reference for the transaction")
  val reference: String,
  @field:Schema(description = "A description for the transaction")
  val description: String,
  @field:Schema(description = "The local date/time of when the transaction is created")
  val timestamp: Instant,
  @field:Schema(description = "The total transaction monetary amount in pence")
  val amount: Long,
  @field:Schema(description = "A collection of postings for the transaction")
  val postings: List<PostingResponse> = emptyList(),
) {
  companion object {
    fun fromEntity(transactionEntity: TransactionEntity): TransactionResponse = TransactionResponse(transactionEntity.id, transactionEntity.createdBy, transactionEntity.createdAt, transactionEntity.reference, transactionEntity.description, transactionEntity.timestamp, transactionEntity.amount, postings = transactionEntity.postings.map { PostingResponse.fromEntity(it) })
  }
}
