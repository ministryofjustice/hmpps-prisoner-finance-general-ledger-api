package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils.toUtcEndOfDay
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils.toUtcStartOfDay
import java.time.LocalDate
import java.util.UUID

@Service
class StatementService(
  private val postingsDataRepository: PostingsDataRepository,
  private val accountService: AccountService,
) {
  fun listStatementEntries(accountId: UUID, startDate: LocalDate? = null, endDate: LocalDate? = null): List<StatementEntryResponse>? {
    accountService.readAccount(accountId) ?: return null

    return postingsDataRepository.getPostingsByAccountId(accountId, startDate = startDate?.toUtcStartOfDay(), endDate = endDate?.toUtcEndOfDay()).map {
      StatementEntryResponse.fromEntity(it)
    }
  }
}
