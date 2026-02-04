package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.IdempotencyEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.IdempotencyKeyDataRepository
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class IdempotencyInterceptorTest {
  @Mock
  lateinit var idempotencyKeyDataRepositoryMock: IdempotencyKeyDataRepository

  @InjectMocks
  lateinit var idempotencyInterceptor: IdempotencyInterceptor

  val mockRequest = MockHttpServletRequest()
  val mockResponse = MockHttpServletResponse()
  val mockHandler = Any()

  @Test
  fun `Should return false if an invalid idempotency key is provided`() {
    mockRequest.addHeader("Idempotency-Key", "")
    val ok = idempotencyInterceptor.preHandle(mockRequest, mockResponse, mockHandler)
    assertThat(ok).isFalse()
  }

  @Test
  fun `Should return true if idempotency key is provided and doesnt already exist`() {
    val idempotencyKey = UUID.randomUUID()
    whenever(idempotencyKeyDataRepositoryMock.getIdempotencyEntityByIdempotencyKey(idempotencyKey)).thenReturn(null)
    mockRequest.addHeader("Idempotency-Key", idempotencyKey.toString())
    val ok = idempotencyInterceptor.preHandle(mockRequest, mockResponse, mockHandler)
    assertThat(ok).isTrue()
  }

  @Test
  fun `Should return false is idempotency key already exists`() {
    val idempotencyKey = UUID.randomUUID()
    val idempotencyEntity = IdempotencyEntity(idempotencyKey)
    whenever(idempotencyKeyDataRepositoryMock.getIdempotencyEntityByIdempotencyKey(idempotencyKey)).thenReturn(idempotencyEntity)
    mockRequest.addHeader("Idempotency-Key", idempotencyKey.toString())

    val ok = idempotencyInterceptor.preHandle(mockRequest, mockResponse, mockHandler)
    assertThat(ok).isFalse()
  }
}
