package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementCallback
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.messaging.Message
import org.springframework.messaging.support.GenericMessage
import org.springframework.messaging.support.MessageBuilder
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.ProcessPostingBalanceService
import java.util.UUID
import java.util.concurrent.CompletableFuture

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

  val fakeAckCallback = object : AcknowledgementCallback<String> {
    override fun onAcknowledge(message: Message<String>): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

    override fun onAcknowledge(messages: Collection<Message<String>>): CompletableFuture<Void> = CompletableFuture.completedFuture(null)
  }

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
    val payload = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountId",
        "source" : "$source",
        "chainPosition" : $chainPosition
      }
    """.trimIndent()

    val message = MessageBuilder
      .withPayload(payload)
      .setHeader("AcknowledgementCallback", fakeAckCallback)
      .build()

    calculatedBalanceEventListener.handleEvents(listOf(message))

    verify(processPostingBalanceService).processBalance(accountId)
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

    assertThatThrownBy {
      calculatedBalanceEventListener.handleEvents(
        listOf(GenericMessage(message)),
      )
    }.isInstanceOf(RuntimeException::class.java)

    verify(processPostingBalanceService).processBalance(accountId)

    val logList = listAppender.list
    assertThat(logList).hasSize(1)

    val logEvent = logList[0]
    assertThat(logEvent.level).isEqualTo(Level.ERROR)
    assertThat(logEvent.formattedMessage).contains("Failed to process balance calculation")
    assertThat(logEvent.formattedMessage).contains(exceptionMessage)
  }

  @Test
  fun `should not process messages from the same accountId twice`() {
    val postingId = UUID.randomUUID()
    val accountIdOne = UUID.randomUUID()
    val accountIdTwo = UUID.randomUUID()
    val source = "test"
    val chainPosition = 0L
    val payloadOne = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountIdOne",
        "source" : "$source",
        "chainPosition" : $chainPosition
      }
    """.trimIndent()
    val payloadTwo = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountIdTwo",
        "source" : "$source",
        "chainPosition" : $chainPosition
      }
    """.trimIndent()

    val messageOne = MessageBuilder
      .withPayload(payloadOne)
      .setHeader("AcknowledgementCallback", fakeAckCallback)
      .build()
    val messageTwo = MessageBuilder
      .withPayload(payloadOne)
      .setHeader("AcknowledgementCallback", fakeAckCallback)
      .build()
    val messageThree = MessageBuilder
      .withPayload(payloadTwo)
      .setHeader("AcknowledgementCallback", fakeAckCallback)
      .build()

    calculatedBalanceEventListener.handleEvents(listOf(messageOne, messageTwo, messageThree))

    verify(processPostingBalanceService, times(1)).processBalance(accountIdOne)
    verify(processPostingBalanceService, times(1)).processBalance(accountIdTwo)
  }
}
