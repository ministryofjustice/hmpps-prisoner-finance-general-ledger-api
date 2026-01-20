package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.UUID

class PostingResponse(
  val id: UUID,
  val createdBy: String,
  val createdAt: LocalDateTime,
  val type: PostingType,
  val amount: Long,
  val subAccountID: UUID,
) {
  companion object {
    fun fromEntity(postingEntity: PostingEntity): PostingResponse = PostingResponse(postingEntity.id, postingEntity.createdBy, postingEntity.createdAt, postingEntity.type, postingEntity.amount, postingEntity.subAccountEntity.id)
  }
}
