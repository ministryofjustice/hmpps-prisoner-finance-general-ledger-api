package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.subaccounts

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccount
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.SubAccountService
import java.time.LocalDateTime
import java.util.UUID

private const val TEST_SUB_ACCOUNT_REF = "TEST_SUB_ACCOUNT_REF"
private const val TEST_USERNAME = "TEST_USERNAME"

@ExtendWith(MockitoExtension::class)
class SubAccountServiceTest {

  @Mock
  lateinit var subAccountRepositoryMock: SubAccountRepository

  @InjectMocks
  lateinit var subAccountService: SubAccountService

  lateinit var dummySubAccount: SubAccount
  val dummySubAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
  val dummyParentAccountUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

  @BeforeEach
  fun setupDummySubAccount() {
    val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
    dummySubAccount = SubAccount(reference = TEST_SUB_ACCOUNT_REF, createdBy = TEST_USERNAME, createdAt = dummyDate, accountId = dummyParentAccountUUID, id = dummySubAccountUUID)
  }

  @Nested
  inner class CreateSubAccount {

    @Test
    fun `Should call the repository to save the sub account and return it`() {
      whenever(subAccountRepositoryMock.save(any())).thenReturn(dummySubAccount)
    }
  }
}
