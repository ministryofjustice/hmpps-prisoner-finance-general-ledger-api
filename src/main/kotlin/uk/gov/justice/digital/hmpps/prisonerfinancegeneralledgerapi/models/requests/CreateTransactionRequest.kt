package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests
import jakarta.validation.Valid
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.ValidTransactionRequest
import java.time.LocalDateTime

@ValidTransactionRequest
data class CreateTransactionRequest(
  val reference: String,
  val description: String,
  val timestamp: LocalDateTime,
  @field:PositiveOrZero(message = "transaction amount must be positive")
  val amount: Long,
  @field:Valid
  @field:Size(min = 2, message = "transaction must have two postings or more")
  val postings: List<CreatePostingRequest>,
)
