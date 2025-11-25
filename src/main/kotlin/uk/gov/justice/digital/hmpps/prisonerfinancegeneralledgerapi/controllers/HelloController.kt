package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.NON_SENSITIVE_READ

@RestController
@RequestMapping("/hello")
class HelloController {

  @PreAuthorize("hasAnyAuthority('$NON_SENSITIVE_READ')")
  @GetMapping
  fun hello(): ResponseEntity<String> = ResponseEntity.ok().body("Hello World!")
}
