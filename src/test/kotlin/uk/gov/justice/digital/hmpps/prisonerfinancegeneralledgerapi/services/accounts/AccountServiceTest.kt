package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.accounts

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

  @Mock
  lateinit var accountRepositoryMock: AccountRepository

  @InjectMocks
  lateinit var accountService: AccountService

  lateinit var dummyAccount: Account

  @BeforeEach
  fun setupDummyAccount() {
    val testAccountReference = "01234567890"
    val testPrisonStaffID = "567"
    val dummyUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
    dummyAccount = Account(reference = testAccountReference, createdBy = testPrisonStaffID, uuid = dummyUUID, createdAt = dummyDate)
  }

  @Nested
  inner class CreateAccount {

    @Test
    fun `Should call the repository to save the account and return it`() {
      whenever(accountRepositoryMock.save(any())).thenReturn(dummyAccount)

      val createdAccount: Account = accountService.createAccount(reference = dummyAccount.reference, createdBy = dummyAccount.createdBy)
      assertThat(createdAccount).isEqualTo(dummyAccount)

      val captor = argumentCaptor<Account>()
      verify(accountRepositoryMock).save(captor.capture())
      assertThat(captor.firstValue.reference).isEqualTo(dummyAccount.reference)
      assertThat(captor.firstValue.createdBy).isEqualTo(dummyAccount.createdBy)

      verify(accountRepositoryMock, times(1)).save(any())
    }
  }
}
