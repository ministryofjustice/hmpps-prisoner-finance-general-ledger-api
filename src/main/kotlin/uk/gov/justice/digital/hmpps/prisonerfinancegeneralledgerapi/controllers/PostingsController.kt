package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PrisonerPostingListResponse
import java.util.UUID

@Tag(name = "Postings Controller")
@RestController
class PostingsController {

  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO, ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO','$ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW')")
  @GetMapping(value = ["/accounts/{accountId}/postings"])
  fun getPostingsFromAccountId(@PathVariable accountId: UUID): ResponseEntity<List<PrisonerPostingListResponse>> = ResponseEntity.ok().body(emptyList<PrisonerPostingListResponse>())
}
