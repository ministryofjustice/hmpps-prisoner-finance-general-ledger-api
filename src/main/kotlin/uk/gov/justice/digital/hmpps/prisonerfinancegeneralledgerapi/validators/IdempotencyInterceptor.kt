package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.IdempotencyKeyDataRepository
import java.util.UUID

@Component
class IdempotencyInterceptor : HandlerInterceptor {

  @Autowired
  private lateinit var idempotencyKeyDataRepository: IdempotencyKeyDataRepository

  override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
    try {
      val idempotencyKey = request.getHeader("Idempotency-Key")
      val parsedKey = UUID.fromString(idempotencyKey)

      val idempotencyEntity = idempotencyKeyDataRepository.getIdempotencyEntityByIdempotencyKey(parsedKey)
      if (idempotencyEntity != null) return false

      return true
    } catch (_: Exception) {
      return false
    }
  }
}
