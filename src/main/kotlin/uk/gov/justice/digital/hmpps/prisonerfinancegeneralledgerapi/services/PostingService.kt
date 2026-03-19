package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PrisonerPostingListResponse
import java.util.UUID

@Service
class PostingService(
  private val postingsDataRepository: PostingsDataRepository,
  ) {
  fun listPostingsForPrisoner(accountId: UUID): List<PrisonerPostingListResponse> {
    return postingsDataRepository.getPostingsByAccountId(accountId).map{
      PrisonerPostingListResponse.fromEntity(it)
    }
  }
}
