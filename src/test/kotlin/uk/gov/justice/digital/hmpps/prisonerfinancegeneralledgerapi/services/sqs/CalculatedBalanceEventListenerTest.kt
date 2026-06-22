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
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.ProcessBalanceRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingBalanceService
import java.util.UUID

@TestPropertySource(
  properties = [
    "feature.balance-calculation.enabled=true",
  ],
)
@ExtendWith(MockitoExtension::class)
class CalculatedBalanceEventListenerTest {

  // This is unused in the test but required for the service
  @Spy
  var objectMapper: ObjectMapper = jacksonObjectMapper()
    .registerModule(JavaTimeModule())

  @Mock
  lateinit var postingBalanceService: PostingBalanceService

  @Mock
  lateinit var messagePublisher: MessagePublisher

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
    val message = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountId"
      }
    """.trimIndent()

    calculatedBalanceEventListener.handleEvents(message)

    verify(postingBalanceService).processBalance(postingId)
    verify(messagePublisher, never())
      .sendMessage(
        payloadDataClass = any<PayloadDataClass>(),
        queueId = any(),
        messageGroupId = any(),
      )
  }

  @Test
  fun `should request a new balance calculation nextPosting is returned`() {
    val postingId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val message = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountId"
      }
    """.trimIndent()

    val nextPosting = mock<ProcessBalanceRequest>()
    whenever(nextPosting.accountId).thenReturn(accountId)

    whenever { postingBalanceService.processBalance(postingId) }.thenReturn(nextPosting)

    calculatedBalanceEventListener.handleEvents(message)

    verify(postingBalanceService).processBalance(postingId)
    verify(messagePublisher).sendMessage(
      payloadDataClass = nextPosting,
      queueId = SqsQueues.CALCULATED_BALANCE_QUEUE_ID,
      messageGroupId = accountId.toString(),
    )
  }

  @Test
  fun `should log error when balance calculation fails`() {
    val postingId = UUID.randomUUID()
    val accountId = UUID.randomUUID()
    val message = """
      {
        "postingId" : "$postingId",
        "accountId" : "$accountId"
      }
    """.trimIndent()

    val exceptionMessage = "Test error"
    whenever { postingBalanceService.processBalance(postingId) }.thenThrow(RuntimeException(exceptionMessage))

    assertThatThrownBy { calculatedBalanceEventListener.handleEvents(message) }.isInstanceOf(RuntimeException::class.java)

    verify(postingBalanceService).processBalance(postingId)

    val logList = listAppender.list
    assertThat(logList).hasSize(1)

    val logEvent = logList[0]
    assertThat(logEvent.level).isEqualTo(Level.ERROR)
    assertThat(logEvent.formattedMessage).contains("Failed to process balance calculation")
    assertThat(logEvent.formattedMessage).contains(exceptionMessage)
  }
}
