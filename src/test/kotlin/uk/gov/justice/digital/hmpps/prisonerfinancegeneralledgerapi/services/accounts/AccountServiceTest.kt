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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import java.time.LocalDateTime
import java.util.UUID

private const val TEST_ACCOUNT_REF = "TEST_ACCOUNT_REF"
private const val TEST_USERNAME = "TEST_USERNAME"

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

  @Mock
  lateinit var accountDataRepositoryMock: AccountDataRepository

  @InjectMocks
  lateinit var accountService: AccountService

  lateinit var dummyAccountEntity: AccountEntity
  val dummyUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  @BeforeEach
  fun setupDummyAccount() {
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
    dummyAccountEntity =
      AccountEntity(reference = TEST_ACCOUNT_REF, createdBy = TEST_USERNAME, id = dummyUUID, createdAt = dummyDate)
  }

  @Nested
  inner class CreateAccount {

    @Test
    fun `Should call the repository to save the account and return it`() {
      whenever(accountDataRepositoryMock.save(any())).thenReturn(dummyAccountEntity)
      val createdAccountEntity: AccountEntity =
        accountService.createAccount(reference = TEST_ACCOUNT_REF, createdBy = TEST_USERNAME)
      assertThat(createdAccountEntity).isEqualTo(dummyAccountEntity)

      verify(accountDataRepositoryMock, times(1)).save(any())
      val captor = argumentCaptor<AccountEntity>()
      verify(accountDataRepositoryMock).save(captor.capture())
      val accountToSave = captor.firstValue
      assertThat(accountToSave.reference).isEqualTo(TEST_ACCOUNT_REF)
      assertThat(accountToSave.createdBy).isEqualTo(TEST_USERNAME)
    }
  }

  @Nested
  inner class ReadAccount {

    @Test
    fun `Should call the repository with a valid ID and return the correct account`() {
      whenever(accountDataRepositoryMock.findAccountById(dummyUUID)).thenReturn(dummyAccountEntity)

      val retrievedAccountEntity: AccountEntity? = accountService.readAccount(dummyUUID)
      assertThat(retrievedAccountEntity).isEqualTo(dummyAccountEntity)
    }

    @Test
    fun `Should return null if the account does not exist`() {
      val incorrectUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
      whenever(accountDataRepositoryMock.findAccountById(incorrectUUID)).thenReturn(null)
      val retrievedAccount = accountService.readAccount(incorrectUUID)
      assertThat(retrievedAccount).isNull()
    }
  }

  @Nested
  inner class FindAccounts {

    @Test
    fun `Should call the repository with the reference provided and return a list of matching accounts`() {
      whenever(accountDataRepositoryMock.findAccountByReference(TEST_ACCOUNT_REF)).thenReturn(dummyAccountEntity)

      val retrievedAccounts = accountService.findAccounts(TEST_ACCOUNT_REF)

      assertThat(retrievedAccounts.first()).isEqualTo(dummyAccountEntity)
      assertThat(retrievedAccounts.size).isEqualTo(1)
    }
  }
}
