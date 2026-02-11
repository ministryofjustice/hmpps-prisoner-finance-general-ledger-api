package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import java.time.Instant
import java.util.UUID

@Schema(description = "The statement balance for a sub-account")
data class StatementBalanceResponse(
  @field:Schema(description = "The created balance in pence")
  val amount: Long,
  @field:Schema(description = "The unique ID for the sub-account")
  val subAccountId: UUID,
  @field:Schema(description = "A local date/time of when the statement balance was created")
  val balanceDateTime: Instant,
) {
  companion object {
    fun fromEntity(entity: StatementBalanceEntity): StatementBalanceResponse = StatementBalanceResponse(amount = entity.amount, subAccountId = entity.subAccountEntity.id, balanceDateTime = entity.balanceDateTime)
  }
}
