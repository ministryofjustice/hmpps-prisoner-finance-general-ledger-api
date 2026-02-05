package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.ValidTransactionRequest
import java.time.LocalDateTime

@Schema(description = "A request to create a transaction within the general ledger")
@ValidTransactionRequest
data class CreateTransactionRequest(
  @field:Schema(description = "A reference for the transaction")
  val reference: String,

  @field:Schema(description = "A description for the transaction")
  val description: String,

  @field:Schema(description = "The local date/time of when the transaction is created")
  val timestamp: LocalDateTime,

  @field:Schema(description = "The non-negative amount in pence of the transaction, this is the total of all money moved with postings in this transaction")
  @field:PositiveOrZero(message = "transaction amount must be positive")
  val amount: Long,

  @field:Schema(
    description = "A collection of postings for the transaction, there must be at least 1 Credit (CR) and 1 Debit (DR) posting supplied.\n" +
      "\n" +
      "We accept one to one postings (1DR to 1CR).\n" +
      "\n" +
      "We accept one to many postings (1DR to many CR or 1CR to many DR).\n" +
      "\n" +
      "We do not accept many to many postings (2 DR to 2CR).\n" +
      "\n" +
      "The sum of all credit in the transaction must equal the sum of all debits in the transaction.\n",
  )
  @field:Valid
  @field:Size(min = 2, message = "transaction must have two postings or more")
  val postings: List<CreatePostingRequest>,
)
