package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.calculatedbalances

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.balances.PostingBalanceCalculator
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PostingBalanceCalculatorTest {

  @Mock
  private lateinit var statementBalanceDataRepository: StatementBalanceDataRepository

  @Mock
  private lateinit var postingBalancesRepository: PostingBalanceDataRepository

  private val mockSubAccounts = mutableListOf<SubAccountEntity>()
  private fun mockPosting(
    posting: PostingEntity,
    subAccountId: UUID = UUID.randomUUID(),
    parentAccountId: UUID = UUID.randomUUID(),
    postingEntrySequence: Long = 1,
    transactionEntrySequence: Long = 1,
    timestamp: Instant,
    amount: Long = 10,
    postingType: PostingType,
  ) {
    val mockSubAccountEntity = mock<SubAccountEntity>()
    mockSubAccounts.add(mockSubAccountEntity)

    val mockParentAccountEntity = mock<AccountEntity>()

    val mockTransaction = mock<TransactionEntity>()

    // posting
    lenient().whenever(posting.id).thenReturn(UUID.randomUUID())
    lenient().whenever(posting.entrySequence).thenReturn(postingEntrySequence)
    lenient().whenever(posting.amount).thenReturn(amount)
    lenient().whenever(posting.type).thenReturn(postingType)

    // subAccount
    lenient().whenever(posting.subAccountEntity).thenReturn(mockSubAccountEntity)
    lenient().whenever(mockSubAccountEntity.id).thenReturn(subAccountId)

    // parentAccount
    lenient().whenever(mockSubAccountEntity.parentAccountEntity).thenReturn(mockParentAccountEntity)
    lenient().whenever(mockParentAccountEntity.id).thenReturn(parentAccountId)
    lenient().whenever(mockParentAccountEntity.subAccounts).thenReturn(mockSubAccounts)

    // transaction
    lenient().whenever(posting.transactionEntity).thenReturn(mockTransaction)
    lenient().whenever(mockTransaction.timestamp).thenReturn(timestamp)
    lenient().whenever(mockTransaction.entrySequence).thenReturn(transactionEntrySequence)
  }

  @Nested
  inner class Calculate {

    val postingInThePast: PostingEntity = mock()
    val startPosting: PostingEntity = mock()
    val nextPosting: PostingEntity = mock()
    val subAccountId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setup() {
      mockSubAccounts.removeAll { true }
    }

    @Test
    fun `Should calculate from zero when there are no previous posting balances and no statement balances`() {
      mockPosting(
        startPosting,
        subAccountId = subAccountId,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = Instant.now(),
        amount = 10,
        postingType = PostingType.CR,
      )

      mockPosting(
        nextPosting,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = startPosting.transactionEntity.timestamp.plusSeconds(10),
        amount = 5,
        postingType = PostingType.CR,
      )

      whenever(
        postingBalancesRepository.getPreviousPostingBalancesByAccount(
          postingId = startPosting.id,
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
          transactionTimestamp = startPosting.transactionEntity.timestamp,
          transactionEntrySequence = startPosting.transactionEntity.entrySequence,
          postingEntrySequence = startPosting.entrySequence,
        ),
      ).thenReturn(emptyList())

      whenever(
        statementBalanceDataRepository.getStatementBalancesByAccountDescOrdered(
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
        ),
      ).thenReturn(emptyList())

      val postingBalanceCalculator = PostingBalanceCalculator(
        statementBalanceDataRepository = statementBalanceDataRepository,
        postingBalancesRepository = postingBalancesRepository,
        startPosting = startPosting,
      )

      val startPostingResponse = postingBalanceCalculator.calculate(startPosting)
      assertThat(startPostingResponse.accountBalance).isEqualTo(10)
      assertThat(startPostingResponse.postingBalance.amount).isEqualTo(10)
      assertThat(startPostingResponse.postingBalance.timestamp).isEqualTo(startPosting.transactionEntity.timestamp)

      val nextPostingResponse = postingBalanceCalculator.calculate(nextPosting)
      assertThat(nextPostingResponse.accountBalance).isEqualTo(15)
      assertThat(nextPostingResponse.postingBalance.amount).isEqualTo(15)
      assertThat(nextPostingResponse.postingBalance.timestamp).isEqualTo(nextPosting.transactionEntity.timestamp)
    }

    @Test
    fun `Should calculate from the statement balance when there are no previous posting balances`() {
      mockPosting(
        startPosting,
        subAccountId = subAccountId,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = Instant.now(),
        amount = 10,
        postingType = PostingType.CR,
      )

      mockPosting(
        nextPosting,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = startPosting.transactionEntity.timestamp.plusSeconds(10),
        amount = 5,
        postingType = PostingType.CR,
      )

      whenever(
        postingBalancesRepository.getPreviousPostingBalancesByAccount(
          postingId = startPosting.id,
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
          transactionTimestamp = startPosting.transactionEntity.timestamp,
          transactionEntrySequence = startPosting.transactionEntity.entrySequence,
          postingEntrySequence = startPosting.entrySequence,
        ),
      ).thenReturn(emptyList())

      val statementBalance = StatementBalanceEntity(
        subAccountEntity = startPosting.subAccountEntity,
        balanceDateTime = startPosting.transactionEntity.timestamp.minusSeconds(10),
        amount = 8,
      )
      whenever(
        statementBalanceDataRepository.getStatementBalancesByAccountDescOrdered(
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
        ),
      ).thenReturn(listOf(statementBalance))

      val postingBalanceCalculator = PostingBalanceCalculator(
        statementBalanceDataRepository = statementBalanceDataRepository,
        postingBalancesRepository = postingBalancesRepository,
        startPosting = startPosting,
      )

      val startPostingResponse = postingBalanceCalculator.calculate(startPosting)

      assertThat(startPostingResponse.accountBalance).isEqualTo(statementBalance.amount + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.amount).isEqualTo(statementBalance.amount + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.timestamp).isEqualTo(startPosting.transactionEntity.timestamp)

      val nextPostingResponse = postingBalanceCalculator.calculate(nextPosting)

      assertThat(nextPostingResponse.accountBalance).isEqualTo(statementBalance.amount + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.amount).isEqualTo(statementBalance.amount + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.timestamp).isEqualTo(nextPosting.transactionEntity.timestamp)
    }

    @Test
    fun `Should calculate from the previous posting balance when there is no previous statement balance`() {
      mockPosting(
        startPosting,
        subAccountId = subAccountId,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = Instant.now(),
        amount = 10,
        postingType = PostingType.CR,
      )

      mockPosting(
        nextPosting,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = startPosting.transactionEntity.timestamp.plusSeconds(10),
        amount = 5,
        postingType = PostingType.CR,
      )

      mockPosting(
        postingInThePast,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = startPosting.transactionEntity.timestamp.minusSeconds(10),
        amount = 5,
        postingType = PostingType.CR,
      )

      val previousPostingBalance = PostingBalanceEntity(
        postingEntity = postingInThePast,
        totalSubAccountBalance = 10,
        totalAccountBalance = 99,
        createdAt = Instant.now(),
        updatedAt = null,
      )

      whenever(
        postingBalancesRepository.getPreviousPostingBalancesByAccount(
          postingId = startPosting.id,
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
          transactionTimestamp = startPosting.transactionEntity.timestamp,
          transactionEntrySequence = startPosting.transactionEntity.entrySequence,
          postingEntrySequence = startPosting.entrySequence,
        ),
      ).thenReturn(listOf(previousPostingBalance))

      whenever(
        statementBalanceDataRepository.getStatementBalancesByAccountDescOrdered(
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
        ),
      ).thenReturn(emptyList())

      val postingBalanceCalculator = PostingBalanceCalculator(
        statementBalanceDataRepository = statementBalanceDataRepository,
        postingBalancesRepository = postingBalancesRepository,
        startPosting = startPosting,
      )

      val startPostingResponse = postingBalanceCalculator.calculate(startPosting)

      assertThat(startPostingResponse.accountBalance).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.amount).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.timestamp).isEqualTo(startPosting.transactionEntity.timestamp)

      val nextPostingResponse = postingBalanceCalculator.calculate(nextPosting)

      assertThat(nextPostingResponse.accountBalance).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.amount).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.timestamp).isEqualTo(nextPosting.transactionEntity.timestamp)
    }

    @Test
    fun `Should calculate from the statement balance even if there is an older posting balance`() {
      mockPosting(
        startPosting,
        subAccountId = subAccountId,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = Instant.now(),
        amount = 10,
        postingType = PostingType.CR,
      )

      mockPosting(
        nextPosting,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = startPosting.transactionEntity.timestamp.plusSeconds(10),
        amount = 5,
        postingType = PostingType.CR,
      )

      val statementBalanceTimestamp = startPosting.transactionEntity.timestamp.minusSeconds(10)
      val postingBalanceTimestamp = startPosting.transactionEntity.timestamp.minusSeconds(100)

      mockPosting(
        postingInThePast,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = postingBalanceTimestamp,
        amount = 5,
        postingType = PostingType.CR,
      )

      val previousPostingBalance = PostingBalanceEntity(
        postingEntity = postingInThePast,
        totalSubAccountBalance = 10,
        totalAccountBalance = 99,
        createdAt = Instant.now(),
        updatedAt = null,
      )

      whenever(
        postingBalancesRepository.getPreviousPostingBalancesByAccount(
          postingId = startPosting.id,
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
          transactionTimestamp = startPosting.transactionEntity.timestamp,
          transactionEntrySequence = startPosting.transactionEntity.entrySequence,
          postingEntrySequence = startPosting.entrySequence,
        ),
      ).thenReturn(listOf(previousPostingBalance))

      val statementBalance = StatementBalanceEntity(
        subAccountEntity = startPosting.subAccountEntity,
        balanceDateTime = statementBalanceTimestamp,
        amount = 8,
      )
      whenever(
        statementBalanceDataRepository.getStatementBalancesByAccountDescOrdered(
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
        ),
      ).thenReturn(listOf(statementBalance))

      val postingBalanceCalculator = PostingBalanceCalculator(
        statementBalanceDataRepository = statementBalanceDataRepository,
        postingBalancesRepository = postingBalancesRepository,
        startPosting = startPosting,
      )

      val startPostingResponse = postingBalanceCalculator.calculate(startPosting)

      assertThat(startPostingResponse.accountBalance).isEqualTo(statementBalance.amount + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.amount).isEqualTo(statementBalance.amount + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.timestamp).isEqualTo(startPosting.transactionEntity.timestamp)

      val nextPostingResponse = postingBalanceCalculator.calculate(nextPosting)

      assertThat(nextPostingResponse.accountBalance).isEqualTo(statementBalance.amount + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.amount).isEqualTo(statementBalance.amount + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.timestamp).isEqualTo(nextPosting.transactionEntity.timestamp)
    }

    @Test
    fun `Should calculate from the posting balance even if there is an older statement balance`() {
      mockPosting(
        startPosting,
        subAccountId = subAccountId,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = Instant.now(),
        amount = 10,
        postingType = PostingType.CR,
      )

      mockPosting(
        nextPosting,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = startPosting.transactionEntity.timestamp.plusSeconds(10),
        amount = 5,
        postingType = PostingType.CR,
      )

      val statementBalanceTimestamp = startPosting.transactionEntity.timestamp.minusSeconds(100)
      val postingBalanceTimestamp = startPosting.transactionEntity.timestamp.minusSeconds(10)

      mockPosting(
        postingInThePast,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = postingBalanceTimestamp,
        amount = 5,
        postingType = PostingType.CR,
      )

      val previousPostingBalance = PostingBalanceEntity(
        postingEntity = postingInThePast,
        totalSubAccountBalance = 10,
        totalAccountBalance = 99,
        createdAt = Instant.now(),
        updatedAt = null,
      )

      whenever(
        postingBalancesRepository.getPreviousPostingBalancesByAccount(
          postingId = startPosting.id,
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
          transactionTimestamp = startPosting.transactionEntity.timestamp,
          transactionEntrySequence = startPosting.transactionEntity.entrySequence,
          postingEntrySequence = startPosting.entrySequence,
        ),
      ).thenReturn(listOf(previousPostingBalance))

      val statementBalance = StatementBalanceEntity(
        subAccountEntity = startPosting.subAccountEntity,
        balanceDateTime = statementBalanceTimestamp,
        amount = 8,
      )
      whenever(
        statementBalanceDataRepository.getStatementBalancesByAccountDescOrdered(
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
        ),
      ).thenReturn(listOf(statementBalance))

      val postingBalanceCalculator = PostingBalanceCalculator(
        statementBalanceDataRepository = statementBalanceDataRepository,
        postingBalancesRepository = postingBalancesRepository,
        startPosting = startPosting,
      )

      val startPostingResponse = postingBalanceCalculator.calculate(startPosting)

      assertThat(startPostingResponse.accountBalance).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.amount).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount)
      assertThat(startPostingResponse.postingBalance.timestamp).isEqualTo(startPosting.transactionEntity.timestamp)

      val nextPostingResponse = postingBalanceCalculator.calculate(nextPosting)

      assertThat(nextPostingResponse.accountBalance).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.amount).isEqualTo(previousPostingBalance.totalSubAccountBalance + startPosting.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.timestamp).isEqualTo(nextPosting.transactionEntity.timestamp)
    }

    @Test
    fun `Should calculate from the statement balances in-between postings`() {
      // test setup
      // start posting, balance: start posting amount
      // a statement balance in-between
      // next posting, balance: statement balance amount + next posting amount

      val startPostingTimestamp = Instant.now().minusSeconds(100)
      val statementBalanceTimestamp = startPostingTimestamp.plusSeconds(10)
      val nextPostingTimestamp = startPostingTimestamp.plusSeconds(20)

      mockPosting(
        startPosting,
        subAccountId = subAccountId,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = startPostingTimestamp,
        amount = 10,
        postingType = PostingType.CR,
      )

      mockPosting(
        nextPosting,
        subAccountId = startPosting.subAccountEntity.id,
        parentAccountId = startPosting.subAccountEntity.parentAccountEntity.id,
        postingEntrySequence = 1,
        transactionEntrySequence = 1,
        timestamp = nextPostingTimestamp,
        amount = 5,
        postingType = PostingType.CR,
      )

      whenever(
        postingBalancesRepository.getPreviousPostingBalancesByAccount(
          postingId = startPosting.id,
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
          transactionTimestamp = startPosting.transactionEntity.timestamp,
          transactionEntrySequence = startPosting.transactionEntity.entrySequence,
          postingEntrySequence = startPosting.entrySequence,
        ),
      ).thenReturn(emptyList())

      val statementBalance = StatementBalanceEntity(
        subAccountEntity = startPosting.subAccountEntity,
        balanceDateTime = statementBalanceTimestamp,
        amount = 8,
      )
      whenever(
        statementBalanceDataRepository.getStatementBalancesByAccountDescOrdered(
          accountId = startPosting.subAccountEntity.parentAccountEntity.id,
        ),
      ).thenReturn(listOf(statementBalance))

      val postingBalanceCalculator = PostingBalanceCalculator(
        statementBalanceDataRepository = statementBalanceDataRepository,
        postingBalancesRepository = postingBalancesRepository,
        startPosting = startPosting,
      )

      val startPostingResponse = postingBalanceCalculator.calculate(startPosting)

      assertThat(startPostingResponse.accountBalance).isEqualTo(startPosting.amount)
      assertThat(startPostingResponse.postingBalance.amount).isEqualTo(startPosting.amount)
      assertThat(startPostingResponse.postingBalance.timestamp).isEqualTo(startPosting.transactionEntity.timestamp)

      val nextPostingResponse = postingBalanceCalculator.calculate(nextPosting)
      assertThat(nextPostingResponse.accountBalance).isEqualTo(statementBalance.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.amount).isEqualTo(statementBalance.amount + nextPosting.amount)
      assertThat(nextPostingResponse.postingBalance.timestamp).isEqualTo(nextPosting.transactionEntity.timestamp)
    }
  }
}
