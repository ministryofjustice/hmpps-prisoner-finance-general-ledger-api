package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant

@DataJpaTest
@Import(RepoTestHelpers::class)
class PostingBalanceDataRepositoryTest @Autowired constructor(
  val postingBalanceDataRepository: PostingBalanceDataRepository,
  val repoTestHelpers: RepoTestHelpers,
) {

  @Nested
  inner class GetSubAccountBalanceOrDefault {
    @Test
    fun `Should return null if there are no previous postings or migrations`() {
      val parentAccount = repoTestHelpers.createAccount(ref="ABC123ZX")
      val subAccount = repoTestHelpers.createSubAccount(ref="CASH", account=parentAccount)

      val previousBalance = postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccountId = subAccount.id,
        transactionTimestamp = Instant.now()
      )
      assertThat(previousBalance).isEqualTo(null)
    }

    @Test
    fun `Should return last posting balance before the timestamp provided when there is one`() {
      val parentAccount = repoTestHelpers.createAccount(ref="ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
      val subAccountSpends= repoTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

      val transactionTimestamp = Instant.now()
      val subAccount1Balance = 1000L
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp,
        transactionAmount=1000,
        subAccountBalance1=subAccount1Balance,
        subAccountBalance2=1000,
        totalAccountBalance=1000
      )


      val previousBalance = postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccountId = subAccountCash.id,
        transactionTimestamp = Instant.now()
      )
      assertThat(previousBalance?.totalSubAccountBalance).isEqualTo(subAccount1Balance)
    }

    @Test
    fun `Should correctly order posting balances by transaction timestamp, transaction entrySequence, and posting entrySequence`(){
      val parentAccount = repoTestHelpers.createAccount(ref="ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
      val subAccountSpends= repoTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

      val transactionTimestamp = Instant.now()

      val subAccount1Balance = 2235L

      // last record
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp,
        transactionAmount=1000,
        subAccountBalance1=subAccount1Balance,
        subAccountBalance2=2235L,
        totalAccountBalance=99999L,
        transactionEntrySequence=2,
        postingsEntrySequences=Pair(3,4),
      )

      // previous record same timestamp
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp,
        transactionAmount=1234,
        subAccountBalance1= 1235,
        subAccountBalance2=1235,
        totalAccountBalance=99999L,
        transactionEntrySequence=2,
        postingsEntrySequences=Pair(1,2),
      )

      // record in the past
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp.minusSeconds(123),
        transactionAmount=1,
        subAccountBalance1=1,
        subAccountBalance2=1,
        totalAccountBalance=99999L,
        transactionEntrySequence=1,
      )

      val previousBalance = postingBalanceDataRepository.getSubAccountBalanceOrDefault(
        subAccountId = subAccountCash.id,
        transactionTimestamp = Instant.now()
      )
      assertThat(previousBalance?.totalSubAccountBalance).isEqualTo(subAccount1Balance)
    }
  }

  @Nested
  inner class GetLatestAccountBalanceOrDefault {
    @Test
    fun `Should return null if there are no previous postings or migrations`() {
      val parentAccount = repoTestHelpers.createAccount(ref="ABC123ZX")

      val previousBalance = postingBalanceDataRepository.getLatestAccountBalance(
        parentAccountId = parentAccount.id,
        transactionTimestamp = Instant.now()
      )

      assertThat(previousBalance).isEqualTo(null)
    }

    @Test
    fun `Should return last posting balance before the timestamp provided when there is one`() {
      val parentAccount = repoTestHelpers.createAccount(ref="ABC123ZX")
      val subAccountCash = repoTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
      val subAccountSpends= repoTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

      val transactionTimestamp = Instant.now()
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp,
        transactionAmount=1000,
        subAccountBalance1=1000,
        subAccountBalance2=1000,
        totalAccountBalance=2000
      )

      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp.minusSeconds(60),
        transactionAmount=1000,
        subAccountBalance1=1000,
        subAccountBalance2=1000,
        totalAccountBalance=1000
      )


      val previousBalance = postingBalanceDataRepository.getLatestAccountBalance(
        parentAccountId = parentAccount.id,
        transactionTimestamp = Instant.now()
      )
      assertThat(previousBalance?.totalSubAccountBalance).isEqualTo(1000)
      assertThat(previousBalance?.totalAccountBalance).isEqualTo(2000)

    }

    @Test
    fun `Should correctly order posting balances by transaction timestamp, transaction entrySequence, and posting entrySequence`(){
      val parentAccount = repoTestHelpers.createAccount(ref="ABC123ZX")
      val prisonAccount = repoTestHelpers.createAccount(ref="LEI")
      val subAccountPrison = repoTestHelpers.createSubAccount(ref="1001:CANT", account=prisonAccount)
      val subAccountCash = repoTestHelpers.createSubAccount(ref="CASH", account=parentAccount)
      val subAccountSpends= repoTestHelpers.createSubAccount(ref="SPENDS", account=parentAccount)

      val transactionTimestamp = Instant.now()

      // last record
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp,
        transactionAmount=1000,
        subAccountBalance1=2236L,
        subAccountBalance2=2236L,
        totalAccountBalance=999L,
        transactionEntrySequence=2,
        postingsEntrySequences=Pair(3,4),
      )

      // previous record same timestamp
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp,
        transactionAmount=1236,
        subAccountBalance1=1236,
        subAccountBalance2=1236,
        totalAccountBalance=999L,
        transactionEntrySequence=2,
        postingsEntrySequences=Pair(1,2),
      )

      // record in the past
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountPrison,
        transactionTimeStamp=transactionTimestamp.minusSeconds(123),
        transactionAmount=1,
        subAccountBalance1=1,
        subAccountBalance2=1,
        totalAccountBalance=999L,
        transactionEntrySequence=1,
      )

      // record in the past
      repoTestHelpers.createPostingBalancePrisoner(
        subAccount1=subAccountCash,
        subAccount2=subAccountSpends,
        transactionTimeStamp=transactionTimestamp.minusSeconds(600),
        transactionAmount=1,
        subAccountBalance1=1,
        subAccountBalance2=1,
        totalAccountBalance=1000L,
        transactionEntrySequence=1,
      )

      val previousBalance = postingBalanceDataRepository.getLatestAccountBalance(
        parentAccountId = parentAccount.id,
        transactionTimestamp = Instant.now()
      )
      assertThat(previousBalance?.totalSubAccountBalance).isEqualTo(2236L)
      assertThat(previousBalance?.totalAccountBalance).isEqualTo(999L)
    }
  }
}