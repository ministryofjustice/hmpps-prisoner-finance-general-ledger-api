package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import java.security.Principal
import java.time.LocalDateTime
import java.util.UUID

private const val TEST_ACCOUNT_REF = "TEST_ACCOUNT_REF"
private const val TEST_USERNAME = "TEST_USERNAME"

@ExtendWith(MockitoExtension::class)
class AccountControllerTest {
  @Mock
  lateinit var accountServiceMock: AccountService

  @InjectMocks
  lateinit var accountController: AccountController

  lateinit var dummyAccount: Account

  @BeforeEach
  fun setupDummyAccount() {
    val dummyUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
    dummyAccount = Account(reference = TEST_ACCOUNT_REF, createdBy = TEST_USERNAME, id = dummyUUID, createdAt = dummyDate)
  }

  @Nested
  inner class CreateAccounts {

    @Test
    fun `createAccount should call the service correctly`() {
      whenever(accountServiceMock.createAccount(TEST_ACCOUNT_REF, TEST_USERNAME)).thenReturn(dummyAccount)
      val mockPrincipal = Mockito.mock<Principal>()
      whenever(mockPrincipal.name).thenReturn(TEST_USERNAME)
      val body = CreateAccountRequest(TEST_ACCOUNT_REF)
      val response: ResponseEntity<Account> = accountController.createAccount(body, mockPrincipal)

      assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
      assertThat(response.body).isEqualTo(dummyAccount)
    }
  }
}
