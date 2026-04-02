package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils.toPageResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils.toUtcEndOfDay
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils.toUtcStartOfDay
import java.time.LocalDate
import java.util.UUID

@Service
class StatementService(
  private val postingsDataRepository: PostingsDataRepository,
  private val accountService: AccountService,
) {
  fun listStatementEntries(accountId: UUID, startDate: LocalDate? = null, endDate: LocalDate? = null, pageNumber: Int = 1, pageSize: Int = 25): PagedResponse<StatementEntryResponse>? {
    accountService.readAccount(accountId) ?: return null

    val zeroIndexedPage = pageNumber - 1

    val pageReq = PageRequest.of(
      zeroIndexedPage,
      pageSize,
      Sort.Direction.DESC,
      "transactionEntity.timestamp",
    )

    val page = postingsDataRepository.getPostingsByAccountId(
      accountId,
      page = pageReq,
      startDate = startDate?.toUtcStartOfDay(),
      endDate = endDate?.toUtcEndOfDay(),
    )

    return page.toPageResponse { content -> content.map { StatementEntryResponse.fromEntity(it) } }
  }
}
