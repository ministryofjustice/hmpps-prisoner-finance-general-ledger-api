package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
import java.time.LocalDate
import java.util.UUID

@Service
class StatementService(
  private val postingsDataRepository: PostingsDataRepository,
  private val accountService: AccountService,
) {
  fun listStatementEntries(accountId: UUID, startDate: LocalDate? = null): List<StatementEntryResponse>? {
    accountService.readAccount(accountId) ?: return null

    return postingsDataRepository.getPostingsByAccountId(accountId).map {
      StatementEntryResponse.fromEntity(it)
    }
  }
}
