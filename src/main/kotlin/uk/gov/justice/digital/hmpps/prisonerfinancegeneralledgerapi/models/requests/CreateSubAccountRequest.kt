package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "A request to create a sub-account within an account")
data class CreateSubAccountRequest(
  @field:Schema(description = "A reference to identify the sub-account, this must be unique within the account")
  val subAccountReference: String,
)
