package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import java.security.Principal

class AccountController(
  private val accountService: AccountService,
) {
  fun createAccount(accountReference: String, user: Principal): ResponseEntity<Account> {
    val account = accountService.createAccount(accountReference, createdBy = user.name)

    return ResponseEntity.ok().body<Account>(account)
  }
}
