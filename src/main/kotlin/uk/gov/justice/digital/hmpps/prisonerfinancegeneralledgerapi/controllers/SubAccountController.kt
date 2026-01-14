package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccount
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateSubAccountRequest
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
  ): ResponseEntity<SubAccount> {
    val createdSubAccount = subAccountService.createSubAccount(reference = request.subAccountReference, parentAccountId = parentAccountId, createdBy = user.name)
    return ResponseEntity.status(201).body(createdSubAccount)
  }
}
