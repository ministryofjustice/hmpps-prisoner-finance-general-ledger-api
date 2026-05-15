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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
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

  fun verifyService(transaction: TransactionEntity, posting: PostingEntity, subAccountAmount: Long, subAccount: SubAccountEntity) {
    verify(postingBalanceDataRepository, times(1)).getPreviousPostingBalanceOrNull(
      postingId = posting.id,
      subAccountId = subAccount.id,
      transactionTimestamp = transaction.timestamp,
    )

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount.id, transaction.timestamp)

    val postingBalanceEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(postingBalanceEntity.capture())
    assertThat(postingBalanceEntity.firstValue.totalSubAccountBalance).isEqualTo(subAccountAmount)
    assertThat(postingBalanceEntity.firstValue.postingEntity).isEqualTo(posting)
  }

  fun setupMocks(
    transaction: TransactionEntity,
    posting: PostingEntity,
    postingBalanceEntity: PostingBalanceEntity?,
    statementBalanceEntity: StatementBalanceEntity?,
    subAccount: SubAccountEntity,
  ) {
    whenever(
      postingBalanceDataRepository.getPreviousPostingBalanceOrNull(
        postingId = posting.id,
        subAccountId = subAccount.id,
        transactionTimestamp = transaction.timestamp,
      ),
    ).thenReturn(postingBalanceEntity)

    whenever(
      statementBalanceDataRepository
        .getLatestStatementBalanceForSubAccountId(
          subAccount.id,
          transaction.timestamp,
        ),
    ).thenReturn(statementBalanceEntity)

    whenever(postingBalanceDataRepository.save(any<PostingBalanceEntity>()))
      .thenAnswer { it.arguments[0] }
  }

  @Nested
  inner class CalculatePostingBalance {
    @ParameterizedTest
    @CsvSource(
      "false, 1, 10",
      "true, 0, -10",
    )
    fun `Should calculate posting balance after transaction when there is not previous posting balance or statement balance`(
      isDebit: Boolean,
      postingIndex: Int,
      amount: Long,
    ) {
      val transaction = createTransaction(isDebit = isDebit, timestamp = Instant.now(), amount = abs(amount))
      setupMocks(
        transaction = transaction,
        posting = transaction.postings[postingIndex],
        postingBalanceEntity = null,
        statementBalanceEntity = null,
        subAccount = subAccount1,
      )

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[postingIndex])

      verifyService(
        transaction = transaction,
        posting = transaction.postings[postingIndex],
        subAccountAmount = amount,
        subAccount = subAccount1,
      )
    }

    @Test
    fun `Should update calculated posting balance when there is an existing posting balance associated with the posting`() {
      val amount = 10L
      val transaction = createTransaction(isDebit = false, timestamp = Instant.now(), amount = amount)

      setupMocks(
        transaction = transaction,
        posting = transaction.postings[1],
        postingBalanceEntity = null,
        statementBalanceEntity = null,
        subAccount = subAccount1,
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

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[1])

      assertThat(existingPostingBalance.totalSubAccountBalance).isEqualTo(amount)
      assertThat(existingPostingBalance.updatedAt).isNotNull()
      verify(postingBalanceDataRepository, times(1)).save(existingPostingBalance)
    }

    @Test
    fun `Should calculate posting balance after transaction when there is a previous statement balance but no previous posting balance`() {
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
        postingBalanceEntity = null,
        statementBalanceEntity = statementBalance,
        subAccount = subAccount1,
      )

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[1])

      verifyService(
        transaction = transaction,
        posting = transaction.postings[1],
        subAccountAmount = statementBalance.amount + amount,
        subAccount = subAccount1,
      )
    }

    @Test
    fun `Should calculate posting balance after transaction when the previous posting balance is more recent than the previous statement balance`() {
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
        postingBalanceEntity = postingBalances.first,
        statementBalanceEntity = statementBalance,
        subAccount = subAccount1,
      )
      postingBalanceService.calculatePostingBalance(
        posting = transaction.postings[1],
      )

      verifyService(
        transaction = transaction,
        posting = transaction.postings[1],
        subAccountAmount = postingBalances.first.totalSubAccountBalance + amount,
        subAccount = subAccount1,
      )
    }

    @Test
    fun `Should calculate posting balance after transaction when the previous statement balance is more recent than the previous posting balance`() {
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
        postingBalanceEntity = postingBalances.first,
        statementBalanceEntity = statementBalance,
        subAccount = subAccount1,
      )

      postingBalanceService.calculatePostingBalance(
        posting = transaction.postings[1],
      )

      verifyService(
        transaction = transaction,
        posting = transaction.postings[1],
        subAccountAmount = statementBalance.amount + amount,
        subAccount = subAccount1,
      )
    }
  }
}
