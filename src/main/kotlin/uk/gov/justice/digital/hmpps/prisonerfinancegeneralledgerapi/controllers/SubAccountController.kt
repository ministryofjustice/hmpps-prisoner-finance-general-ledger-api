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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateStatementBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.SubAccountService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.security.Principal
import java.util.UUID

@Tag(name = "Sub Account Controller")
@RestController
class SubAccountController(
  private val subAccountService: SubAccountService,
) {
  @Operation(
    summary = "Create a new sub-account",
    description = "Creates a new sub-account using a case insensitive account reference.  Sub-account references must be unique within an account.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Created a new sub-account",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = SubAccountResponse::class),
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
        description = "Resource not found - Parent account not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Conflict - sub-account already exists",
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
  @PostMapping(value = ["/accounts/{parentAccountId}/sub-accounts"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun createSubAccount(
    @Valid @RequestBody request: CreateSubAccountRequest,
    @PathVariable parentAccountId: UUID,
    user: Principal,
  ): ResponseEntity<SubAccountResponse> {
    try {
      val subAccountEntity = subAccountService.createSubAccount(
        reference = request.subAccountReference.uppercase(),
        parentAccountId = parentAccountId,
        createdBy = user.name,
      )
      val subAccountResponse = SubAccountResponse.fromEntity(subAccountEntity)
      return ResponseEntity.status(201).body(subAccountResponse)
    } catch (e: Exception) {
      if (e is DataIntegrityViolationException) {
        throw CustomException(
          message = "Duplicate sub-account reference: ${request.subAccountReference.uppercase()}",
          status = HttpStatus.CONFLICT,
        )
      }
      if (e is JpaObjectRetrievalFailureException) {
        throw CustomException(message = "Parent account not found", status = HttpStatus.NOT_FOUND)
      }
      throw e
    }
  }

  @Operation(
    summary = "Finds a sub-account",
    description = "Finds a sub-account using a case insensitive account reference and sub-account reference. \n " +
      "Account reference and sub-account reference are both required.",
    parameters = [
      Parameter(name = "reference", description = "Sub-account reference", required = true),
      Parameter(name = "accountReference", description = "Account reference", required = true),
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the requested sub-account",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = SubAccountResponse::class)),
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
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @GetMapping("/sub-accounts")
  fun findSubAccounts(@RequestParam reference: String?, @RequestParam accountReference: String?): ResponseEntity<List<SubAccountResponse>> {
    if (reference == null || accountReference == null) {
      throw CustomException(
        message = "Both reference and subAccount reference must be provided",
        status = HttpStatus.BAD_REQUEST,
      )
    }

    val subAccounts = subAccountService.findSubAccounts(accountReference.uppercase(), reference.uppercase())

    return ResponseEntity.ok(subAccounts.map { SubAccountResponse.fromEntity(it) })
  }

  @Operation(
    summary = "Get Sub Account by ID",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the requested sub-account",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = SubAccountResponse::class),
          ),
        ],
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
        description = "Resource not found - Sub account not found",
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
  @GetMapping("/sub-accounts/{subAccountId}")
  fun getSubAccountByID(@PathVariable subAccountId: UUID): ResponseEntity<SubAccountResponse> {
    val subAccount = subAccountService.getSubAccountByID(subAccountId)
    if (subAccount == null) {
      throw CustomException(message = "Sub account not found", status = HttpStatus.NOT_FOUND)
    }
    return ResponseEntity.ok(SubAccountResponse.fromEntity(subAccount))
  }

  @Operation(
    summary = "Get the balance of a sub-account",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Returns the requested sub-account balance",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = SubAccountBalanceResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request - Sub Account ID is not a valid UUID",
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
        description = "Resource not found - Sub account not found",
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
  @GetMapping("/sub-accounts/{subAccountId}/balance")
  fun getSubAccountBalance(@PathVariable subAccountId: UUID): ResponseEntity<SubAccountBalanceResponse> {
    val subAccountBalance = subAccountService.getSubAccountBalance(subAccountId)
    if (subAccountBalance == null) {
      throw CustomException(message = "Sub Account not found", status = HttpStatus.NOT_FOUND)
    }
    return ResponseEntity.ok().body(subAccountBalance)
  }

  @Operation(
    summary = "Create a statement balance",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Returns a created statement balance",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = StatementBalanceResponse::class),
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
        description = "Resource not found - Sub account not found",
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
  @PostMapping("/sub-accounts/{subAccountId}/balance")
  fun postStatementBalance(@PathVariable subAccountId: UUID, @RequestBody statementBalanceRequest: CreateStatementBalanceRequest): ResponseEntity<StatementBalanceResponse> {
    val subAccountStatementBalance = subAccountService.createStatementBalance(subAccountId, statementBalanceRequest.amount, statementBalanceRequest.balanceDateTime)
    if (subAccountStatementBalance == null) {
      throw CustomException(message = "Sub Account not found", status = HttpStatus.NOT_FOUND)
    }
    return ResponseEntity.status(201).body(StatementBalanceResponse.fromEntity(subAccountStatementBalance))
  }
}
