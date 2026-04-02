package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses

class PagedResponse<T>(
  val content: List<T>,
  val pageNumber: Int,
  val pageSize: Int,
  val totalElements: Long,
  val totalPages: Int,
  val isLastPage: Boolean,
)
