package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrisonerFinanceGeneralLedgerApi

fun main(args: Array<String>) {
  runApplication<PrisonerFinanceGeneralLedgerApi>(*args)
}
