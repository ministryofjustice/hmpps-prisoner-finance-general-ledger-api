package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.UUID

data class SubAccountResponse(
  @field:NotBlank
  val id: UUID,
  @field:NotBlank
  val reference: String,
  @field:NotBlank
  val parentAccountId: UUID,
  @field:NotBlank
  val createdBy: String,
  @field:NotBlank
  val createdAt: LocalDateTime,
)
