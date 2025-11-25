package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration.controllers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers.HelloController

class HelloControllerTest {

  @Test
  fun helloTest() {
    val response: ResponseEntity<String> = HelloController().hello()
    assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(response.body).isEqualTo("Hello World!")
  }
}
