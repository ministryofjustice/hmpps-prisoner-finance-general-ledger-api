package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingBalanceService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CalculatedBalanceEventListenerTest {
  @Spy
  var objectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())

  @Mock
  lateinit var postingBalanceService: PostingBalanceService

  @Mock
  lateinit var messagePublisher: MessagePublisher

  @InjectMocks
  lateinit var calculatedBalanceEventListener: CalculateBalanceEventListener

  private lateinit var listAppender: ListAppender<ILoggingEvent>
  private lateinit var eventLogger: Logger

  @BeforeEach
  fun setupLogger() {
    eventLogger = LoggerFactory.getLogger(CalculateBalanceEventListener::class.java) as Logger

    listAppender = ListAppender<ILoggingEvent>()
    listAppender.start()

    eventLogger.addAppender(listAppender)
  }

  @AfterEach
  fun tearDownLogger() {
    eventLogger.detachAppender(listAppender)
  }

  @Test
  fun `should process balance when a valid message is received`() {
    val postingId = UUID.randomUUID()
    val message = """
      {
        "postingId" : "$postingId",
        "amount" : 77,
        "type" : "CR",
        "transactionId" : "620c0bc1-6501-4d29-955f-f039a4fc7dec",
        "transactionTimestamp" : "2026-05-13T15:01:38.998577Z",
        "transactionEntrySequence" : 1,
        "postingEntrySequence" : 1,
        "subAccountId" : "4048f04c-38bb-469e-966c-0a180bfa390c",
        "parentAccountId" : "5d9f4ba8-506a-4d21-9d00-5e19a837d6b7",
        "parentAccountType" : "PRISONER"
      }
    """.trimIndent()

    calculatedBalanceEventListener.handleEvents(message)

    verify(postingBalanceService).calculatePostingBalance(postingId)
    verify(messagePublisher, never()).sendMessage(any<PayloadDataClass>(), any())
  }

  @Test
  fun `should request a new balance calculation nextPosting is returned`() {
    val postingId = UUID.randomUUID()
    val message = """
      {
        "postingId" : "$postingId",
        "amount" : 77,
        "type" : "CR",
        "transactionId" : "620c0bc1-6501-4d29-955f-f039a4fc7dec",
        "transactionTimestamp" : "2026-05-13T15:01:38.998577Z",
        "transactionEntrySequence" : 1,
        "postingEntrySequence" : 1,
        "subAccountId" : "4048f04c-38bb-469e-966c-0a180bfa390c",
        "parentAccountId" : "5d9f4ba8-506a-4d21-9d00-5e19a837d6b7",
        "parentAccountType" : "PRISONER"
      }
    """.trimIndent()

    val nextPosting = mock<ProcessBalanceRequest>()
    whenever { postingBalanceService.calculatePostingBalance(postingId) }.thenReturn(nextPosting)

    calculatedBalanceEventListener.handleEvents(message)

    verify(postingBalanceService).calculatePostingBalance(postingId)
    verify(messagePublisher).sendMessage(nextPosting, SqsQueues.CALCULATED_BALANCE)
  }

  @Test
  fun `should log error when balance calculation fails`() {
    val postingId = UUID.randomUUID()
    val message = """
      {
        "postingId" : "$postingId",
        "amount" : 77,
        "type" : "CR",
        "transactionId" : "620c0bc1-6501-4d29-955f-f039a4fc7dec",
        "transactionTimestamp" : "2026-05-13T15:01:38.998577Z",
        "transactionEntrySequence" : 1,
        "postingEntrySequence" : 1,
        "subAccountId" : "4048f04c-38bb-469e-966c-0a180bfa390c",
        "parentAccountId" : "5d9f4ba8-506a-4d21-9d00-5e19a837d6b7",
        "parentAccountType" : "PRISONER"
      }
    """.trimIndent()

    val exceptionMessage = "Test error"
    whenever { postingBalanceService.calculatePostingBalance(postingId) }.thenThrow(RuntimeException(exceptionMessage))

    assertThatThrownBy { calculatedBalanceEventListener.handleEvents(message) }.isInstanceOf(RuntimeException::class.java)

    verify(postingBalanceService).calculatePostingBalance(postingId)

    val logList = listAppender.list
    assertThat(logList).hasSize(1)

    val logEvent = logList[0]
    assertThat(logEvent.level).isEqualTo(Level.ERROR)
    assertThat(logEvent.formattedMessage).contains("Failed to process balance calculation")
    assertThat(logEvent.formattedMessage).contains(exceptionMessage)
  }
}
