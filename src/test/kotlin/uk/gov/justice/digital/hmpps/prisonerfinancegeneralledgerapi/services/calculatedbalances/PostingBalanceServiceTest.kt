package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.calculatedbalances

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingBalanceService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class PostingBalanceServiceTest {

  @Mock
  lateinit var postingBalanceDataRepository: PostingBalanceDataRepository

  @InjectMocks
  lateinit var postingBalanceService: PostingBalanceService

  private val serviceTestHelpers = ServiceTestHelpers()

  @Test
  fun `Should get the previous posting balance from the repository`() {
    val parentAccount = serviceTestHelpers.createAccount(ref="ABC123ZX", type=AccountType.PRISONER)
    val subAccount1 = serviceTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
    val subAccount2 = serviceTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)
    val postingBalances = serviceTestHelpers.createPostingBalance(
      subAccount1=subAccount1,
      subAccount2=subAccount2,
      transactionTimeStamp= Instant.now(),
      transactionAmount=1000,
      subAccountBalance1=1000,
      subAccountBalance2=1000,
      totalAccountBalance=1000,
    )

    val transaction = serviceTestHelpers.createOneToOneTransaction(
      transactionAmount=10,
      transactionDateTime = Instant.now(),
      debitSubAccount = subAccount1,
      creditSubAccount = subAccount2,
    )

    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(postingBalances.first)

    postingBalanceService.calculatePostingBalance(
      subAccountId = subAccount1.id,
      transaction.postings.first(),
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)
    }
}
