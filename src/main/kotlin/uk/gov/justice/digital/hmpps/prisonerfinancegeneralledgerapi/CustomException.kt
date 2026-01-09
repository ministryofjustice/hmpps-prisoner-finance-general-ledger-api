package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi

import org.springframework.http.HttpStatus

class CustomException constructor(message: String, val status: HttpStatus) : Exception(message)
