package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
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
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.TransactionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.security.Principal
import java.util.UUID

@Tag(name = "Transaction Controller")
@RestController
class TransactionController(
  private val transactionService: TransactionService,
) {
  @Operation(
    summary = "Create a new transaction",
    description = "Creates a new transaction with associated postings.</br>\n" +
      "<table border=\"1\" cellpadding=\"5\" cellspacing=\"0\"> <thead> <tr> <th>Postings</th> <th>Expected Response Time (ms)</th> </tr> </thead> <tbody> <tr> <td>2</td> <td>37</td> </tr> <tr> <td>1,000</td> <td>2,443</td> </tr> <tr> <td>2,000</td> <td>2,966</td> </tr> <tr> <td>3,000</td> <td>4,693</td> </tr> <tr> <td>4,000</td> <td>5,676</td> </tr> <tr> <td>8,000</td> <td>8,840</td> </tr> <tr> <td>16,000</td> <td>16,183</td> </tr> <tr> <td>32,000</td> <td>33,696</td> </tr> </tbody> </table>",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Created a new transaction and its postings",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = TransactionResponse::class),
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
        description = "Unauthorized",
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
  @PostMapping(value = ["/transactions"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun postTransaction(
    @Valid @RequestBody body: CreateTransactionRequest,
    @Parameter(
      name = "Idempotency-Key",
      `in` = ParameterIn.HEADER,
      required = true,
      description = "An Idempotency Key to ensure that transactions are not repeated",
    )
    @RequestHeader(
      "Idempotency-Key",
      required = true,
    )
    idempotencyKey: UUID,
    user: Principal,
  ): ResponseEntity<TransactionResponse> {
    try {
      val transactionEntity = transactionService.createTransaction(body, createdBy = user.name, idempotencyKey = idempotencyKey)
      return ResponseEntity<TransactionResponse>.status(HttpStatus.CREATED).body(
        TransactionResponse.fromEntity(transactionEntity = transactionEntity),
      )
    } catch (ex: Exception) {
      if (ex is DataIntegrityViolationException) {
        throw CustomException(status = BAD_REQUEST, message = "Duplicate transaction reference: ${body.reference}")
      }
      if (ex is JpaObjectRetrievalFailureException) {
        throw CustomException(status = BAD_REQUEST, message = "Sub-account not found")
      }
      throw ex
    }
  }

  @Operation(summary = "Get an transaction by UUID")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the transaction",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = TransactionResponse::class))],
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
  @GetMapping(value = ["/transactions/{transactionUUID}"])
  fun getTransactionById(@PathVariable transactionUUID: UUID): ResponseEntity<TransactionResponse> {
    val transactionEntity = transactionService.readTransaction(transactionUUID)

    if (transactionEntity == null) {
      throw CustomException(message = "Parent account not found", status = HttpStatus.NOT_FOUND)
    }

    return ResponseEntity<TransactionResponse>.status(HttpStatus.OK).body(
      TransactionResponse.fromEntity(transactionEntity = transactionEntity),
    )
  }
}
