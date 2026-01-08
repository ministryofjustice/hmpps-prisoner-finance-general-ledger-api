package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models

import java.time.LocalDateTime
import java.util.UUID

data class AccountResponse(
  val uuid: UUID,
  val createdBy: String,
  val createdAt: LocalDateTime,
  val reference: String,
)
