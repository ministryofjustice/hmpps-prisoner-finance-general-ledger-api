package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingBalanceService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.StatementService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.util.UUID

@Tag(name = "Statement Controller")
@RestController
class MigrateController(
  private val postingBalanceService: PostingBalanceService,
) {

  @Operation(
    summary = "Migrate historical subAccount balances",
    description = "Migrates historical subAccount balances",
  )
  @SecurityRequirement(
    name = "bearer-jwt",
    scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW],
  )
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @GetMapping(value = ["/migrate/subAccountBalances"], produces = ["application/json"])
  fun migrateSubAccountBalances(
  ): ResponseStatus {
    try {
      postingBalanceService.migrateAllPostingBalances()
      return ResponseStatus(HttpStatus.ACCEPTED, HttpStatus.ACCEPTED, "ok")
    }
    catch (e: Exception) {
      throw CustomException(status = HttpStatus.INTERNAL_SERVER_ERROR, message = "${e.message}\n\n${e.cause}\n\n${e.stackTrace}")
    }
  }
}
