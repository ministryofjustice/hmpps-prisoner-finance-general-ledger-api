package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import jakarta.validation.constraints.PositiveOrZero
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.util.UUID

data class CreatePostingRequest(
  val subAccountId: UUID,
  val type: PostingType,
  @field:PositiveOrZero(message = "posting amount must be positive")
  val amount: Long,
)
