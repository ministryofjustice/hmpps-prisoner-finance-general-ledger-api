package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant
import java.util.UUID

@DataJpaTest
@Import(RepoTestHelpers::class)
class PostingBalanceDataRepositoryTest @Autowired constructor(
  val postingBalanceDataRepository: PostingBalanceDataRepository,
  val repoTestHelpers: RepoTestHelpers,
) {
  @Nested
  inner class GetPreviousPostingBalancesByAccount {
    @Test
    fun `Should return an empty list if there are no previous postings`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
        postingId = UUID.randomUUID(),
        accountId = parentAccount.id,
        transactionTimestamp = Instant.now(),
        transactionEntrySequence = 1,
        postingEntrySequence = 1,
      )
      assertThat(previousBalance).hasSize(0)
    }

    @Test
    fun `Should return last posting balance before the timestamp provided`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)

      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val transactionTimestamp = Instant.now()
      val subAccount1Balance = 1000L
      val postingBalances = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      )

      val transactionInTheFuture = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp.plusSeconds(1000),
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      ).first.postingEntity.transactionEntity

      repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp.minusSeconds(1000),
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      )

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
        postingId = transactionInTheFuture.postings.first().id,
        accountId = parentAccount.id,
        transactionTimestamp = transactionInTheFuture.timestamp,
        transactionEntrySequence = 1,
        postingEntrySequence = 1,
      )
      assertThat(previousBalance).hasSize(1)
      assertThat(previousBalance.first().id).isEqualTo(postingBalances.first.id)
    }

    @Test
    fun `Should return last posting balance before by transaction timestamp, transaction entrySequence`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)

      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val transactionTimestamp = Instant.now()

      val subAccount1Balance = 2235L

      val lastPosting = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 2235L,
        transactionEntrySequence = 3,
        postingsEntrySequences = Pair(5, 6),
      ).first.postingEntity

      val previousRecord = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 2235L,
        transactionEntrySequence = 2,
        postingsEntrySequences = Pair(3, 4),
      )

      repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1234,
        subAccountBalance1 = 1235,
        subAccountBalance2 = 1235,
        transactionEntrySequence = 1,
        postingsEntrySequences = Pair(1, 2),
      )

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
        postingId = lastPosting.id,
        accountId = parentAccount.id,
        transactionTimestamp = transactionTimestamp,
        transactionEntrySequence = 3,
        postingEntrySequence = 5,
      )

      assertThat(previousBalance).hasSize(1)
      assertThat(previousBalance.first().id).isEqualTo(previousRecord.first.id)
    }

    @Test
    fun `Should correctly order posting balances by transaction timestamp, transaction entrySequence, and posting entrySequence`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)

      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val transactionTimestamp = Instant.now()

      val transactionAmount = 1L

      val transactionEntity = TransactionEntity(
        id = UUID.randomUUID(),
        reference = "TEST_REF",
        amount = transactionAmount,
        timestamp = transactionTimestamp,
        postings = mutableListOf(),
        entrySequence = 1,
        description = "CANTEEN Transactions",
      )

      val postingEntity1 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = subAccountCash,
        transactionEntity = transactionEntity,
        entrySequence = 1,
      )
      val postingBalance1 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity1,
        totalSubAccountBalance = 1,
      )

      val postingEntity2 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = prisonCanteenSubAccount,
        transactionEntity = transactionEntity,
        entrySequence = 2,
      )
      val postingBalance2 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity2,
        totalSubAccountBalance = 1,
      )

      val postingEntity3 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = subAccountCash,
        transactionEntity = transactionEntity,
        entrySequence = 3,
      )
      val postingBalance3 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity3,
        totalSubAccountBalance = 1,
      )

      val postingEntity4 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = prisonCanteenSubAccount,
        transactionEntity = transactionEntity,
        entrySequence = 4,
      )
      val postingBalance4 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity4,
        totalSubAccountBalance = 1,
      )

      transactionEntity.postings.add(postingEntity1)
      transactionEntity.postings.add(postingEntity2)
      transactionEntity.postings.add(postingEntity3)
      transactionEntity.postings.add(postingEntity4)

      repoTestHelpers.persist(transactionEntity)

      repoTestHelpers.persist(postingEntity1)
      repoTestHelpers.persist(postingEntity2)
      repoTestHelpers.persist(postingEntity3)
      repoTestHelpers.persist(postingEntity4)

      repoTestHelpers.persist(postingBalance1)
      repoTestHelpers.persist(postingBalance2)
      repoTestHelpers.persist(postingBalance3)
      repoTestHelpers.persist(postingBalance4)

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
        postingId = postingEntity3.id,
        accountId = parentAccount.id,
        transactionTimestamp = transactionTimestamp,
        transactionEntrySequence = 1,
        postingEntrySequence = 3,
      )
      assertThat(previousBalance).hasSize(1)
      assertThat(previousBalance.first().id).isEqualTo(postingBalance1.id)
    }

    @Test
    fun `Should return last posting balance before by id when posting entrySequence and transactionEntrySequence are zero`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)

      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val transactionTimestamp = Instant.now()

      val subAccount1Balance = 2235L

      val postingOne = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 2235L,
        transactionEntrySequence = 0,
        postingsEntrySequences = Pair(0, 0),
      ).first.postingEntity

      val postingTwo = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1234,
        subAccountBalance1 = 1235,
        subAccountBalance2 = 1235,
        transactionEntrySequence = 0,
        postingsEntrySequences = Pair(0, 0),
      ).first.postingEntity

      val lastPostingId = UUID.fromString(listOf(postingOne.id, postingTwo.id).maxOf { it.toString() })
      val firstPostingId = UUID.fromString(listOf(postingOne.id, postingTwo.id).minOf { it.toString() })

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
        postingId = lastPostingId,
        accountId = parentAccount.id,
        transactionTimestamp = transactionTimestamp,
        transactionEntrySequence = 0,
        postingEntrySequence = 0,
      )

      assertThat(previousBalance).hasSize(1)
      assertThat(previousBalance.first().postingEntity.id).isEqualTo(firstPostingId)
    }

    @Test
    fun `Should return previous posting balance for each sub account`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)
      val subAccountSpends = repoTestHelpers.createSubAccount(ref = "SPENDS", account = parentAccount)

      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val transactionTimestamp = Instant.now()
      val subAccount1Balance = 1000L
      val postingBalancesCash = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountCash,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = Instant.now(),
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      )

      val postingBalancesSpends = repoTestHelpers.createOneToOneTransactionPostingBalances(
        subAccount1 = subAccountSpends,
        subAccount2 = prisonCanteenSubAccount,
        transactionTimeStamp = Instant.now(),
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      )

      val previousBalances = postingBalanceDataRepository.getPreviousPostingBalancesByAccount(
        postingId = UUID.randomUUID(),
        accountId = parentAccount.id,
        transactionTimestamp = transactionTimestamp.plusSeconds(1),
        transactionEntrySequence = 1,
        postingEntrySequence = 1,
      )
      assertThat(previousBalances).hasSize(2)

      val cashBalance = previousBalances.first { pb -> pb.postingEntity.subAccountEntity.id == subAccountCash.id }
      val spendsBalance = previousBalances.first { pb -> pb.postingEntity.subAccountEntity.id == subAccountSpends.id }

      assertThat(cashBalance.id).isEqualTo(postingBalancesCash.first.id)
      assertThat(spendsBalance.id).isEqualTo(postingBalancesSpends.first.id)
    }
  }

  @Nested
  inner class DeleteFromTimestampByAccountId {

    @Test
    fun `Should delete all existing posting balances for a parentAccount from the timestamp`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)

      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val transactionTimestamp = Instant.now()

      var incrementSeconds = 10L
      val subAccount1Balance = 1000L
      repeat(10) {
        repoTestHelpers.createOneToOneTransactionPostingBalances(
          subAccount1 = subAccountCash,
          subAccount2 = prisonCanteenSubAccount,
          transactionTimeStamp = transactionTimestamp.plusSeconds(incrementSeconds),
          transactionAmount = 1000,
          subAccountBalance1 = subAccount1Balance,
          subAccountBalance2 = 1000,
        )
        incrementSeconds += 10L
      }

      val afterSecondTransaction = transactionTimestamp.plusSeconds(21)

      postingBalanceDataRepository.deleteFromTimestampByAccountId(
        parentAccount.id,
        afterSecondTransaction,
      )

      val pbs = postingBalanceDataRepository.findAll()

      val prisonerPbs = pbs.filter { it.postingEntity.subAccountEntity.parentAccountEntity.id == parentAccount.id }
      assertThat(prisonerPbs).hasSize(2)
      assertThat(prisonerPbs.firstOrNull { it.postingEntity.transactionEntity.timestamp == transactionTimestamp.plusSeconds(10) }).isNotNull()
      assertThat(prisonerPbs.firstOrNull { it.postingEntity.transactionEntity.timestamp == transactionTimestamp.plusSeconds(20) }).isNotNull()

      val prison = pbs.filter { it.postingEntity.subAccountEntity.parentAccountEntity.id == prisonAccount.id }
      assertThat(prison).hasSize(10)
    }
  }
}
