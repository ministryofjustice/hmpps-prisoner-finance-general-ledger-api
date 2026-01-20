package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.UUID

class TransactionResponse (
  val id: UUID,
  val createdBy: String,
  val createdAt: LocalDateTime,
  val reference : String,
  val description : String,
  val timestamp: LocalDateTime,
  val amount: BigInteger,
  val postings : List<PostingResponse> = emptyList()
  )