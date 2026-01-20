package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.math.BigInteger
import java.util.UUID

data class CreatePostingRequest(
  val subAccountId: UUID,
  val type: PostingType,
  val amount: BigInteger,
)
