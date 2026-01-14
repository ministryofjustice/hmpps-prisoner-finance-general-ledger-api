package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccount
import java.time.LocalDateTime
import java.util.UUID

data class AccountResponse(
  val id: UUID,
  val reference: String,
  val createdBy: String,
  val createdAt: LocalDateTime,
  @JsonIgnoreProperties("parentAccount")
  val subAccounts: MutableList<SubAccount>,
)
