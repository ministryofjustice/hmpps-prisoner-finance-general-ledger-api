package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.security.Principal

class HelloControllerTest {

  @Test
  fun helloTest() {
    val userMock: Principal = Mockito.mock<Principal>()
    whenever(userMock.name).thenReturn("Dummy User")
    val response: ResponseEntity<String> = HelloController().hello(userMock)
    assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(response.body).isEqualTo("Hello Dummy User!")
  }
}
