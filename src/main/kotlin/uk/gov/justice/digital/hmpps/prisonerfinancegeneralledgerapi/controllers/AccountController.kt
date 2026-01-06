package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import java.security.Principal

@RestController
class AccountController(
  private val accountService: AccountService,
) {

  @PostMapping(value = ["/account"], consumes = [MediaType.APPLICATION_JSON_VALUE])
  fun createAccount(@RequestBody body: CreateAccountRequest, user: Principal): ResponseEntity<Account> {
    println(body.accountReference)
    val account = accountService.createAccount(body.accountReference, createdBy = user.name)

    return ResponseEntity.ok().body<Account>(account)
  }
}
