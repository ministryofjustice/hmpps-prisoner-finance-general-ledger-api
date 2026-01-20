package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests
import java.math.BigInteger
import java.time.LocalDateTime

data class CreateTransactionRequest(
  val reference: String,
  val description: String,
  val timestamp: LocalDateTime,
  val amount: Long,
  val postings: List<CreatePostingRequest>,
)
