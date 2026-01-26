package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models

import java.time.LocalDateTime
import java.util.UUID

class SubAccountBalanceResponse(
  val subAccountId: UUID,
  val balanceDateTime: LocalDateTime,
  val amount: Long,
)
