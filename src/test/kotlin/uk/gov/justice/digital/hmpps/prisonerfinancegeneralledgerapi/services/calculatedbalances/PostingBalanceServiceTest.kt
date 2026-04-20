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
  lateinit var statementBalanceDataRepository: StatementBalanceDataRepository

  @InjectMocks
  lateinit var postingBalanceService: PostingBalanceService

  private val serviceTestHelpers = ServiceTestHelpers()

  val prisonAccount = serviceTestHelpers.createAccount(ref="LEI", type=AccountType.PRISON)
  val parentAccount = serviceTestHelpers.createAccount(ref="ABC123ZX", type=AccountType.PRISONER)
  val subAccount1 = serviceTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
  val subAccount2 = serviceTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)
  val subAccountPrison = serviceTestHelpers.createSubAccount(ref="CANT:1001", account=prisonAccount)

  val postingBalances = serviceTestHelpers.createPostingBalance(
    subAccount1=subAccount1,
    subAccount2=subAccount2,
    transactionTimeStamp= Instant.now(),
    transactionAmount=1000,
    subAccountBalance1=1000,
    subAccountBalance2=1000,
    totalAccountBalance=1000,
  )


  fun createTransaction(isDebit: Boolean, timestamp: Instant, amount: Long): TransactionEntity {
    if (isDebit)
      return serviceTestHelpers.createOneToOneTransaction(
        transactionAmount=amount,
        transactionDateTime = timestamp,
        debitSubAccount = subAccount1,
        creditSubAccount = subAccount2,
      )
    else
      return serviceTestHelpers.createOneToOneTransaction(
        transactionAmount=amount,
        transactionDateTime = timestamp,
        debitSubAccount = subAccount2,
        creditSubAccount = subAccount1,
      )
  }

  fun verifyService(transaction: TransactionEntity, posting: PostingEntity, subAccountAmount: Long, totalAmount: Long, subAccount: SubAccountEntity) {
    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount.id,
      transactionTimestamp = transaction.timestamp)

    verify(postingBalanceDataRepository, times(1)).getLatestAccountBalance(
      parentAccountId = subAccount.parentAccountEntity.id,
      transactionTimestamp = transaction.timestamp
    )

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount.id)

    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(subAccountAmount)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(totalAmount)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(posting)
  }

  fun setupMocks(
    transaction: TransactionEntity,
    postingBalanceEntity: PostingBalanceEntity?,
    latestAccountPostingBalanceEntity: PostingBalanceEntity?,
    statementBalanceEntity: StatementBalanceEntity?,
    subAccount: SubAccountEntity){
    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount.id,
        transaction.timestamp)
    ).thenReturn(postingBalanceEntity)

    whenever(postingBalanceDataRepository.getLatestAccountBalance(
      parentAccountId = subAccount.parentAccountEntity.id,
      transactionTimestamp = transaction.timestamp
    )).thenReturn(latestAccountPostingBalanceEntity)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount.id)).thenReturn(statementBalanceEntity)
  }


  @Nested
  inner class SubAccountCalculation {
    @ParameterizedTest
    @CsvSource(
      "false, 1, 10",
      "true, 0, -10"
    )
    fun `Should calculate posting balance after transaction when there is not previous posting balance or migration`(
      isDebit: Boolean,
      postingIndex: Int,
      amount: Long
    ) {
      val transaction = createTransaction(isDebit = isDebit, timestamp = Instant.now(), amount = abs(amount))
      setupMocks(
        transaction = transaction,
        postingBalanceEntity = null,
        latestAccountPostingBalanceEntity= null,
        statementBalanceEntity = null,
        subAccount = subAccount1)

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[postingIndex])

      verifyService(
        transaction = transaction,
        posting = transaction.postings[postingIndex],
        subAccountAmount = amount,
        totalAmount = amount,
        subAccount = subAccount1
      )
    }

    @ParameterizedTest
    @CsvSource(
      "false, 1, 10",
      "true, 0, -10"
    )
    fun `Should calculate posting balance after transaction when there is a previous posting balance and no previous migration`(
      isDebit: Boolean,
      postingIndex: Int,
      amount: Long
    ) {
      val transaction = createTransaction(isDebit = isDebit, timestamp = Instant.now(), amount = abs(amount))

      setupMocks(
        transaction = transaction,
        postingBalanceEntity = postingBalances.first,
        latestAccountPostingBalanceEntity= postingBalances.first,
        statementBalanceEntity = null,
        subAccount = subAccount1)

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[postingIndex])

      verifyService(
        transaction = transaction,
        posting = transaction.postings[postingIndex],
        subAccountAmount = postingBalances.first.totalSubAccountBalance + amount,
        totalAmount = postingBalances.first.totalAccountBalance + amount,
        subAccount = subAccount1
      )
    }

    @ParameterizedTest
    @CsvSource(
      "false, 1, 10",
      "true, 0, -10"
    )
    fun `Should calculate posting balance after transaction when there is no previous posting balance and a previous migration`(
      isDebit: Boolean,
      postingIndex: Int,
      amount: Long
    ) {
      val transaction = createTransaction(isDebit = isDebit, timestamp = Instant.now(), amount = abs(amount))

      val migration = StatementBalanceEntity(
        id = UUID.randomUUID(),
        subAccountEntity = subAccount1,
        balanceDateTime = transaction.timestamp.minusSeconds(123213),
        amount = 333,
      )

      setupMocks(
        transaction = transaction,
        postingBalanceEntity = null,
        latestAccountPostingBalanceEntity = null,
        statementBalanceEntity = migration,
        subAccount = subAccount1)

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[postingIndex])

      verifyService(
        transaction = transaction,
        posting = transaction.postings[postingIndex],
        subAccountAmount = migration.amount + amount,
        totalAmount = migration.amount + amount,
        subAccount = subAccount1
      )
    }

    @ParameterizedTest
    @CsvSource(
      "false, 1, 10",
      "true, 0, -10"
    )
    fun `Should calculate posting balance after transaction when there the previous posting balance is more recent than the previous migration`(
      isDebit: Boolean,
      postingIndex: Int,
      amount: Long
    ) {
      val transaction = createTransaction(isDebit = isDebit, timestamp = Instant.now(), amount = abs(amount))

      val migration = StatementBalanceEntity(
        id = UUID.randomUUID(),
        subAccountEntity = subAccount1,
        balanceDateTime = postingBalances.first.postingEntity.transactionEntity.timestamp.minusSeconds(60),
        amount = 333,
      )
      setupMocks(
        transaction = transaction,
        postingBalanceEntity = postingBalances.first,
        latestAccountPostingBalanceEntity = postingBalances.first,
        statementBalanceEntity = migration,
        subAccount = subAccount1
      )
      postingBalanceService.calculatePostingBalance(
        posting = transaction.postings[postingIndex],
      )

      verifyService(
        transaction = transaction,
        posting = transaction.postings[postingIndex],
        subAccountAmount = postingBalances.first.totalSubAccountBalance + amount,
        totalAmount = postingBalances.first.totalAccountBalance + amount,
        subAccount = subAccount1
      )
    }

    @ParameterizedTest
    @CsvSource(
      "false, 1, 10",
      "true, 0, -10"
    )
    fun `Should calculate posting balance after transaction when there the previous migration is more recent than the previous posting balance`(
      isDebit: Boolean,
      postingIndex: Int,
      amount: Long
    ) {
      val transaction = createTransaction(isDebit = isDebit, timestamp = Instant.now(), amount = abs(amount))

      val migration = StatementBalanceEntity(
        id = UUID.randomUUID(),
        subAccountEntity = subAccount1,
        balanceDateTime = postingBalances.first.postingEntity.transactionEntity.timestamp.plusSeconds(60),
        amount = 333,
      )

      setupMocks(
        transaction = transaction,
        postingBalanceEntity = postingBalances.first,
        latestAccountPostingBalanceEntity = postingBalances.first,
        statementBalanceEntity = migration,
        subAccount = subAccount1
      )

      postingBalanceService.calculatePostingBalance(
        posting = transaction.postings[postingIndex],
      )

      verifyService(
        transaction = transaction,
        posting = transaction.postings[postingIndex],
        subAccountAmount = migration.amount + amount,
        totalAmount = migration.amount + amount,
        subAccount = subAccount1
      )
    }
  }

  @Nested
  inner class TotalCalculation{

    @Test
    fun `should calculate account total balance when there is no previous posting balance`(){
      val transaction = serviceTestHelpers.createOneToOneTransaction(
        transactionAmount=10L,
        transactionDateTime = Instant.now(),
        debitSubAccount = subAccount2,
        creditSubAccount = subAccountPrison,
      )

      setupMocks(
        transaction = transaction,
        postingBalanceEntity = null,
        latestAccountPostingBalanceEntity = null,
        statementBalanceEntity = null,
        subAccount = subAccount2)

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[0])

      verifyService(
        transaction = transaction,
        posting = transaction.postings[0],
        subAccountAmount = -10,
        totalAmount =  -10,
        subAccount = subAccount2
      )
    }

    @Test
    fun `should calculate account total balance when there is a previous posting balance on other subAccount but no migration`(){
      val subAccount1Balance = serviceTestHelpers.createPostingBalance(
                subAccount1=subAccount1,
                subAccount2=subAccountPrison,
                transactionTimeStamp= Instant.now().minusSeconds(60),
                transactionAmount=1000,
                subAccountBalance1=1000,
                subAccountBalance2=1000,
                totalAccountBalance=1000).first

      val transaction = serviceTestHelpers.createOneToOneTransaction(
        transactionAmount=10L,
        transactionDateTime = Instant.now(),
        debitSubAccount = subAccount2,
        creditSubAccount = subAccountPrison,
      )

      setupMocks(
        transaction = transaction,
        postingBalanceEntity = null,
        latestAccountPostingBalanceEntity = subAccount1Balance,
        statementBalanceEntity = null,
        subAccount = subAccount2)

      postingBalanceService.calculatePostingBalance(posting = transaction.postings[0])

      verifyService(
        transaction = transaction,
        posting = transaction.postings[0],
        subAccountAmount = -10,
        totalAmount = subAccount1Balance.totalAccountBalance -10,
        subAccount = subAccount2
      )
    }
  }
}
