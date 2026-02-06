package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.PositiveOrZero
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.util.UUID

@Schema(description = "A posting within a CreateTransactionRequest")
data class CreatePostingRequest(
  @field:Schema(description = "The ID of the sub-account associated with the posting")
  val subAccountId: UUID,

  @field:Schema(description = "Enumeration of PostingType either CR or DR")
  val type: PostingType,

  @field:Schema(description = "The non-negative posting amount")
  @field:PositiveOrZero(message = "posting amount must be positive")
  val amount: Long,
)
