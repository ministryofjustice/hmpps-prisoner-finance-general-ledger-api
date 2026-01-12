package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.CustomException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestControllerAdvice
class PrisonerFinanceGeneralLedgerApiExceptionHandler {

  @ExceptionHandler(CustomException::class)
  fun handleCustomException(e: CustomException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(e.status)
    .body(
      ErrorResponse(
        status = e.status.value(),
        userMessage = e.message,
        developerMessage = e.message,
      ),
    ).also { log.info("CustomExceptionThrown: {}", e.message) }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.BAD_REQUEST)
    .body(
      ErrorResponse(
        status = HttpStatus.BAD_REQUEST,
        userMessage = "Invalid parameter type failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("Invalid parameter type exception: {}", e.message) }

  @ExceptionHandler(value = [ValidationException::class, HttpMessageNotReadableException::class])
  fun handleValidationException(e: ValidationException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.BAD_REQUEST)
    .body(
      ErrorResponse(
        status = HttpStatus.BAD_REQUEST,
        userMessage = "Validation failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.NOT_FOUND)
    .body(
      ErrorResponse(
        status = HttpStatus.NOT_FOUND,
        userMessage = "No resource found failure: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.info("No resource found exception: {}", e.message) }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.FORBIDDEN)
    .body(
      ErrorResponse(
        status = HttpStatus.FORBIDDEN,
        userMessage = "Forbidden: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.debug("Forbidden (403) returned: {}", e.message) }

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(HttpStatus.INTERNAL_SERVER_ERROR)
    .body(
      ErrorResponse(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        userMessage = "Unexpected error: ${e.message}",
        developerMessage = e.message,
      ),
    ).also { log.error("Unexpected exception", e) }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
