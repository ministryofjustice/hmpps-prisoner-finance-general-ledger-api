package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.data.domain.PageRequest
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
  fun listStatementEntries(
    accountId: UUID,
    subAccountId: UUID? = null,
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    pageNumber: Int = 1,
    pageSize: Int = 25,
    credit: Boolean = false,
    debit: Boolean = false,
  ): PagedResponse<StatementEntryResponse>? {
    accountService.readAccount(accountId) ?: return null

    val zeroIndexedPage = pageNumber - 1

    // Ordering is baked into the repository query, so the page request only carries offset/limit.
    val pageReq = PageRequest.of(zeroIndexedPage, pageSize)

    val page = postingsDataRepository.getPostingsByAccountId(
      accountId,
      page = pageReq,
      subAccountId = subAccountId,
      startDate = startDate?.toUtcStartOfDay(),
      endDate = endDate?.toUtcEndOfDay(),
      credit = credit,
      debit = debit,
    )

    // Second query: fetch every posting on this page's transactions, grouped by transaction,
    // so each entry's opposite postings can be stitched in without touching lazy associations.
    val transactionIds = page.content.map { it.transactionId }.distinct()
    val oppositePostingsByTransaction = if (transactionIds.isEmpty()) {
      emptyMap()
    } else {
      postingsDataRepository.getOppositePostingsByTransactionIds(transactionIds).groupBy { it.transactionId }
    }

    return page.toPageResponse { content ->
      content.map { entry ->
        val oppositePostings = oppositePostingsByTransaction[entry.transactionId]
          .orEmpty()
          .filter { it.type != entry.postingType }
        StatementEntryResponse.fromProjection(entry, oppositePostings)
      }
    }
  }
}
