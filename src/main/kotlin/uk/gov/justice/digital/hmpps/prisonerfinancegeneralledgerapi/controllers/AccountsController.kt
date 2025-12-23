package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account

class AccountsController {
  fun createAccount(accountReference: String): ResponseEntity<Account> {
    return ResponseEntity.ok().body<Account>(Account(reference = accountReference, createdBy = "AccountController"))
  }
}
