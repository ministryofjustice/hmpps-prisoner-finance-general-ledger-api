package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import kotlin.math.exp

class AccountsControllerTest {

  @Test
  fun `Can create an account`() {
    val accountsReference = "TEST-ACCOUNT-REFERENCE"
    val response: ResponseEntity<Account> = AccountsController().createAccount(accountsReference)
    assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(response.body?.reference).isEqualTo(accountsReference)
  }
}