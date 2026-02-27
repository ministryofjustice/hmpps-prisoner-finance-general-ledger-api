package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant

@DataJpaTest
@Import(RepoTestHelpers::class)
class TransactionDataRepositoryTest @Autowired constructor(
  val transactionDataRepository: TransactionDataRepository,
  private val repoTestHelpers: RepoTestHelpers,
) {

  lateinit var testAccount: AccountEntity

  // TO-DO
  // 1 - Account has no transactions
  // 2 - Account has transactions for one sub-account
  // 3 - Account has transactions for multiple sub-accounts

  @BeforeEach
  fun setup() {
    testAccount = repoTestHelpers.createAccount("TEST_ACCOUNT_REF")
  }

  @Nested
  inner class FindTransactionsByAccountId {
    @Test
    fun `should return an empty list when there are no transactions`() {
      val transactions = transactionDataRepository.findTransactionsByAccountId(testAccount.id)
      assertThat(transactions).isEmpty()
    }

    @Test
    fun `should return transactions associated with this account`() {
      val cash = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spends = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends)

      val transactions = transactionDataRepository.findTransactionsByAccountId(testAccount.id)

      assertThat(transactions).hasSize(1)
      assertThat(transactions[0].postings.size).isEqualTo(2)
    }

    @Test
    fun `should return transactions only associated with this account`() {
      val cashA = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spendsA = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cashA, spendsA)

      val accountB = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_2")
      val cashB = repoTestHelpers.createSubAccount("CASH", accountB)
      val spendsB = repoTestHelpers.createSubAccount("SPENDS", accountB)

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cashB, spendsB)

      val transactions = transactionDataRepository.findTransactionsByAccountId(testAccount.id)

      assertThat(transactions).hasSize(1)
      assertThat(transactions[0].postings.size).isEqualTo(2)

      val correctSubAccountIds = listOf(cashA.id, spendsA.id)
      val postingsSubAccountIds = transactions[0].postings.map { posting -> posting.subAccountEntity.id }

      assertThat(correctSubAccountIds).containsAll(postingsSubAccountIds)
    }

    @Test
    fun `should return transactions ordered by timestamp descending`() {
      val cash = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spends = repoTestHelpers.createSubAccount("SPENDS", testAccount)
      val tx1 = repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends, Instant.now().minusSeconds(1000))
      val tx2 = repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends, Instant.now())

      val transactions = transactionDataRepository.findTransactionsByAccountId(testAccount.id)

      assertThat(transactions).hasSize(2)
      assertThat(transactions[0].id).isEqualTo(tx2.id)
    }
  }
}
