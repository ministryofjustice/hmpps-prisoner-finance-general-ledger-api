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
import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.security.Principal
import java.util.UUID

@Tag(name = "Account Controller")
@RestController
class AccountController(
  private val accountService: AccountService,
) {
  @Operation(
    summary = "Create a new account",
    description = "Creates a new account using a case insensitive account reference",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Created a new account",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AccountResponse::class))],
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
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @PostMapping(value = ["/accounts"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun createAccount(@Valid @RequestBody body: CreateAccountRequest, user: Principal): ResponseEntity<AccountResponse> {
    try {
      val accountEntity = accountService.createAccount(body.accountReference.uppercase(), createdBy = user.name)
      return ResponseEntity<AccountResponse>.status(HttpStatus.CREATED).body(
        AccountResponse.fromEntity(accountEntity = accountEntity),
      )
    } catch (e: Exception) {
      if (e is DataIntegrityViolationException) {
        throw CustomException(status = BAD_REQUEST, message = "Duplicate account reference: ${body.accountReference}")
      }
      throw e
    }
  }

  @Operation(summary = "Get an account by UUID")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the account",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AccountResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request - Parameter is not a valid UUID",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request - Parameter is not a valid UUID",
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
        description = "Resource not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @GetMapping("/accounts/{accountUUID}")
  fun getAccount(@PathVariable accountUUID: UUID): ResponseEntity<AccountResponse> {
    val accountEntity = accountService.readAccount(accountUUID)

    if (accountEntity == null) {
      throw CustomException(status = HttpStatus.NOT_FOUND, message = "Account not found")
    }

    return ResponseEntity<AccountResponse>.status(HttpStatus.OK).body(
      AccountResponse.fromEntity(accountEntity = accountEntity),
    )
  }

  @Operation(
    summary = "Find accounts using query parameters",
    description = "At least one query parameter is required for this endpoint.",
    parameters = [Parameter(name = "reference", description = "Account reference", required = true)],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the account",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(AccountResponse::class)))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request - Invalid query parameters",
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
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @GetMapping("/accounts")
  fun getAccount(@RequestParam reference: String?): ResponseEntity<List<AccountResponse>> {
//    If no params are provided, return 400. In future updates, this needs to account for all params (&&).
    if (reference.isNullOrEmpty()) {
      throw CustomException(
        status = BAD_REQUEST,
        message = "Query parameters must be provided",
      )
    }

    val retrievedAccounts = accountService.findAccounts(reference.uppercase())

    val listOfAccountResponses = retrievedAccounts.map { AccountResponse.fromEntity(it) }
    return ResponseEntity<List<AccountResponse>>.status(HttpStatus.OK).body(listOfAccountResponses)
  }
}
