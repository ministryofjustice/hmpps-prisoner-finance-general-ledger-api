package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators.referenceStringValidator.ValidReferenceString

@Schema(description = "A request to create an account")
data class CreateAccountRequest(
  @ValidReferenceString
  @field:Schema(description = "A unique reference to identify an account, example LEI or 123456")
  val accountReference: String,

  @field:Schema(description = "A account type to signify if the account is a PRISONER or PRISON", example = "PRISONER", required = true)
  val type: AccountType,
)
