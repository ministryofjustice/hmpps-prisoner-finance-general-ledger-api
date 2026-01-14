package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models

import java.time.LocalDateTime
import java.util.UUID

data class SubAccountResponse(
  val id: UUID,
  val reference: String,
  val parentAccountId: UUID,
  val createdBy: String,
  val createdAt: LocalDateTime,
)
