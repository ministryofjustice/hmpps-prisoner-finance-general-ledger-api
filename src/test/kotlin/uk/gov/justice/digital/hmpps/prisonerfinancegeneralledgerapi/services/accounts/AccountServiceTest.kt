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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.SubAccountService
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

private const val TEST_ACCOUNT_REF = "TEST_ACCOUNT_REF"
private const val TEST_USERNAME = "TEST_USERNAME"

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

  @Mock
  lateinit var accountDataRepositoryMock: AccountDataRepository

  @Mock
  lateinit var subAccountServiceMock: SubAccountService

  @Mock
  lateinit var postingDataRepositoryMock: PostingsDataRepository

  @InjectMocks
  lateinit var accountService: AccountService

  lateinit var dummyAccountEntity: AccountEntity
  val dummyUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  @BeforeEach
  fun setupDummyAccount() {
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0).toInstant(java.time.ZoneOffset.UTC)
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

  @Nested
  inner class CalculateAccountBalance {

    @Test
    fun `Should return null if the account doesn't exist`() {
      whenever(accountDataRepositoryMock.findAccountById(dummyAccountEntity.id)).thenReturn(null)
      val accountBalance = accountService.calculateAccountBalance(dummyAccountEntity.id)

      assertThat(accountBalance).isNull()
    }

    @Test
    fun `Should return a zero balance if account exists without subaccounts`() {
      whenever(accountDataRepositoryMock.findAccountById(dummyAccountEntity.id)).thenReturn(dummyAccountEntity)

      val accountBalance = accountService.calculateAccountBalance(dummyAccountEntity.id)

      assertThat(accountBalance!!.accountId).isEqualTo(dummyAccountEntity.id)
      assertThat(accountBalance.amount).isEqualTo(0)
      assertThat(accountBalance.balanceDateTime).isInThePast
      verify(subAccountServiceMock, times(0)).getSubAccountBalance(any())
    }

    @Test
    fun `Should return the subaccount balance when there is only one on the account`() {
      whenever(accountDataRepositoryMock.findAccountById(dummyAccountEntity.id)).thenReturn(dummyAccountEntity)
      dummyAccountEntity.subAccounts.add(SubAccountEntity(reference = "123456", parentAccountEntity = dummyAccountEntity))

      whenever(subAccountServiceMock.getSubAccountBalance(dummyAccountEntity.subAccounts[0].id)).thenReturn(
        SubAccountBalanceResponse(
          subAccountId = dummyAccountEntity.subAccounts[0].id,
          amount =
          10,
          balanceDateTime = Instant.now(),
        ),
      )

      val accountBalance = accountService.calculateAccountBalance(dummyAccountEntity.id)

      assertThat(accountBalance!!.accountId).isEqualTo(dummyAccountEntity.id)
      assertThat(accountBalance.amount).isEqualTo(10)
      assertThat(accountBalance.balanceDateTime).isInThePast
      verify(subAccountServiceMock, times(1)).getSubAccountBalance(subAccountId = dummyAccountEntity.subAccounts[0].id)
    }

    @Test
    fun `Should return the summed subaccount balances when there are multiple subaccounts on the account`() {
      whenever(accountDataRepositoryMock.findAccountById(dummyAccountEntity.id)).thenReturn(dummyAccountEntity)
      dummyAccountEntity.subAccounts.add(SubAccountEntity(reference = "123456", parentAccountEntity = dummyAccountEntity))
      dummyAccountEntity.subAccounts.add(SubAccountEntity(reference = "654321", parentAccountEntity = dummyAccountEntity))

      whenever(subAccountServiceMock.getSubAccountBalance(dummyAccountEntity.subAccounts[0].id)).thenReturn(SubAccountBalanceResponse(subAccountId = dummyAccountEntity.subAccounts[0].id, amount = 10, balanceDateTime = Instant.now()))

      whenever(subAccountServiceMock.getSubAccountBalance(dummyAccountEntity.subAccounts[1].id)).thenReturn(SubAccountBalanceResponse(subAccountId = dummyAccountEntity.subAccounts[0].id, amount = 100, balanceDateTime = Instant.now()))

      val accountBalance = accountService.calculateAccountBalance(dummyAccountEntity.id)

      assertThat(accountBalance!!.accountId).isEqualTo(dummyAccountEntity.id)
      assertThat(accountBalance.amount).isEqualTo(110)
      assertThat(accountBalance.balanceDateTime).isInThePast
      verify(subAccountServiceMock, times(2)).getSubAccountBalance(any())
    }
  }

  @Nested
  inner class CalculatePrisonersBalanceAtAPrison {

    lateinit var dummyPrisonAccount: AccountEntity
    lateinit var dummyPrisonerAccount: AccountEntity

    @BeforeEach
    fun setUp() {
      dummyPrisonAccount = AccountEntity(reference = "LEI")
      dummyPrisonerAccount = AccountEntity(reference = "123456")
    }

    @Test
    fun `If a prisoner and prison exists with no shared transactions, returns a balance of 0`() {
      whenever(accountDataRepositoryMock.findAccountByReference(any())).thenReturn(dummyPrisonAccount)
      whenever(accountDataRepositoryMock.findAccountById(any())).thenReturn(dummyPrisonerAccount)
      whenever(postingDataRepositoryMock.getBalanceForAPrisonerAtAPrison(dummyPrisonAccount.id, dummyPrisonerAccount.id)).thenReturn(0)

      val prisonerBalance = accountService.calculatePrisonerBalanceAtAPrison(dummyPrisonerAccount.id, dummyPrisonAccount.reference)

      assertThat(prisonerBalance?.amount).isEqualTo(0)
      assertThat(prisonerBalance?.accountId).isEqualTo(dummyPrisonerAccount.id)
    }

    @Test
    fun `If a prisoner and prison exists with shared transactions, returns the balance`() {
      whenever(accountDataRepositoryMock.findAccountByReference(any())).thenReturn(dummyPrisonAccount)
      whenever(accountDataRepositoryMock.findAccountById(any())).thenReturn(dummyPrisonerAccount)
      whenever(postingDataRepositoryMock.getBalanceForAPrisonerAtAPrison(dummyPrisonAccount.id, dummyPrisonerAccount.id)).thenReturn(10)

      val prisonerBalance = accountService.calculatePrisonerBalanceAtAPrison(dummyPrisonerAccount.id, dummyPrisonAccount.reference)

      assertThat(prisonerBalance?.amount).isEqualTo(10)
      assertThat(prisonerBalance?.accountId).isEqualTo(dummyPrisonerAccount.id)
    }

    @Test
    fun `If a prisoner doesnt exist, returns null, doesnt call any more repo methods`() {
      whenever(accountDataRepositoryMock.findAccountById(any())).thenReturn(null)

      val prisonerBalance = accountService.calculatePrisonerBalanceAtAPrison(dummyPrisonerAccount.id, dummyPrisonerAccount.reference)

      assertThat(prisonerBalance).isNull()
      verify(accountDataRepositoryMock, times(0)).findAccountByReference(any())
      verify(postingDataRepositoryMock, times(0)).getBalanceForAPrisonerAtAPrison(any(), any())
    }

    @Test
    fun `If a prison doesnt exist, returns a zero balance object and doesnt call any more repo methods`() {
      whenever(accountDataRepositoryMock.findAccountById(any())).thenReturn(dummyPrisonerAccount)
      whenever(accountDataRepositoryMock.findAccountByReference(any())).thenReturn(null)

      val prisonerBalance = accountService.calculatePrisonerBalanceAtAPrison(dummyPrisonerAccount.id, dummyPrisonerAccount.reference)

      assertThat(prisonerBalance?.amount).isEqualTo(0)
      verify(postingDataRepositoryMock, times(0)).getBalanceForAPrisonerAtAPrison(any(), any())
    }
  }
}
