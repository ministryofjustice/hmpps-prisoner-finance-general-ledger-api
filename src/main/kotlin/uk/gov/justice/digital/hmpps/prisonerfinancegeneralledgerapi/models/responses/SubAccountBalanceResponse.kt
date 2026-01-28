package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import java.time.LocalDateTime
import java.util.UUID

data class SubAccountBalanceResponse(
  val subAccountId: UUID,
  val balanceDateTime: LocalDateTime,
  val amount: Long,
)
