package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import jakarta.transaction.Transactional
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository

@Service
class MigrateService(
  val postingsDataRepository: PostingsDataRepository,
  val processPostingBalanceService: ProcessPostingBalanceService,
  val postingBalanceDataRepository: PostingBalanceDataRepository,
  val accountDataRepository: AccountDataRepository,
) {
  @Transactional
  fun migrateAllPostingBalances() {
    postingBalanceDataRepository.deleteAll()

    accountDataRepository.findAll().forEach { account ->
      try {
        processPostingBalanceService.processBalance(
          account.id,
        )
      } catch (e: Exception) {
        log.error("Failed process balances for account: ${account.id}\n${e.message}\n${e.stackTrace} ", e)
      }
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
