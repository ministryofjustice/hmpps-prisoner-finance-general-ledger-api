package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class CalculatedBalanceEventPublisherTest {
  @Mock
  lateinit var messagePublisher: MessagePublisher

  @Mock
  lateinit var postingsDataRepository: PostingsDataRepository

  @InjectMocks
  lateinit var calculatedBalanceEventPublisher: CalculatedBalanceEventPublisher

  private lateinit var listAppender: ListAppender<ILoggingEvent>
  private lateinit var eventLogger: Logger

  @BeforeEach
  fun setupLogger() {
    eventLogger = LoggerFactory.getLogger(CalculatedBalanceEventPublisher::class.java) as Logger

    listAppender = ListAppender<ILoggingEvent>()
    listAppender.start()

    eventLogger.addAppender(listAppender)
  }

  private val serviceTestHelpers = ServiceTestHelpers()

  val prisonerAccount = serviceTestHelpers.createAccount("ABC123XZ", AccountType.PRISONER)
  val prisonerCashAccount = serviceTestHelpers.createSubAccount("CASH", prisonerAccount)

  val prisonAccount = serviceTestHelpers.createAccount("LEI", AccountType.PRISON)
  val prisonCantAccount = serviceTestHelpers.createSubAccount("CANT:1010", prisonAccount)

  val transaction = serviceTestHelpers.createOneToOneTransaction(
    transactionAmount = 1,
    transactionDateTime = Instant.now(),
    debitSubAccount = prisonerCashAccount,
    creditSubAccount = prisonCantAccount,
    description = "Test Transaction",
  )

  val statementEntity = serviceTestHelpers.createStatementBalance(
    subAccount = prisonerCashAccount,
    amount = 1000,
    balanceDateTime = Instant.now(),
  )

  @Nested
  inner class RequestCalculatedBalanceForTransaction {
    @Test
    fun `Should send a message for each posting`() {
      calculatedBalanceEventPublisher.requestCalculatedBalanceForTransaction(transaction)

      val messageRequestCaptor = argumentCaptor<ProcessBalanceRequest>()
      val messageGroupIdCaptor = argumentCaptor<String>()

      verify(messagePublisher, times(transaction.postings.size))
        .sendMessage(
          payloadDataClass = messageRequestCaptor.capture(),
          queueId = eq(SqsQueues.CALCULATED_BALANCE_QUEUE_ID),
          messageGroupId = messageGroupIdCaptor.capture(),
        )

      val capturedGroupIds = messageGroupIdCaptor.allValues
      val capturedRequests = messageRequestCaptor.allValues

      val expectedPostingIds = transaction.postings.map { it.id }
      val actualPostingIds = capturedRequests.map { it.postingId }
      assertEquals(expectedPostingIds, actualPostingIds)

      val expectedMessageGroupId = transaction.postings.map { it.subAccountEntity.parentAccountEntity.id.toString() }
      val actualMessageGroupId = capturedGroupIds.map { it }
      assertEquals(expectedMessageGroupId, actualMessageGroupId)
    }

    @Test
    fun `Should log error when sending message fails`() {
      val expectedException = RuntimeException("SQS Connection Failed")
      whenever(
        messagePublisher.sendMessage(
          payloadDataClass = any<ProcessBalanceRequest>(),
          queueId = eq(SqsQueues.CALCULATED_BALANCE_QUEUE_ID),
          messageGroupId = any<String>(),
        ),
      )
        .thenThrow(expectedException)

      val firstPostingId = transaction.postings.first().id
      val expectedLogMessage = "Failed send balanceCalculation to queue for Transaction: ${transaction.id} Posting: $firstPostingId"

      calculatedBalanceEventPublisher.requestCalculatedBalanceForTransaction(transaction)

      val logs = listAppender.list
      assertThat(logs).hasSize(transaction.postings.size)

      val errorLog = logs.find { it.level == Level.ERROR }
        ?: throw AssertionError("Expected an ERROR level log but none was found")

      assertEquals(expectedLogMessage, errorLog.formattedMessage)
      assertEquals(RuntimeException::class.java.name, errorLog.throwableProxy.className)
      assertEquals("SQS Connection Failed", errorLog.throwableProxy.message)
    }
  }

  @Nested
  inner class RequestCalculatedBalanceForStatementBalance {
    @Test
    fun `Should get the next posting if there is one and send a balance calculation request`() {
      whenever {
        postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
          accountId = prisonerCashAccount.parentAccountEntity.id,
          dateTime = statementEntity.balanceDateTime,
        )
      }.thenReturn(transaction.postings.first())

      calculatedBalanceEventPublisher.requestCalculatedBalanceForStatementBalance(statementEntity)

      val messageRequestCaptor = argumentCaptor<ProcessBalanceRequest>()
      verify(messagePublisher).sendMessage(
        payloadDataClass = messageRequestCaptor.capture(),
        queueId = eq(SqsQueues.CALCULATED_BALANCE_QUEUE_ID),
        messageGroupId = eq(prisonerAccount.id.toString()),
      )
      assertThat(messageRequestCaptor.firstValue.postingId).isEqualTo(transaction.postings.first().id)
    }

    @Test
    fun `Should not send a balance calculation request if there is no next posting`() {
      whenever {
        postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
          accountId = prisonerCashAccount.parentAccountEntity.id,
          dateTime = statementEntity.balanceDateTime,
        )
      }.thenReturn(null)

      calculatedBalanceEventPublisher.requestCalculatedBalanceForStatementBalance(statementEntity)

      verify(messagePublisher, never()).sendMessage(
        payloadDataClass = any<ProcessBalanceRequest>(),
        queueId = any(),
        messageGroupId = any(),
      )
    }

    @Test
    fun `Should log error when sending message fails`() {
      whenever {
        postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
          accountId = prisonerCashAccount.parentAccountEntity.id,
          dateTime = statementEntity.balanceDateTime,
        )
      }.thenReturn(transaction.postings.first())
      val expectedException = RuntimeException("SQS Connection Failed")
      whenever {
        messagePublisher.sendMessage(
          payloadDataClass = any<ProcessBalanceRequest>(),
          queueId = eq(SqsQueues.CALCULATED_BALANCE_QUEUE_ID),
          messageGroupId = any<String>(),
        )
      }.thenThrow(expectedException)

      val expectedLogMessage = "Failed send balanceCalculation to queue for Statement: ${statementEntity.id}"

      calculatedBalanceEventPublisher.requestCalculatedBalanceForStatementBalance(statementEntity)

      val logs = listAppender.list
      assertThat(logs).hasSize(1)

      val errorLog = logs.find { it.level == Level.ERROR }
        ?: throw AssertionError("Expected an ERROR level log but none was found")

      assertThat(errorLog.formattedMessage).contains(expectedLogMessage)
      assertEquals(RuntimeException::class.java.name, errorLog.throwableProxy.className)
      assertEquals("SQS Connection Failed", errorLog.throwableProxy.message)
    }
  }
}
