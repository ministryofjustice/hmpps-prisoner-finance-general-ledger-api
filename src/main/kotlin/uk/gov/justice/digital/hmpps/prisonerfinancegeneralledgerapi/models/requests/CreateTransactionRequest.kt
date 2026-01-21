package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.ValidTransactionRequest
import java.time.LocalDateTime

@ValidTransactionRequest
data class CreateTransactionRequest(
  val reference: String,
  val description: String,
  val timestamp: LocalDateTime,
  val amount: Long,
  val postings: List<CreatePostingRequest>,
)
