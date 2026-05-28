package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs.SqsQueues
import java.time.Instant

class CalculatedBalanceIntegrationTest(
  @Autowired
  val postingBalanceDataRepository: PostingBalanceDataRepository,
) : IntegrationTestBase() {
  @Transactional
  @BeforeEach
  fun resetDB() {
    integrationTestHelpers.clearDB()

    accountPrisoner = integrationTestHelpers.createAccount("ABCXZ123", AccountType.PRISONER)
    subAccountPrisonerCash = integrationTestHelpers.createSubAccount(accountPrisoner.id, "CASH")
    subAccountPrisonerSpends = integrationTestHelpers.createSubAccount(accountPrisoner.id, "SPENDS")

    accountPrison = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)
    subAccountPrisonCanteen = integrationTestHelpers.createSubAccount(accountPrison.id, "CANT:1001")
  }

  lateinit var accountPrisoner: AccountResponse
  lateinit var subAccountPrisonerCash: SubAccountResponse
  lateinit var subAccountPrisonerSpends: SubAccountResponse
  lateinit var accountPrison: AccountResponse
  lateinit var subAccountPrisonCanteen: SubAccountResponse

  @Nested
  inner class AccountCalculatedBalance {

    @Test
    fun `Should calculate balances when multiple transactions are posted`() {
      val amountFirst = 77L
      val amountSecond = 27L

      // txn 1
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountFirst,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      // txn 2
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountSecond,
        debitSubAccountId = subAccountPrisonerCash.id,
        creditSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(2L, 1L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      val statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)

      assertThat(content[0].postingType).isEqualTo(PostingType.DR)
      assertThat(content[0].amount).isEqualTo(amountSecond)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst - amountSecond)
      assertThat(content[0].accountBalance).isEqualTo(amountFirst - amountSecond)

      assertThat(content[1].postingType).isEqualTo(PostingType.CR)
      assertThat(content[1].amount).isEqualTo(amountFirst)
      assertThat(content[1].subAccountBalance).isEqualTo(amountFirst)
      assertThat(content[1].accountBalance).isEqualTo(amountFirst)
    }

    @Test
    fun `Should calculate balances after a transaction is posted`() {
      val amount = 77L

      integrationTestHelpers.createOneToOneTransaction(
        amount = amount,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      val statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amount)
      assertThat(content[0].subAccountBalance).isEqualTo(amount)
      assertThat(content[0].accountBalance).isEqualTo(amount)
    }

    @Test
    fun `Should calculate balances after a transaction is posted and account for previous statement balance`() {
      val amount = 77L
      val statementBalanceAmount = 100L

      val statementBalanceTimestamp = Instant.now().minusSeconds(120)
      integrationTestHelpers.createStatementBalance(
        subAccountId = subAccountPrisonerCash.id,
        amount = statementBalanceAmount,
        timestamp = statementBalanceTimestamp,
      )

      integrationTestHelpers.createOneToOneTransaction(
        amount = amount,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      val statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amount)
      assertThat(content[0].subAccountBalance).isEqualTo(statementBalanceAmount + amount)
      assertThat(content[0].accountBalance).isEqualTo(statementBalanceAmount + amount)
    }

    @Test
    fun `Should re-calculate balances when a posting is created in the past`() {
      // txn 1
      val timestampFirst = Instant.now()
      val amountFirst = 77L
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountFirst,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = timestampFirst,
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst)

      // txn 2 in the past
      val timestampSecond = timestampFirst.minusSeconds(120)
      val amountSecond = 27L
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountSecond,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = timestampSecond,
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst + amountSecond)
      assertThat(content[0].accountBalance).isEqualTo(amountFirst + amountSecond)

      assertThat(content[1].amount).isEqualTo(amountSecond)
      assertThat(content[1].subAccountBalance).isEqualTo(amountSecond)
      assertThat(content[1].accountBalance).isEqualTo(amountSecond)
    }

    @Test
    fun `Should re-calculate balances when a statement balance is created in the past`() {
      val amountFirst = 77L
      val amountStatementBalance = 27L

      // TXN
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountFirst,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountFirst)
      assertThat(content[0].accountBalance).isEqualTo(amountFirst)

      // Insert statement balance
      val statementBalanceTimestamp = Instant.now().minusSeconds(120)
      integrationTestHelpers.createStatementBalance(
        subAccountId = subAccountPrisonerCash.id,
        amount = amountStatementBalance,
        timestamp = statementBalanceTimestamp,
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountFirst)
      assertThat(content[0].subAccountBalance).isEqualTo(amountStatementBalance + amountFirst)
      assertThat(content[0].accountBalance).isEqualTo(amountStatementBalance + amountFirst)
    }

    @Test
    fun `Should re-calculate all sub-account balances when a statement balance is created in the past for another sub-account`() {
      //  Cash first total is the posting amount
      //  Spends second total is the posting amount plus cash subAccount
      //
      //  Then I insert a migration for Spends before Cash
      //  I want to see Cash total balance update to include the new Spend balance

      // TXN CASH

      val transactionCashTimestamp = Instant.now()
      val amountCashTx = 10L
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountCashTx,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = transactionCashTimestamp,
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      // TXN SPENDS
      val amountSpendsTx = 22L
      val transactionSpendsTimestamp = transactionCashTimestamp.plusSeconds(10)
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountSpendsTx,
        creditSubAccountId = subAccountPrisonerSpends.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = transactionSpendsTimestamp,
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
      )

      var content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].subAccount.id).isEqualTo(subAccountPrisonerSpends.id)
      assertThat(content[0].amount).isEqualTo(amountSpendsTx)
      assertThat(content[0].subAccountBalance).isEqualTo(amountSpendsTx)
      assertThat(content[0].accountBalance).isEqualTo(amountSpendsTx + amountCashTx)

      assertThat(content[1].subAccount.id).isEqualTo(subAccountPrisonerCash.id)
      assertThat(content[1].amount).isEqualTo(amountCashTx)
      assertThat(content[1].subAccountBalance).isEqualTo(amountCashTx)
      assertThat(content[1].accountBalance).isEqualTo(amountCashTx)

      // Insert statement balance
      val amountStatementBalance = 27L
      val statementBalanceTimestamp = transactionCashTimestamp.minusSeconds(60)
      integrationTestHelpers.createStatementBalance(
        subAccountId = subAccountPrisonerSpends.id,
        amount = amountStatementBalance,
        timestamp = statementBalanceTimestamp,
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
      )

      content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].subAccount.id).isEqualTo(subAccountPrisonerSpends.id)
      assertThat(content[0].amount).isEqualTo(amountSpendsTx)
      assertThat(content[0].subAccountBalance).isEqualTo(amountSpendsTx + amountStatementBalance)
      assertThat(content[0].accountBalance).isEqualTo(amountSpendsTx + amountCashTx + amountStatementBalance)

      assertThat(content[1].subAccount.id).isEqualTo(subAccountPrisonerCash.id)
      assertThat(content[1].amount).isEqualTo(amountCashTx)
      assertThat(content[1].subAccountBalance).isEqualTo(amountCashTx)
      assertThat(content[1].accountBalance).isEqualTo(amountCashTx + amountStatementBalance)
    }

    @Test
    fun `Should not change total account balance when a subAccount transfer is posted`() {
      val amountCanteenTransaction = 77L

      integrationTestHelpers.createOneToOneTransaction(
        amount = amountCanteenTransaction,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      var statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      var content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].amount).isEqualTo(amountCanteenTransaction)
      assertThat(content[0].subAccountBalance).isEqualTo(amountCanteenTransaction)
      assertThat(content[0].accountBalance).isEqualTo(amountCanteenTransaction)

      // sub acc transfer

      val amountSpendsToCash = 22L

      integrationTestHelpers.createOneToOneTransaction(
        amount = amountSpendsToCash,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonerSpends.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(2L, 1L),
      )

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].postingType).isEqualTo(PostingType.CR)
      assertThat(content[0].amount).isEqualTo(amountSpendsToCash)
      assertThat(content[0].subAccountBalance).isEqualTo(amountCanteenTransaction + amountSpendsToCash)
      assertThat(content[0].accountBalance).isEqualTo(amountCanteenTransaction)

      assertThat(content[1].postingType).isEqualTo(PostingType.CR)
      assertThat(content[1].amount).isEqualTo(amountCanteenTransaction)
      assertThat(content[1].subAccountBalance).isEqualTo(amountCanteenTransaction)
      assertThat(content[1].accountBalance).isEqualTo(amountCanteenTransaction)
    }
  }

  @Nested
  inner class MigrateBalancesTest {

    @Test
    fun `should migrate the accounts balances`() {
      val amountFirst = 77L
      val amountSecond = 27L

      // txn 1
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountFirst,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      // txn 2
      integrationTestHelpers.createOneToOneTransaction(
        amount = amountSecond,
        creditSubAccountId = subAccountPrisonerCash.id,
        debitSubAccountId = subAccountPrisonCanteen.id,
        transactionReference = "test",
        timestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      // waiting for sqs to empty and then clearing balances
      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)
      postingBalanceDataRepository.deleteAllInBatch()

      var statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      var content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].subAccountBalance).isNull()
      assertThat(content[0].accountBalance).isNull()

      assertThat(content[1].subAccountBalance).isNull()
      assertThat(content[1].accountBalance).isNull()

      webTestClient.post()
        .uri("/migrate/subAccountBalances")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .returnResult()

      integrationTestHelpers.waitUntilEmpty(SqsQueues.CALCULATED_BALANCE_QUEUE_ID, hmppsQueueService)

      statementEntryResponse = integrationTestHelpers.getStatementEntry(
        accountId = accountPrisoner.id,
        subAccountId = subAccountPrisonerCash.id,
      )

      content = statementEntryResponse.content

      assertThat(content[0].amount).isEqualTo(amountSecond)
      assertThat(content[0].subAccountBalance).isEqualTo(amountSecond + amountFirst)
      assertThat(content[0].accountBalance).isEqualTo(amountSecond + amountFirst)

      assertThat(content[1].amount).isEqualTo(amountFirst)
      assertThat(content[1].subAccountBalance).isEqualTo(amountFirst)
      assertThat(content[1].accountBalance).isEqualTo(amountFirst)
    }
  }
}
