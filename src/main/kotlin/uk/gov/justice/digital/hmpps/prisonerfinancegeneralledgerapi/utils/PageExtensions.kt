package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils

import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PagedResponse
import kotlin.math.max

fun <T : Any, R> Page<T>.toPageResponse(mapper: (List<T>) -> List<R>): PagedResponse<R> {
  val nonZeroPageNumber = this.number + 1
  val nonZeroTotalPages = max(1, this.totalPages)
  val pageRequestedIsOutOfRange = nonZeroPageNumber > nonZeroTotalPages

  if (pageRequestedIsOutOfRange) {
    throw CustomException(message = "Page requested is out of range", status = HttpStatus.BAD_REQUEST)
  }

  return PagedResponse(
    content = mapper.invoke(this.content),
    pageNumber = this.number + 1,
    pageSize = this.size,
    totalElements = this.totalElements,
    totalPages = this.totalPages,
    isLastPage = this.isLast,
  )
}
