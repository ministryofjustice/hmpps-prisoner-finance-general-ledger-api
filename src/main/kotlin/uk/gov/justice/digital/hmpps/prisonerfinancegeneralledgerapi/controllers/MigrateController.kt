package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.MigrateService

@Tag(name = "Migration Controller")
@RestController
class MigrateController(
  private val migrateService: MigrateService,
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
  @PostMapping(value = ["/migrate/subAccountBalances"], produces = ["application/json"])
  fun migrateSubAccountBalances(): ResponseStatus {
    migrateService.migrateAllPostingBalances()
    return ResponseStatus(HttpStatus.ACCEPTED, HttpStatus.ACCEPTED, "ok")
  }
}
