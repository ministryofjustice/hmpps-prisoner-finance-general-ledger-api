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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.SubAccountService
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

  @InjectMocks
  lateinit var subAccountService: SubAccountService

  lateinit var dummyParentAccountEntity: AccountEntity
  lateinit var dummySubAccountEntity: SubAccountEntity

  val dummySubAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val dummyParentAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  @BeforeEach
  fun setupDummySubAccount() {
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
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
  inner class ReadSubAccountBalance {

    @Test
    fun `Should check if the subAccount exists and call the method on the postings repository to get the balance`() {
      whenever(subAccountDataRepositoryMock.getReferenceById(dummySubAccountUUID)).thenReturn(dummySubAccountEntity)
      whenever(postingsDataRepository.getNetAmountForSubAccount(dummySubAccountUUID)).thenReturn(10)

      val subAccountBalance = subAccountService.getSubAccountBalance(dummySubAccountUUID)

      assertThat(subAccountBalance?.subAccountId).isEqualTo(dummySubAccountUUID)
      assertThat(subAccountBalance?.balanceDateTime).isInThePast
      assertThat(subAccountBalance?.amount).isEqualTo(10)
    }

    @Test
    fun `Should return null if no subAccount found with the id`() {
      val randomUUID = UUID.randomUUID()
      whenever(subAccountDataRepositoryMock.getReferenceById(randomUUID)).thenReturn(null)

      val subAccountBalance = subAccountService.getSubAccountBalance(randomUUID)

      assertThat(subAccountBalance).isNull()
    }
  }
}
