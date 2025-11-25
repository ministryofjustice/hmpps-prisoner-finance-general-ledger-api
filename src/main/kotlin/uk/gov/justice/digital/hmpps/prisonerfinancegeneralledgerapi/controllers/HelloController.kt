package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.springframework.http.ResponseEntity

class HelloController {
  fun hello(): ResponseEntity<String> = ResponseEntity.ok().body("Hello World!")
}
