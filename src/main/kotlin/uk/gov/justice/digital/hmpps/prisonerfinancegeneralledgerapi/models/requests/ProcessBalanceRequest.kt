package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.PayloadDataClass
import java.util.UUID

data class ProcessBalanceRequest(
  val postingId: UUID,
  val accountId: UUID,
  val source: String,
  val chainPosition: Long,
) : PayloadDataClass {
  companion object {
    fun fromPostingEntity(posting: PostingEntity, source: String, chainPosition: Long): ProcessBalanceRequest = ProcessBalanceRequest(
      postingId = posting.id,
      accountId = posting.subAccountEntity.parentAccountEntity.id,
      source,
      chainPosition,
    )
  }
}
