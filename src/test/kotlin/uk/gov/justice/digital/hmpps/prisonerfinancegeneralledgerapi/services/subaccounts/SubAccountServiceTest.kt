package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.subaccounts

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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.SubAccountService
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

private const val TEST_ACCOUNT_REF = "TEST_ACCOUNT_REF"
private const val TEST_SUB_ACCOUNT_REF = "TEST_SUB_ACCOUNT_REF"
private const val TEST_USERNAME = "TEST_USERNAME"

@ExtendWith(MockitoExtension::class)
class SubAccountServiceTest {

  @Mock
  lateinit var subAccountDataRepositoryMock: SubAccountDataRepository

  @Mock
  lateinit var accountDataRepository: AccountDataRepository

  @Mock
  lateinit var postingsDataRepository: PostingsDataRepository

  @Mock
  lateinit var statementBalanceDataRepository: StatementBalanceDataRepository

  @InjectMocks
  lateinit var subAccountService: SubAccountService
  lateinit var dummyParentAccountEntity: AccountEntity
  lateinit var dummySubAccountEntity: SubAccountEntity

  val dummySubAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val dummyParentAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  @BeforeEach
  fun setupDummySubAccount() {
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0).toInstant(java.time.ZoneOffset.UTC)
    dummyParentAccountEntity = AccountEntity(reference = TEST_ACCOUNT_REF, id = dummyParentAccountUUID)
    dummySubAccountEntity = SubAccountEntity(
      reference = TEST_SUB_ACCOUNT_REF,
      createdBy = TEST_USERNAME,
      createdAt = dummyDate,
      parentAccountEntity = dummyParentAccountEntity,
      id = dummySubAccountUUID,
    )
  }

  @Nested
  inner class CreateSubAccount {

    @Test
    fun `Should call the repository to save the sub account and return it`() {
      whenever(accountDataRepository.getReferenceById(dummyParentAccountUUID)).thenReturn(dummyParentAccountEntity)
      whenever(subAccountDataRepositoryMock.save(any())).thenReturn(dummySubAccountEntity)
      val createdSubAccount = subAccountService.createSubAccount(
        reference = TEST_SUB_ACCOUNT_REF,
        createdBy = TEST_USERNAME,
        parentAccountId = dummyParentAccountUUID,
      )
      assertThat(createdSubAccount).isEqualTo(dummySubAccountEntity)

      verify(subAccountDataRepositoryMock, times(1)).save(any())
      val captor = argumentCaptor<SubAccountEntity>()
      verify(subAccountDataRepositoryMock).save(captor.capture())
      val subAccountToSave = captor.firstValue
      assertThat(subAccountToSave.reference).isEqualTo(TEST_SUB_ACCOUNT_REF)
      assertThat(subAccountToSave.createdBy).isEqualTo(TEST_USERNAME)
      assertThat(subAccountToSave.parentAccountEntity.id).isEqualTo(dummyParentAccountUUID)
    }
  }

  @Nested
  inner class FindSubAccounts {

    @Test
    fun `Should call the repo method for finding by parent account ref and sub account ref if both are present`() {
      whenever(
        subAccountDataRepositoryMock.findByParentAccountEntityReferenceAndReference(
          TEST_ACCOUNT_REF,
          TEST_SUB_ACCOUNT_REF,
        ),
      ).thenReturn(dummySubAccountEntity)

      val retrievedSubAccounts = subAccountService.findSubAccounts(TEST_ACCOUNT_REF, TEST_SUB_ACCOUNT_REF)
      assert(retrievedSubAccounts.size == 1)
      assertThat(retrievedSubAccounts.first()).isEqualTo(
        dummySubAccountEntity,
      )
    }
  }

  @Nested
  inner class GetSubAccountById {

    @Test
    fun `Should call the repo method to get the sub account and return it if a matching sub account is found`() {
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(dummySubAccountUUID)).thenReturn(dummySubAccountEntity)

      val retrievedAccount = subAccountService.getSubAccountByID(dummySubAccountUUID)

      assertThat(retrievedAccount).isEqualTo(dummySubAccountEntity)
    }

    @Test
    fun `Should return null if the repository method returns null`() {
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(any())).thenReturn(null)

      val retrievedAccount = subAccountService.getSubAccountByID(dummySubAccountUUID)

      assertThat(retrievedAccount).isNull()
    }
  }

  @Nested
  inner class ReadSubAccountBalance {

    @Test
    fun `Should return a balance when the sub account exists and has postings`() {
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(dummySubAccountUUID)).thenReturn(dummySubAccountEntity)
      whenever(postingsDataRepository.getBalanceForSubAccount(dummySubAccountUUID)).thenReturn(10)

      val subAccountBalance = subAccountService.getSubAccountBalance(dummySubAccountUUID)

      assertThat(subAccountBalance?.subAccountId).isEqualTo(dummySubAccountUUID)
      assertThat(subAccountBalance?.balanceDateTime).isInThePast
      assertThat(subAccountBalance?.amount).isEqualTo(10)
    }

    @Test
    fun `Should return null if no subAccount found with the id`() {
      val randomUUID = UUID.randomUUID()
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(randomUUID)).thenReturn(null)

      val subAccountBalance = subAccountService.getSubAccountBalance(randomUUID)

      assertThat(subAccountBalance).isNull()
    }

    @Test
    fun `Should return a balance if the sub account exists but has no postings`() {
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(dummySubAccountUUID)).thenReturn(dummySubAccountEntity)
      whenever(postingsDataRepository.getBalanceForSubAccount(dummySubAccountUUID)).thenReturn(0)

      val subAccountBalance = subAccountService.getSubAccountBalance(dummySubAccountUUID)

      assertThat(subAccountBalance?.subAccountId).isEqualTo(dummySubAccountUUID)
      assertThat(subAccountBalance?.balanceDateTime).isInThePast
      assertThat(subAccountBalance?.amount).isEqualTo(0)
    }

    @Test
    fun `Should combine latest statement balance amount with postings when there is statement balance date`() {
      val statementBalanceEntity = StatementBalanceEntity(id = UUID.randomUUID(), amount = 33L, subAccountEntity = dummySubAccountEntity)
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(dummySubAccountUUID)).thenReturn(dummySubAccountEntity)
      whenever(statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(dummySubAccountUUID)).thenReturn(statementBalanceEntity)
      whenever(postingsDataRepository.getBalanceForSubAccount(dummySubAccountUUID, latestStatementBalanceDateTime = statementBalanceEntity.balanceDateTime)).thenReturn(10)

      val subAccountBalance = subAccountService.getSubAccountBalance(dummySubAccountUUID)

      assertThat(subAccountBalance?.subAccountId).isEqualTo(dummySubAccountUUID)
      assertThat(subAccountBalance?.balanceDateTime).isInThePast
      assertThat(subAccountBalance?.amount).isEqualTo(43)
    }
  }

  @Nested
  inner class CreateStatementBalance {

    @Test
    fun `should save the balance if the provided sub account exists`() {
      val dummyStatementBalanceEntity = StatementBalanceEntity(subAccountEntity = dummySubAccountEntity, amount = 10, balanceDateTime = Instant.now())
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(dummySubAccountUUID)).thenReturn(dummySubAccountEntity)
      whenever(statementBalanceDataRepository.save(any())).thenReturn(dummyStatementBalanceEntity)

      val returnedBalance = subAccountService.createStatementBalance(dummySubAccountUUID, 10, Instant.now())
      assertThat(returnedBalance).isEqualTo(dummyStatementBalanceEntity)
    }

    @Test
    fun `should return null if the provided sub account does not exist`() {
      whenever(subAccountDataRepositoryMock.getSubAccountEntityById(any())).thenReturn(null)
      val returnedBalance = subAccountService.createStatementBalance(UUID.randomUUID(), 10, Instant.now())
      assertThat(returnedBalance).isNull()
    }
  }
}
