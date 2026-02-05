package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "An account balance")
data class AccountBalanceResponse(
  @field:Schema(description = "The ID for the account")
  val accountId: UUID,
  @field:Schema(description = "The local date/time of the account balance")
  val balanceDateTime: LocalDateTime,
  @field:Schema(description = "The balance of an account in pence")
  val amount: Long,
)
