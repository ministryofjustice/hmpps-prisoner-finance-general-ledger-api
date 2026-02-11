package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "A request to create a statement balance for a sub-account")
data class CreateStatementBalanceRequest(
  @field:Schema(description = "The balance in pence")
  val amount: Long,
  @field:Schema(description = "The local date/time of a statement balance as defined by SYNC (Note: This will override any existing balance prior to this date/time)")
  val balanceDateTime: Instant,
)
