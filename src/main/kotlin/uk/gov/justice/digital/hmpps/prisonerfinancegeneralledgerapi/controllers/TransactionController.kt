package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.controllers

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.TransactionService

@Tag(name = "Transaction Controller")
@RestController
class TransactionController (
  private val transactionService: TransactionService,
)
{

}