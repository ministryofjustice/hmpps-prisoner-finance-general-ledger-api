package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.PayloadDataClass
import java.time.Instant
import java.util.UUID

data class ProcessBalanceRequest(
  val postingId: UUID,
  val accountId: UUID,
  val requestTime: Instant,
) : PayloadDataClass {
  companion object {
    fun fromPostingEntity(posting: PostingEntity, requestTime: Instant): ProcessBalanceRequest = ProcessBalanceRequest(
      postingId = posting.id,
      accountId = posting.subAccountEntity.parentAccountEntity.id,
      requestTime = requestTime,
    )
  }
}
