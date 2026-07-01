package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.LogSqsCalculatedBalances
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.LogSqsCalculatedBalancesRepository

@Service
class LogSqsCalculatedBalanceService(
  private val logSqsCalculatedBalancesRepository: LogSqsCalculatedBalancesRepository,
) {
  fun save(log: LogSqsCalculatedBalances) {
    logSqsCalculatedBalancesRepository.save(log)
  }
}
