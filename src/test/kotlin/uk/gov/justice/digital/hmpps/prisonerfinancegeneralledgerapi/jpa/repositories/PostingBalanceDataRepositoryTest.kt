package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
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
  inner class GetPreviousPostingBalanceOrNull {
    @Test
    fun `Should return null if there are no previous postings`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalanceOrNull(
        postingId = UUID.randomUUID(),
        subAccountId = subAccount.id,
        transactionTimestamp = Instant.now(),
      )
      assertThat(previousBalance).isEqualTo(null)
    }

    @Test
    fun `Should return last posting balance before the timestamp provided`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)
      val subAccountSpends = repoTestHelpers.createSubAccount(ref = "SPENDS", account = parentAccount)

      val transactionTimestamp = Instant.now()
      val subAccount1Balance = 1000L
      val postingBalances = repoTestHelpers.createPostingBalancePrisoner(
        subAccount1 = subAccountCash,
        subAccount2 = subAccountSpends,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      )

      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1 = subAccountCash,
        subAccount2 = subAccountSpends,
        transactionTimeStamp = transactionTimestamp.plusSeconds(1000),
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      )

      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1 = subAccountCash,
        subAccount2 = subAccountSpends,
        transactionTimeStamp = transactionTimestamp.minusSeconds(1000),
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 1000,
      )

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalanceOrNull(
        postingId = UUID.randomUUID(),
        subAccountId = subAccountCash.id,
        transactionTimestamp = transactionTimestamp.plusSeconds(1),
      )
      assertThat(previousBalance?.id).isEqualTo(postingBalances.first.id)
    }

    @Test
    fun `Should correctly order posting balances by transaction timestamp, transaction entrySequence, and posting entrySequence`() {
      val parentAccount = repoTestHelpers.createAccount(ref = "ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref = "CASH", account = parentAccount)
      val subAccountSpends = repoTestHelpers.createSubAccount(ref = "SPENDS", account = parentAccount)

      val transactionTimestamp = Instant.now()

      val subAccount1Balance = 2235L

      // last record
      val mostRecentPostingBalance = repoTestHelpers.createPostingBalancePrisoner(
        subAccount1 = subAccountCash,
        subAccount2 = subAccountSpends,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1000,
        subAccountBalance1 = subAccount1Balance,
        subAccountBalance2 = 2235L,
        transactionEntrySequence = 2,
        postingsEntrySequences = Pair(3, 4),
      )

      // previous record same timestamp
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1 = subAccountCash,
        subAccount2 = subAccountSpends,
        transactionTimeStamp = transactionTimestamp,
        transactionAmount = 1234,
        subAccountBalance1 = 1235,
        subAccountBalance2 = 1235,
        transactionEntrySequence = 1,
        postingsEntrySequences = Pair(1, 2),
      )

      // record in the past
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1 = subAccountCash,
        subAccount2 = subAccountSpends,
        transactionTimeStamp = transactionTimestamp.minusSeconds(123),
        transactionAmount = 1,
        subAccountBalance1 = 1,
        subAccountBalance2 = 1,
        transactionEntrySequence = 1,
      )

      val previousBalance = postingBalanceDataRepository.getPreviousPostingBalanceOrNull(
        postingId = UUID.randomUUID(),
        subAccountId = subAccountCash.id,
        transactionTimestamp = Instant.now(),
      )
      assertThat(previousBalance?.id).isEqualTo(mostRecentPostingBalance.first.id)
    }
  }
}
