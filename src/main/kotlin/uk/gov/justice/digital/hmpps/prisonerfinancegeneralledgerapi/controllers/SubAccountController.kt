package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.SubAccountService
import java.security.Principal
import java.util.UUID

@Tag(name = "Sub Account Controller")
@RestController
class SubAccountController(
  private val subAccountService: SubAccountService,
) {
  @PostMapping(value = ["/accounts/{parentAccountId}/sub-accounts"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun createSubAccount(
    @Valid @RequestBody request: CreateSubAccountRequest,
    @PathVariable parentAccountId: UUID,
    user: Principal,
  ): ResponseEntity<SubAccountResponse> {
    try {
      val createdSubAccount = subAccountService.createSubAccount(reference = request.subAccountReference.uppercase(), parentAccountId = parentAccountId, createdBy = user.name)
      val subAccountResponse = SubAccountResponse(
        id = createdSubAccount.id,
        reference = createdSubAccount.reference,
        parentAccountId = createdSubAccount.parentAccount.id,
        createdBy = createdSubAccount.createdBy,
        createdAt = createdSubAccount.createdAt,
      )
      return ResponseEntity.status(201).body(subAccountResponse)
    } catch (e: DataIntegrityViolationException) {
      throw CustomException(message = "Sub account reference exists in account already", status = HttpStatus.BAD_REQUEST)
    }
  }
}
