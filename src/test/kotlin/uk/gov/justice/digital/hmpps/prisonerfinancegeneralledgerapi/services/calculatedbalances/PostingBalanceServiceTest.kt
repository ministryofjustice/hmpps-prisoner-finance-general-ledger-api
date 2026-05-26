package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.calculatedbalances

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingBalanceService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

@ExtendWith(MockitoExtension::class)
class PostingBalanceServiceTest {
  @Mock
  lateinit var postingBalanceDataRepository: PostingBalanceDataRepository

  @Mock
  lateinit var postingDataRepository: PostingsDataRepository

  @Mock
  lateinit var statementBalanceDataRepository: StatementBalanceDataRepository

  @InjectMocks
  lateinit var postingBalanceService: PostingBalanceService

  private val serviceTestHelpers = ServiceTestHelpers()

  val parentAccount = serviceTestHelpers.createAccount(ref = "ABC123ZX", type = AccountType.PRISONER)
  val subAccount1 = serviceTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)
  val subAccount2 = serviceTestHelpers.createSubAccount(ref = "SPENDS", account = parentAccount)
  val subAccount3 = serviceTestHelpers.createSubAccount(ref = "SAVINGS", account = parentAccount)

  val prisonAccount = serviceTestHelpers.createAccount(ref = "LEI", type = AccountType.PRISON)
  val canteenSubAccount = serviceTestHelpers.createSubAccount(ref = "CANT:1001", account = prisonAccount)

  val postingBalances = serviceTestHelpers.createPostingBalance(
    subAccount1 = subAccount1,
    subAccount2 = subAccount2,
    transactionTimeStamp = Instant.now(),
    transactionAmount = 1000,
    subAccountBalance1 = 1000,
    subAccountBalance2 = 1000,
  )

  fun createTransaction(isDebit: Boolean, timestamp: Instant, amount: Long): TransactionEntity {
    if (isDebit) {
      return serviceTestHelpers.createOneToOneTransaction(
        transactionAmount = amount,
        transactionDateTime = timestamp,
        debitSubAccount = subAccount1,
        creditSubAccount = subAccount2,
      )
    } else {
      return serviceTestHelpers.createOneToOneTransaction(
        transactionAmount = amount,
        transactionDateTime = timestamp,
        debitSubAccount = subAccount2,
        creditSubAccount = subAccount1,
      )
    }
  }

  fun verifySubAccountBalanceService(
    transaction: TransactionEntity,
    posting: PostingEntity,
    subAccountAmount: Long,
    account: AccountEntity,
  ) {
    verify(postingBalanceDataRepository, times(1)).getPreviousPostingBalancesByAccount(
      postingId = posting.id,
      accountId = account.id,
      transactionTimestamp = transaction.timestamp,
    )

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalancesForAccountId(account.id, transaction.timestamp)

    val postingBalanceEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(postingBalanceEntity.capture())
    assertThat(postingBalanceEntity.firstValue.totalSubAccountBalance).isEqualTo(subAccountAmount)
    assertThat(postingBalanceEntity.firstValue.postingEntity).isEqualTo(posting)
  }

  fun setupMocks(
    transaction: TransactionEntity,
    posting: PostingEntity,
    postingBalanceEntities: List<PostingBalanceEntity>,
    statementBalanceEntities: List<StatementBalanceEntity>,
    account: AccountEntity,
  ) {
    whenever(
      postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
        postingId = posting.id,
        accountId = account.id,
        transactionTimestamp = transaction.timestamp,
      ),
    ).thenReturn(postingBalanceEntities)

    whenever(
      statementBalanceDataRepository
        .getLatestStatementBalancesForAccountId(
          accountId = account.id,
          fromTimestamp = transaction.timestamp,
        ),
    ).thenReturn(statementBalanceEntities)

    whenever(postingBalanceDataRepository.save(any<PostingBalanceEntity>()))
      .thenAnswer { it.arguments[0] }
  }

  @Nested
  inner class CalculatePostingBalance {

    @Nested
    inner class SubAccountBalance {
      @ParameterizedTest
      @CsvSource(
        "false, 1, 10",
        "true, 0, -10",
      )
      fun `Should calculate subAccount posting balance after transaction when there is not previous posting balance or statement balance`(
        isDebit: Boolean,
        postingIndex: Int,
        amount: Long,
      ) {
        val transaction = createTransaction(isDebit = isDebit, timestamp = Instant.now(), amount = abs(amount))
        setupMocks(
          transaction = transaction,
          posting = transaction.postings[postingIndex],
          postingBalanceEntities = emptyList(),
          statementBalanceEntities = emptyList(),
          account = parentAccount,
        )

        postingBalanceService.calculatePostingBalances(posting = transaction.postings[postingIndex])

        verifySubAccountBalanceService(
          transaction = transaction,
          posting = transaction.postings[postingIndex],
          subAccountAmount = amount,
          account = parentAccount,
        )
      }

      @Test
      fun `Should update calculated subAccount posting balance when there is an existing posting balance associated with the posting`() {
        val amount = 10L
        val transaction = createTransaction(isDebit = false, timestamp = Instant.now(), amount = amount)

        setupMocks(
          transaction = transaction,
          posting = transaction.postings[1],
          postingBalanceEntities = emptyList(),
          statementBalanceEntities = emptyList(),
          account = parentAccount,
        )

        val existingPostingBalance = PostingBalanceEntity(
          id = UUID.randomUUID(),
          postingEntity = transaction.postings[1],
          totalSubAccountBalance = 1000,
        )

        whenever(
          postingBalanceDataRepository
            .findByPostingEntity(transaction.postings[1]),
        ).thenReturn(existingPostingBalance)

        postingBalanceService.calculatePostingBalances(posting = transaction.postings[1])

        assertThat(existingPostingBalance.totalSubAccountBalance).isEqualTo(amount)
        assertThat(existingPostingBalance.updatedAt).isNotNull()
        verify(postingBalanceDataRepository, times(1)).save(existingPostingBalance)
      }

      @Test
      fun `Should calculate subAccount posting balance after transaction when there is a previous statement balance but no previous posting balance`() {
        val amount = 10L
        val transaction = createTransaction(isDebit = false, timestamp = Instant.now(), amount = amount)

        val statementBalance = StatementBalanceEntity(
          id = UUID.randomUUID(),
          subAccountEntity = subAccount1,
          balanceDateTime = transaction.timestamp.minusSeconds(123213),
          amount = 333,
        )

        setupMocks(
          transaction = transaction,
          posting = transaction.postings[1],
          postingBalanceEntities = emptyList(),
          statementBalanceEntities = listOf(statementBalance),
          account = parentAccount,
        )

        postingBalanceService.calculatePostingBalances(posting = transaction.postings[1])

        verifySubAccountBalanceService(
          transaction = transaction,
          posting = transaction.postings[1],
          subAccountAmount = statementBalance.amount + amount,
          account = parentAccount,
        )
      }

      @Test
      fun `Should calculate subAccount posting balance after transaction when the previous posting balance is more recent than the previous statement balance`() {
        val amount = 10L
        val transaction = createTransaction(isDebit = false, timestamp = Instant.now(), amount = amount)

        val statementBalance = StatementBalanceEntity(
          id = UUID.randomUUID(),
          subAccountEntity = subAccount1,
          balanceDateTime = postingBalances.first.postingEntity.transactionEntity.timestamp.minusSeconds(60),
          amount = 333,
        )
        setupMocks(
          transaction = transaction,
          posting = transaction.postings[1],
          postingBalanceEntities = listOf(postingBalances.first),
          statementBalanceEntities = listOf(statementBalance),
          account = parentAccount,
        )
        postingBalanceService.calculatePostingBalances(
          posting = transaction.postings[1],
        )

        verifySubAccountBalanceService(
          transaction = transaction,
          posting = transaction.postings[1],
          subAccountAmount = postingBalances.first.totalSubAccountBalance + amount,
          account = parentAccount,
        )
      }

      @Test
      fun `Should calculate subAccount posting balance after transaction when the previous statement balance is more recent than the previous posting balance`() {
        val amount = 10L
        val transaction = createTransaction(isDebit = false, timestamp = Instant.now(), amount = amount)

        val statementBalance = StatementBalanceEntity(
          id = UUID.randomUUID(),
          subAccountEntity = subAccount1,
          balanceDateTime = postingBalances.first.postingEntity.transactionEntity.timestamp.plusSeconds(60),
          amount = 333,
        )

        setupMocks(
          transaction = transaction,
          posting = transaction.postings[1],
          postingBalanceEntities = listOf(postingBalances.first),
          statementBalanceEntities = listOf(statementBalance),
          account = parentAccount,
        )

        postingBalanceService.calculatePostingBalances(
          posting = transaction.postings[1],
        )

        verifySubAccountBalanceService(
          transaction = transaction,
          posting = transaction.postings[1],
          subAccountAmount = statementBalance.amount + amount,
          account = parentAccount,
        )
      }
    }

    @Nested
    inner class TotalAccountBalance {
      @Test
      fun `Should calculate total posting balance after transaction when there is not previous posting balance or statement balance`() {
        val amount = 10L
        val transaction = createTransaction(isDebit = false, timestamp = Instant.now(), amount = abs(amount))
        val newPosting = transaction.postings[1]
        setupMocks(
          transaction = transaction,
          posting = newPosting,
          postingBalanceEntities = emptyList(),
          statementBalanceEntities = emptyList(),
          account = parentAccount,
        )

        postingBalanceService.calculatePostingBalances(posting = newPosting)

        verify(postingBalanceDataRepository, times(1)).getPreviousPostingBalancesByAccount(
          postingId = newPosting.id,
          accountId = parentAccount.id,
          transactionTimestamp = transaction.timestamp,
        )

        verify(statementBalanceDataRepository, times(1))
          .getLatestStatementBalancesForAccountId(parentAccount.id, transaction.timestamp)

        val postingBalanceEntity = argumentCaptor<PostingBalanceEntity>()
        verify(postingBalanceDataRepository, times(1))
          .save(postingBalanceEntity.capture())
        assertThat(postingBalanceEntity.firstValue.totalSubAccountBalance).isEqualTo(amount)
        assertThat(postingBalanceEntity.firstValue.totalAccountBalance).isEqualTo(amount)
        assertThat(postingBalanceEntity.firstValue.postingEntity).isEqualTo(newPosting)
      }

      @Test
      fun `Should calculate total posting balance after posting when there are previous posting balances across multiple subAccounts and there isn't any statement balance`() {
        val amount = 10L

        val postingBalanceSubOne = serviceTestHelpers.createPostingBalance(
          subAccount1 = subAccount1,
          subAccount2 = canteenSubAccount,
          transactionTimeStamp = Instant.now(),
          transactionAmount = 1000,
          subAccountBalance1 = 1000,
          subAccountBalance2 = 1000,
        ).first

        val postingBalanceSubTwo = serviceTestHelpers.createPostingBalance(
          subAccount1 = subAccount2,
          subAccount2 = canteenSubAccount,
          transactionTimeStamp = Instant.now(),
          transactionAmount = 1000,
          subAccountBalance1 = 9999,
          subAccountBalance2 = 9999,
        ).first

        val transaction = serviceTestHelpers.createOneToOneTransaction(
          transactionAmount = amount,
          transactionDateTime = Instant.now(),
          creditSubAccount = subAccount1,
          debitSubAccount = canteenSubAccount,
        )

        val newPosting = transaction.postings[1]
        setupMocks(
          transaction = transaction,
          posting = newPosting,
          postingBalanceEntities = listOf(postingBalanceSubOne, postingBalanceSubTwo),
          statementBalanceEntities = emptyList(),
          account = parentAccount,
        )

        postingBalanceService.calculatePostingBalances(posting = newPosting)

        verify(postingBalanceDataRepository, times(1)).getPreviousPostingBalancesByAccount(
          postingId = newPosting.id,
          accountId = parentAccount.id,
          transactionTimestamp = transaction.timestamp,
        )

        verify(statementBalanceDataRepository, times(1))
          .getLatestStatementBalancesForAccountId(parentAccount.id, transaction.timestamp)

        val postingBalanceEntity = argumentCaptor<PostingBalanceEntity>()
        verify(postingBalanceDataRepository, times(1))
          .save(postingBalanceEntity.capture())

        assertThat(postingBalanceEntity.firstValue.totalSubAccountBalance).isEqualTo(amount + postingBalanceSubOne.totalSubAccountBalance)

        val expectedTotalAccountBalance = postingBalanceSubOne.totalSubAccountBalance + postingBalanceSubTwo.totalSubAccountBalance + amount
        assertThat(postingBalanceEntity.firstValue.totalAccountBalance).isEqualTo(expectedTotalAccountBalance)

        assertThat(postingBalanceEntity.firstValue.postingEntity).isEqualTo(newPosting)
      }

      @Test
      fun `Should calculate total posting balance after posting when there are statement balances across multiple subAccounts and there isn't any posting balance`() {
        val amount = 10L

        val statementBalanceSubAccountOne = serviceTestHelpers.createStatementBalance(
          amount = 333,
          balanceDateTime = Instant.now(),
          subAccount = subAccount1,
        )

        val statementBalanceSubAccountTwo = serviceTestHelpers.createStatementBalance(
          amount = 555,
          balanceDateTime = Instant.now(),
          subAccount = subAccount2,
        )

        val transaction = serviceTestHelpers.createOneToOneTransaction(
          transactionAmount = amount,
          transactionDateTime = Instant.now(),
          creditSubAccount = subAccount1,
          debitSubAccount = canteenSubAccount,
        )

        val newPosting = transaction.postings[1]
        setupMocks(
          transaction = transaction,
          posting = newPosting,
          postingBalanceEntities = emptyList(),
          statementBalanceEntities = listOf(statementBalanceSubAccountOne, statementBalanceSubAccountTwo),
          account = parentAccount,
        )

        postingBalanceService.calculatePostingBalances(posting = newPosting)

        verify(postingBalanceDataRepository, times(1)).getPreviousPostingBalancesByAccount(
          postingId = newPosting.id,
          accountId = parentAccount.id,
          transactionTimestamp = transaction.timestamp,
        )

        verify(statementBalanceDataRepository, times(1))
          .getLatestStatementBalancesForAccountId(parentAccount.id, transaction.timestamp)

        val postingBalanceEntity = argumentCaptor<PostingBalanceEntity>()
        verify(postingBalanceDataRepository, times(1))
          .save(postingBalanceEntity.capture())

        assertThat(postingBalanceEntity.firstValue.totalSubAccountBalance).isEqualTo(amount + statementBalanceSubAccountOne.amount)

        val expectedTotalAccountBalance = statementBalanceSubAccountOne.amount + statementBalanceSubAccountTwo.amount + amount
        assertThat(postingBalanceEntity.firstValue.totalAccountBalance).isEqualTo(expectedTotalAccountBalance)

        assertThat(postingBalanceEntity.firstValue.postingEntity).isEqualTo(newPosting)
      }

      @Test
      fun `Should calculate total posting balance after posting when the most recent subAccount balances have varied origins`() {
        /*
         * sub1 -> postingBalance, null
         * sub2 -> null, null -> posting to process
         * sub3 -> null, statementBalance
         */

        val amount = 10L
        val statementBalanceSubAccountThree = serviceTestHelpers.createStatementBalance(
          amount = 333,
          balanceDateTime = Instant.now(),
          subAccount = subAccount3,
        )

        val postingBalanceSubOne = serviceTestHelpers.createPostingBalance(
          subAccount1 = subAccount1,
          subAccount2 = canteenSubAccount,
          transactionTimeStamp = Instant.now(),
          transactionAmount = 1000,
          subAccountBalance1 = 1000,
          subAccountBalance2 = 1000,
        ).first

        val transaction = serviceTestHelpers.createOneToOneTransaction(
          transactionAmount = amount,
          transactionDateTime = Instant.now(),
          creditSubAccount = subAccount2,
          debitSubAccount = canteenSubAccount,
        )
        val newPosting = transaction.postings[1]

        setupMocks(
          transaction = transaction,
          posting = newPosting,
          postingBalanceEntities = listOf(postingBalanceSubOne),
          statementBalanceEntities = listOf(statementBalanceSubAccountThree),
          account = parentAccount,
        )

        postingBalanceService.calculatePostingBalances(posting = newPosting)

        verify(postingBalanceDataRepository, times(1)).getPreviousPostingBalancesByAccount(
          postingId = newPosting.id,
          accountId = parentAccount.id,
          transactionTimestamp = transaction.timestamp,
        )

        verify(statementBalanceDataRepository, times(1))
          .getLatestStatementBalancesForAccountId(parentAccount.id, transaction.timestamp)

        val postingBalanceEntity = argumentCaptor<PostingBalanceEntity>()
        verify(postingBalanceDataRepository, times(1))
          .save(postingBalanceEntity.capture())

        assertThat(postingBalanceEntity.firstValue.totalSubAccountBalance).isEqualTo(amount)

        val expectedTotalAccountBalance = statementBalanceSubAccountThree.amount + postingBalanceSubOne.totalSubAccountBalance + amount
        assertThat(postingBalanceEntity.firstValue.totalAccountBalance).isEqualTo(expectedTotalAccountBalance)

        assertThat(postingBalanceEntity.firstValue.postingEntity).isEqualTo(newPosting)
      }
    }
  }
}
