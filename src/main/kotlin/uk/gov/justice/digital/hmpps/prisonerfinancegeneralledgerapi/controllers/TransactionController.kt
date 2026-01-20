package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.jpa.domain.AbstractAuditable_.createdBy
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.TransactionService
import java.security.Principal

@Tag(name = "Transaction Controller")
@RestController
class TransactionController(
  private val transactionService: TransactionService,
) {
  @Operation(
    summary = "Create a new transaction",
    description = "Creates a new transaction with associated postings. ",
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
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @PostMapping(value = ["/transactions"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun createTransaction(
    @Valid @RequestBody body: CreateTransactionRequest,
    user: Principal,
  ): ResponseEntity<TransactionResponse> {
    try {
      val transactionEntity = transactionService.createTransaction(body, createdBy = user.name)
      return ResponseEntity<TransactionResponse>.status(HttpStatus.CREATED).body(
        TransactionResponse.fromEntity(transactionEntity = transactionEntity),
      )
    } catch (ex: Exception) {
      throw ex
    }
  }
}
