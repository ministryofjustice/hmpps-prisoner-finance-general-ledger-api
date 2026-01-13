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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccount
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.SubAccountService
import java.time.LocalDateTime
import java.util.UUID

private const val TEST_ACCOUNT_REF = "TEST_ACCOUNT_REF"
private const val TEST_SUB_ACCOUNT_REF = "TEST_SUB_ACCOUNT_REF"
private const val TEST_USERNAME = "TEST_USERNAME"

@ExtendWith(MockitoExtension::class)
class SubAccountServiceTest {

  @Mock
  lateinit var subAccountRepositoryMock: SubAccountRepository

  @Mock
  lateinit var accountRepository: AccountRepository

  @InjectMocks
  lateinit var subAccountService: SubAccountService

  lateinit var dummyParentAccount: Account
  lateinit var dummySubAccount: SubAccount

  val dummySubAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val dummyParentAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  @BeforeEach
  fun setupDummySubAccount() {
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
    dummyParentAccount = Account(reference = TEST_ACCOUNT_REF, id = dummyParentAccountUUID)
    dummySubAccount = SubAccount(
      reference = TEST_SUB_ACCOUNT_REF,
      createdBy = TEST_USERNAME,
      createdAt = dummyDate,
      parentAccount = dummyParentAccount,
      id = dummySubAccountUUID,
    )
  }

  @Nested
  inner class CreateSubAccount {

    @Test
    fun `Should call the repository to save the sub account and return it`() {
      whenever(accountRepository.getReferenceById(dummyParentAccountUUID)).thenReturn(dummyParentAccount)
      whenever(subAccountRepositoryMock.save(any())).thenReturn(dummySubAccount)
      val createdSubAccount = subAccountService.createSubAccount(
        reference = TEST_SUB_ACCOUNT_REF,
        createdBy = TEST_USERNAME,
        parentAccountId = dummyParentAccountUUID,
      )
      assertThat(createdSubAccount).isEqualTo(dummySubAccount)

      verify(subAccountRepositoryMock, times(1)).save(any())
      val captor = argumentCaptor<SubAccount>()
      verify(subAccountRepositoryMock).save(captor.capture())
      val subAccountToSave = captor.firstValue
      assertThat(subAccountToSave.reference).isEqualTo(TEST_SUB_ACCOUNT_REF)
      assertThat(subAccountToSave.createdBy).isEqualTo(TEST_USERNAME)
      assertThat(subAccountToSave.parentAccount.id).isEqualTo(dummyParentAccountUUID)
    }
  }
}
