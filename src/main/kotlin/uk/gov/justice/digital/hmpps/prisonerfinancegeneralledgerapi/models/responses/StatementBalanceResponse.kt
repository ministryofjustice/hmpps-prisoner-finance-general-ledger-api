package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import java.time.LocalDateTime
import java.util.UUID

data class StatementBalanceResponse(
  val amount: Long,
  val subAccountId: UUID,
  val balanceDateTime: LocalDateTime,
) {
  companion object {
    fun fromEntity(entity: StatementBalanceEntity): StatementBalanceResponse = StatementBalanceResponse(amount = entity.amount, subAccountId = entity.subAccountEntity.id, balanceDateTime = entity.balanceDateTime)
  }
}
