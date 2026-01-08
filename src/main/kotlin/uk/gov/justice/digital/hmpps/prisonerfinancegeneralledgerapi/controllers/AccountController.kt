package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.security.Principal

@Tag(name = "Account Controller")
@RestController
class AccountController(
  private val accountService: AccountService,
) {
  @Operation(summary = "Create a new account")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Created a new account",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = Account::class))],
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
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = ["SCOPE_write"])
  @PreAuthorize("hasAnyAuthority('SCOPE_write')")
  @PostMapping(value = ["/account"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun createAccount(@Valid @RequestBody body: CreateAccountRequest, user: Principal): ResponseEntity<Account> {
    val account = accountService.createAccount(body.accountReference, createdBy = user.name)

    return ResponseEntity.status(201).body(account)
  }

  @GetMapping("/account/{accountReference}")
  fun getAccount(@PathVariable accountReference: String): ResponseEntity<Account> {
    val account = accountService.readAccount(accountReference)
    return ResponseEntity<Account>.status(HttpStatus.OK).body(account)
  }
}
