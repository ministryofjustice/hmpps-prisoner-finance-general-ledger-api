package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.StatementService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.util.UUID

@Tag(name = "Statement Controller")
@RestController
class StatementController(
  private val statementService: StatementService,
) {
  @Operation(
    summary = "Return Statement Entries list for the account",
    description = "Returns all statement entries (postings) for the account.",
    parameters = [
      Parameter(name = "startDate", description = "Filter statements from start date (inclusive) in a yyyy-MM-dd format", required = false, example = "2025-12-24"),
      Parameter(name = "endDate", description = "Filter statements to end date (inclusive) in a yyyy-MM-dd format", required = false, example = "2025-12-25"),
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Return Statement Entries list for the account",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(StatementEntryResponse::class)),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized - requires a valid OAuth2 token",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires an appropriate role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Not Found - Account not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(
    name = "bearer-jwt",
    scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO, ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW],
  )
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO','$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @GetMapping(value = ["/accounts/{accountId}/statement"])
  fun getStatementForAccountId(
    @PathVariable accountId: UUID,
    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") startDate: LocalDate? = null,
    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") endDate: LocalDate? = null,
    @RequestParam @Min(1) pageNumber: Int = 1,
    @RequestParam @Min(1) pageSize: Int = 25,
  ): ResponseEntity<PagedResponse<StatementEntryResponse>> {
    val pagedStatementEntryResponses = statementService.listStatementEntries(accountId, startDate, endDate, pageNumber, pageSize)

    if (pagedStatementEntryResponses == null) {
      throw CustomException(message = "Account not found", status = HttpStatus.NOT_FOUND)
    }

    return ResponseEntity<PagedResponse<StatementEntryResponse>>.status(200)
      .body(pagedStatementEntryResponses)
  }
}
