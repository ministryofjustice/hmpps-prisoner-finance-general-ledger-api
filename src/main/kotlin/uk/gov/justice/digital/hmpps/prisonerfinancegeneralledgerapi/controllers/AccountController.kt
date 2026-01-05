package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account

class AccountController {
  fun createAccount(accountReference: String): ResponseEntity<Account> = ResponseEntity.ok().body<Account>(Account(reference = accountReference, createdBy = "AccountController"))
}
