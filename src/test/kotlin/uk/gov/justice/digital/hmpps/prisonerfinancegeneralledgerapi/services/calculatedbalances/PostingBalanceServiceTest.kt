package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.calculatedbalances

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.StatementBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingBalanceService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PostingBalanceServiceTest {

  @Mock
  lateinit var postingBalanceDataRepository: PostingBalanceDataRepository

  @Mock
  lateinit var statementBalanceDataRepository: StatementBalanceDataRepository

  @InjectMocks
  lateinit var postingBalanceService: PostingBalanceService

  private val serviceTestHelpers = ServiceTestHelpers()

  @Test
  fun `Should calculate posting balance after CR transaction when there is not previous posting balance or migration`(){
    val parentAccount = serviceTestHelpers.createAccount(ref="ABC123ZX", type=AccountType.PRISONER)
    val subAccount1 = serviceTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
    val subAccount2 = serviceTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

    val transaction = serviceTestHelpers.createOneToOneTransaction(
      transactionAmount=10,
      transactionDateTime = Instant.now(),
      debitSubAccount = subAccount2,
      creditSubAccount = subAccount1,
    )

    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(null)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)).thenReturn(null)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[1],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[1])
  }

  @Test
  fun `Should calculate posting balance after DR transaction when there is not previous posting balance or migration`(){
    val parentAccount = serviceTestHelpers.createAccount(ref="ABC123ZX", type=AccountType.PRISONER)
    val subAccount1 = serviceTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
    val subAccount2 = serviceTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

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
    ).thenReturn(null)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)).thenReturn(null)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[0],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(-10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(-10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[0])
  }

  @Test
  fun `Should calculate posting balance after CR transaction when there is a previous posting balance and no previous migration`() {
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
      debitSubAccount = subAccount2,
      creditSubAccount = subAccount1,
    )

    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(postingBalances.first)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)).thenReturn(null)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[1],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(postingBalances.first.totalSubAccountBalance + 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(postingBalances.first.totalAccountBalance + 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[1])
  }

  @Test
  fun `Should calculate posting balance after DR transaction when there is a previous posting balance and no previous migration`() {
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


    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)).thenReturn(null)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[0],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(postingBalances.first.totalSubAccountBalance - 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(postingBalances.first.totalAccountBalance - 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[0])
  }

  @Test
  fun `Should calculate posting balance after CR transaction when there is no previous posting balance and a previous migration`() {
    val parentAccount = serviceTestHelpers.createAccount(ref="ABC123ZX", type=AccountType.PRISONER)
    val subAccount1 = serviceTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
    val subAccount2 = serviceTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

    val transaction = serviceTestHelpers.createOneToOneTransaction(
      transactionAmount=10,
      transactionDateTime = Instant.now(),
      debitSubAccount = subAccount2,
      creditSubAccount = subAccount1,
    )

    val migration = StatementBalanceEntity(
      id= UUID.randomUUID(),
      subAccountEntity=subAccount1,
      balanceDateTime=transaction.timestamp.minusSeconds(123213),
      amount=333,
    )


    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(null)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id))
      .thenReturn(migration)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[1],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(migration.amount + 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(migration.amount + 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[1])
  }

  @Test
  fun `Should calculate posting balance after DR transaction when there is no previous posting balance and a previous migration`() {
    val parentAccount = serviceTestHelpers.createAccount(ref="ABC123ZX", type=AccountType.PRISONER)
    val subAccount1 = serviceTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
    val subAccount2 = serviceTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

    val transaction = serviceTestHelpers.createOneToOneTransaction(
      transactionAmount=10,
      transactionDateTime = Instant.now(),
      debitSubAccount = subAccount1,
      creditSubAccount = subAccount2,
    )

    val migration = StatementBalanceEntity(
      id= UUID.randomUUID(),
      subAccountEntity=subAccount1,
      balanceDateTime=transaction.timestamp.minusSeconds(123213),
      amount=333,
    )


    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(null)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id))
      .thenReturn(migration)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[0],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(migration.amount - 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(migration.amount - 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[0])
  }

  @Test
  fun `Should calculate posting balance after CR transaction when there the previous posting balance is more recent than the previous migration`() {
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
      debitSubAccount = subAccount2,
      creditSubAccount = subAccount1,
    )

    val migration = StatementBalanceEntity(
      id= UUID.randomUUID(),
      subAccountEntity=subAccount1,
      balanceDateTime=postingBalances.first.postingEntity.transactionEntity.timestamp.minusSeconds(60),
      amount=333,
    )

    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(postingBalances.first)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id))
      .thenReturn(migration)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[1],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(postingBalances.first.totalSubAccountBalance + 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(postingBalances.first.totalAccountBalance + 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[1])
  }

  @Test
  fun `Should calculate posting balance after DR transaction when there the previous posting balance is more recent than the previous migration`() {
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

    val migration = StatementBalanceEntity(
      id= UUID.randomUUID(),
      subAccountEntity=subAccount1,
      balanceDateTime=postingBalances.first.postingEntity.transactionEntity.timestamp.minusSeconds(60),
      amount=333,
    )

    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(postingBalances.first)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id))
      .thenReturn(migration)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[0],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(postingBalances.first.totalSubAccountBalance - 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(postingBalances.first.totalAccountBalance - 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[0])
  }

  @Test
  fun `Should calculate posting balance after CR transaction when there the previous migration is more recent than the previous posting balance`() {
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
      debitSubAccount = subAccount2,
      creditSubAccount = subAccount1,
    )

    val migration = StatementBalanceEntity(
      id= UUID.randomUUID(),
      subAccountEntity=subAccount1,
      balanceDateTime=postingBalances.first.postingEntity.transactionEntity.timestamp.plusSeconds(60),
      amount=333,
    )

    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(postingBalances.first)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id))
      .thenReturn(migration)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[1],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(migration.amount + 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(migration.amount + 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[1])
  }

  @Test
  fun `Should calculate posting balance after DR transaction when there the previous migration is more recent than the previous posting balance`() {
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

    val migration = StatementBalanceEntity(
      id= UUID.randomUUID(),
      subAccountEntity=subAccount1,
      balanceDateTime=postingBalances.first.postingEntity.transactionEntity.timestamp.plusSeconds(60),
      amount=333,
    )

    whenever(
      postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccount1.id,
        transaction.timestamp)
    ).thenReturn(postingBalances.first)

    whenever(statementBalanceDataRepository
      .getLatestStatementBalanceForSubAccountId(subAccount1.id))
      .thenReturn(migration)

    postingBalanceService.calculatePostingBalance(
      transaction.postings[0],
    )

    verify(postingBalanceDataRepository, times(1)).getSubAccountBalanceOrDefault(
      subAccountId = subAccount1.id,
      transactionTimestamp = transaction.timestamp)

    verify(statementBalanceDataRepository, times(1))
      .getLatestStatementBalanceForSubAccountId(subAccount1.id)


    val savedEntity = argumentCaptor<PostingBalanceEntity>()
    verify(postingBalanceDataRepository, times(1))
      .save(savedEntity.capture())

    assertThat(savedEntity.firstValue.totalSubAccountBalance).isEqualTo(migration.amount - 10)
    assertThat(savedEntity.firstValue.totalAccountBalance).isEqualTo(migration.amount - 10)
    assertThat(savedEntity.firstValue.postingEntity).isEqualTo(transaction.postings[0])
  }
}
