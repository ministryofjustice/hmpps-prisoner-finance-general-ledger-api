package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.PayloadDataClass
import java.util.UUID

data class ProcessBalanceRequest(
  val postingId: UUID,
) : PayloadDataClass {
  companion object {
    fun fromPostingEntity(posting: PostingEntity): ProcessBalanceRequest = ProcessBalanceRequest(
      postingId = posting.id,
    )
  }
}
