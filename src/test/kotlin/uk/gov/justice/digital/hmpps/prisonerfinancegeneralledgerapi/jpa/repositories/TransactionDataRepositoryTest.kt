package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant
import java.util.UUID

@DataJpaTest
@Import(RepoTestHelpers::class)
class TransactionDataRepositoryTest @Autowired constructor(
  @Autowired val transactionDataRepository: TransactionDataRepository,
  private val repoTestHelpers: RepoTestHelpers,
  dataRepository: TransactionDataRepository,
) {

  @Autowired
  lateinit var entityManager: EntityManager

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
  inner class FindTransactionsByIds {
    @Test
    fun `should return an empty list when there are no matching transaction Ids`() {
      val page = PageRequest.of(0, 5)
      val transactions = transactionDataRepository.findTransactionsByIds(
        listOf(UUID.randomUUID()),
        page,
      )
      assertThat(transactions.content).isEmpty()
    }

    @Test
    fun `should return a list of transactions ordered by timestamp, and entry sequence, and id`() {
      val page = PageRequest.of(0, 9999)

      val cash = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spends = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      val transactionIds = mutableListOf<UUID>()

      val timestamp = Instant.now()

      val transactionNowEntry1 = repoTestHelpers.createOneToOneTransaction(
        1,
        Instant.now(),
        cash,
        spends,
        transactionTimeStamp = timestamp,
        transactionEntrySequence = 1,
      )
      transactionIds.add(transactionNowEntry1.id)

      val transactionInThePast = repoTestHelpers.createOneToOneTransaction(
        1,
        Instant.now(),
        cash,
        spends,
        transactionTimeStamp = timestamp.minusSeconds(60),
      )
      transactionIds.add(transactionInThePast.id)

      val transactionInTheFuture = repoTestHelpers.createOneToOneTransaction(
        1,
        Instant.now(),
        cash,
        spends,
        transactionTimeStamp = timestamp.plusSeconds(60),
      )
      transactionIds.add(transactionInTheFuture.id)

      val transactionNowEntry2 = repoTestHelpers.createOneToOneTransaction(
        1,
        Instant.now(),
        cash,
        spends,
        transactionTimeStamp = timestamp,
        transactionEntrySequence = 2,
      )
      transactionIds.add(transactionNowEntry2.id)

      // testing for id fallback when entry sequences and timestamps are the same
      val transactionEntrySequenceZeroOne = repoTestHelpers.createOneToOneTransaction(
        2,
        Instant.now(),
        cash,
        spends,
        transactionTimeStamp = timestamp.plusSeconds(120),
        transactionEntrySequence = 0,
      )
      transactionIds.add(transactionEntrySequenceZeroOne.id)

      val transactionEntrySequenceZeroTwo = repoTestHelpers.createOneToOneTransaction(
        2,
        Instant.now(),
        cash,
        spends,
        transactionTimeStamp = timestamp.plusSeconds(120),
        transactionEntrySequence = 0,
      )
      transactionIds.add(transactionEntrySequenceZeroTwo.id)

      val lowestEntryZeroId = listOf(transactionEntrySequenceZeroOne, transactionEntrySequenceZeroTwo).map { it.id.toString() }.min()
      val higherEntryZeroId = listOf(transactionEntrySequenceZeroOne, transactionEntrySequenceZeroTwo).map { it.id.toString() }.max()

      val retrievedTransactions = transactionDataRepository.findTransactionsByIds(transactionIds, page)
      assertThat(retrievedTransactions.content).hasSize(6)

      assertThat(retrievedTransactions.content[0].id).isEqualTo(UUID.fromString(higherEntryZeroId))
      assertThat(retrievedTransactions.content[1].id).isEqualTo(UUID.fromString(lowestEntryZeroId))

      assertThat(retrievedTransactions.content[2].id).isEqualTo(transactionInTheFuture.id)
      assertThat(retrievedTransactions.content[3].id).isEqualTo(transactionNowEntry2.id)
      assertThat(retrievedTransactions.content[4].id).isEqualTo(transactionNowEntry1.id)
      assertThat(retrievedTransactions.content[5].id).isEqualTo(transactionInThePast.id)
    }

    @Test
    fun `should return a list of transactions for matching Ids`() {
      val page = PageRequest.of(0, 5)

      val cash = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spends = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      val transactionIds = mutableListOf<UUID>()

      repeat(3) {
        val transaction = repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends, transactionTimeStamp = Instant.now())
        transactionIds.add(transaction.id)
      }

      val retrievedTransactions = transactionDataRepository.findTransactionsByIds(transactionIds, page)
      assertThat(retrievedTransactions.content).hasSize(3)
    }

    @Test
    fun `should return a first 5 transaction for matching Ids`() {
      val page = PageRequest.of(0, 5)

      val cash = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spends = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      val transactionIds = mutableListOf<UUID>()

      repeat(10) {
        val transaction = repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends, transactionTimeStamp = Instant.now())
        transactionIds.add(transaction.id)
      }

      val retrievedTransactions = transactionDataRepository.findTransactionsByIds(transactionIds, page)
      assertThat(retrievedTransactions.content).hasSize(5)
    }
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

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends, transactionTimeStamp = Instant.now())

      val transactions = transactionDataRepository.findTransactionsByAccountId(testAccount.id)

      assertThat(transactions).hasSize(1)
      assertThat(transactions[0].postings.size).isEqualTo(2)
    }

    @Test
    fun `should return transactions only associated with this account`() {
      val cashA = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spendsA = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cashA, spendsA, transactionTimeStamp = Instant.now())

      val accountB = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_2")
      val cashB = repoTestHelpers.createSubAccount("CASH", accountB)
      val spendsB = repoTestHelpers.createSubAccount("SPENDS", accountB)

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cashB, spendsB, transactionTimeStamp = Instant.now())

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
      val tx1 = repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends, transactionTimeStamp = Instant.now().minusSeconds(1000))
      val tx2 = repoTestHelpers.createOneToOneTransaction(1, Instant.now(), cash, spends, transactionTimeStamp = Instant.now())

      val transactions = transactionDataRepository.findTransactionsByAccountId(testAccount.id)

      assertThat(transactions).hasSize(2)
      assertThat(transactions[0].id).isEqualTo(tx2.id)
      assertThat(transactions[1].id).isEqualTo(tx1.id)
    }

    @Test
    fun `postings are guaranteed to be ordered by entrySequence DESC from query`() {
      val cash = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spends = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now(),
        debitSubAccount = cash,
        creditSubAccount = spends,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
        transactionTimeStamp = Instant.now(),
      )

      // make sure in memory changes are persisted and clear memory cache to make sure db is used
      entityManager.flush()
      entityManager.clear()

      val loadedTransaction = transactionDataRepository.findTransactionsByAccountId(testAccount.id)
      val postings = loadedTransaction[0].postings

      val entrySequenceList = postings.map { it.entrySequence }

      assertThat(entrySequenceList).containsExactly(2, 1)
    }

    @Test
    fun `transactions are guaranteed to be ordered by entrySequence DESC from query`() {
      val cash = repoTestHelpers.createSubAccount("CASH", testAccount)
      val spends = repoTestHelpers.createSubAccount("SPENDS", testAccount)

      val transactionTimeStamp = Instant.now()

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimeStamp,
        debitSubAccount = cash,
        creditSubAccount = spends,
        transactionEntrySequence = 1,
        transactionTimeStamp = transactionTimeStamp,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
      )

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimeStamp,
        debitSubAccount = cash,
        creditSubAccount = spends,
        transactionEntrySequence = 2,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
        transactionTimeStamp = transactionTimeStamp,
      )

      // make sure in memory changes are persisted and clear memory cache to make sure db is used
      entityManager.flush()
      entityManager.clear()

      val loadedTransaction = transactionDataRepository.findTransactionsByAccountId(testAccount.id)

      val entrySequenceList = loadedTransaction.map { it.entrySequence }

      assertThat(entrySequenceList).containsExactly(2, 1)
    }
  }
}
