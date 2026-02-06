package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "The balance of a sub-account")
data class SubAccountBalanceResponse(
  @field:Schema(description = "The unique ID for the sub-account")
  val subAccountId: UUID,
  @field:Schema(description = "The local date/time when requesting the sub-account balance")
  val balanceDateTime: LocalDateTime,
  @field:Schema(description = "The calculated sum of the latest statement balance and the sub-account balance for postings, made after the latest statement balance date/time.")
  val amount: Long,
)
