package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.ProcessPostingBalanceService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CalculatedBalanceEventListenerTest {

  // This is unused in the test but required for the service
  @Spy
  var objectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())

  @Mock
  lateinit var processPostingBalanceService: ProcessPostingBalanceService

  @Mock
  lateinit var messagePublisher: MessagePublisher

  @Mock
  lateinit var telemetryClient: TelemetryClient

  @InjectMocks
  lateinit var calculatedBalanceEventListener: CalculatedBalanceEventListener

  private lateinit var listAppender: ListAppender<ILoggingEvent>
  private lateinit var eventLogger: Logger

  @BeforeEach
  fun setupLogger() {
    eventLogger = LoggerFactory.getLogger(CalculatedBalanceEventListener::class.java) as Logger

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
    val accountId = UUID.randomUUID()
    val source = "test"
    val chainPosition = 0L
    val message = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountId",
        "source" : "$source",
        "chainPosition" : $chainPosition
      }
    """.trimIndent()

    calculatedBalanceEventListener.handleEvents(message)

    verify(processPostingBalanceService).processBalance(accountId)
    verify(messagePublisher, never())
      .sendMessage(
        payloadDataClass = any<PayloadDataClass>(),
        queueId = any(),
        messageGroupId = any(),
      )
  }

  @Test
  fun `should log error when balance calculation fails`() {
    val postingId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val source = "test"
    val chainPosition = 0L
    val message = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountId",
        "source" : "$source",
        "chainPosition" : $chainPosition
      }
    """.trimIndent()

    val exceptionMessage = "Test error"
    whenever { processPostingBalanceService.processBalance(accountId) }.thenThrow(RuntimeException(exceptionMessage))

    assertThatThrownBy { calculatedBalanceEventListener.handleEvents(message) }.isInstanceOf(RuntimeException::class.java)

    verify(processPostingBalanceService).processBalance(accountId)

    val logList = listAppender.list
    assertThat(logList).hasSize(1)

    val logEvent = logList[0]
    assertThat(logEvent.level).isEqualTo(Level.ERROR)
    assertThat(logEvent.formattedMessage).contains("Failed to process balance calculation")
    assertThat(logEvent.formattedMessage).contains(exceptionMessage)
  }
}
