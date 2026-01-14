package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models

import java.util.UUID

data class AccountResponse(
  val id: UUID,
  val reference: String,
  val createdBy: String,
  val createdAt: String,
  val subAccounts: List<SubAccountResponse>?,
)
