package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService

@ExtendWith(MockitoExtension::class)
class AccountControllerTest {
  @Mock
  lateinit var accountServiceMock: AccountService

  @InjectMocks
  lateinit var accountsController: AccountController

  @Nested
  inner class CreateAccounts {
    @Test
    fun `createAccount should return 200`() {
      val accountsReference = "TEST-ACCOUNT-REFERENCE"
      val response: ResponseEntity<Account> = AccountController().createAccount(accountsReference)
      assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
      assertThat(response.body?.reference).isEqualTo(accountsReference)
    }

    @Test
    fun `createAccount should call the service correctly`() {
    }
  }
}
