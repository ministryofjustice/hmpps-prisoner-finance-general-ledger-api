package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import java.time.LocalDateTime
import java.util.UUID
class AccountResponse(
  val id: UUID,
  val reference: String,
  val createdBy: String,
  val createdAt: LocalDateTime,
) {

  companion object {
    fun fromEntity(accountEntity: AccountEntity): AccountResponse = AccountResponse(accountEntity.id, accountEntity.reference, accountEntity.createdBy, accountEntity.createdAt)
  }
}
