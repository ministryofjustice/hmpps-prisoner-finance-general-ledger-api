package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.dto

import java.time.LocalDateTime
import java.util.UUID

data class AccountDTO(
  val id: UUID,
  val reference: String,
  val createdAt: LocalDateTime,
  val createdBy: String,
)
