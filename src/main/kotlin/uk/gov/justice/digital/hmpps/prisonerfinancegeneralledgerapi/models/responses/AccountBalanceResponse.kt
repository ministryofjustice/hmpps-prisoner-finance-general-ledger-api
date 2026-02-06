package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "An account balance")
data class AccountBalanceResponse(
  @field:Schema(description = "The ID for the account")
  val accountId: UUID,
  @field:Schema(description = "The local date/time that the account balance was calculated")
  val balanceDateTime: LocalDateTime,
  @field:Schema(description = "The balance of all sub-accounts within the account in pence")
  val amount: Long,
)
