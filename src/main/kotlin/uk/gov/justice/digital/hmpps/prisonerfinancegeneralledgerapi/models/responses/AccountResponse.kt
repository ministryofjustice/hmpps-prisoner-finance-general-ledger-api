package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import java.time.Instant
import java.util.UUID

@Schema(description = "An account within the general ledger")
class AccountResponse(
  @field:Schema(description = "The unique ID for the account")
  val id: UUID,
  @field:Schema(description = "The unique reference for the account")
  val reference: String,
  @field:Schema(description = "The principal users name when the account was created")
  val createdBy: String,
  @field:Schema(description = "The date/time of when the account was created in UTC/Instant format")
  val createdAt: Instant,
  @field:Schema(description = "The collection of sub-accounts within the account")
  val subAccounts: List<SubAccountResponse> = emptyList(),
) {
  companion object {
    fun fromEntity(accountEntity: AccountEntity): AccountResponse = AccountResponse(
      accountEntity.id,
      accountEntity.reference,
      accountEntity.createdBy,
      accountEntity.createdAt,
      accountEntity.subAccounts.map { SubAccountResponse.fromEntity(it) },
    )
  }
}
