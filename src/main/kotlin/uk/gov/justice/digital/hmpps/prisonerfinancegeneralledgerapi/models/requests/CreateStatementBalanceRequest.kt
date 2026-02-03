package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import java.time.LocalDateTime

data class CreateStatementBalanceRequest(
  val amount: Long,
  val balanceDateTime: LocalDateTime,
)
