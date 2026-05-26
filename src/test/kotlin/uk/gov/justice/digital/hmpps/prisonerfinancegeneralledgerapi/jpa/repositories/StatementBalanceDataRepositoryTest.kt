package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@Import(RepoTestHelpers::class)
class StatementBalanceDataRepositoryTest @Autowired constructor(
  val statementBalanceDataRepository: StatementBalanceDataRepository,
  val repoTestHelpers: RepoTestHelpers,
) {

  lateinit var testAccountOne: AccountEntity
  lateinit var testAccountOneSubAccountOne: SubAccountEntity
  lateinit var testAccountOneSubAccountTwo: SubAccountEntity

  lateinit var testAccountTwo: AccountEntity
  lateinit var testAccountTwoSubAccountOne: SubAccountEntity

  @BeforeEach
  fun setup() {
    repoTestHelpers.clearDb()
    testAccountOne = repoTestHelpers.createAccount("TEST_ACCOUNT_REF")
    testAccountOneSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_1", testAccountOne)
    testAccountOneSubAccountTwo = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_2", testAccountOne)

    testAccountTwo = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_2")
    testAccountTwoSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_2", testAccountTwo)
  }

  @Nested
  inner class GetLatestStatementBalanceForSubAccount {

    @Test
    fun `Should return null if no statement balance exists for the sub account`() {
      val statementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(UUID.randomUUID())

      assertThat(statementBalance).isNull()
    }

    @Test
    fun `Should return a statement balance when one exists`() {
      val createdStatementBalance = repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testAccountOneSubAccountOne)

      val retrievedStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testAccountOneSubAccountOne.id)

      assertThat(retrievedStatementBalance?.amount).isEqualTo(createdStatementBalance.amount)
      assertThat(retrievedStatementBalance?.balanceDateTime).isEqualTo(createdStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalance?.subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }

    @Test
    fun `Should return the latest statement balance when multiple exist`() {
      val olderStatementBalance = repoTestHelpers.createStatementBalance(
        amount = 10,
        balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC),
        subAccount =
        testAccountOneSubAccountOne,
      )

      val retrievedStatementBalanceOne = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testAccountOneSubAccountOne.id)

      assertThat(retrievedStatementBalanceOne?.amount).isEqualTo(olderStatementBalance.amount)
      assertThat(retrievedStatementBalanceOne?.balanceDateTime).isEqualTo(olderStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalanceOne?.subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)

      val latestCreatedStatementBalance = repoTestHelpers.createStatementBalance(amount = 20, balanceDateTime = Instant.now(), subAccount = testAccountOneSubAccountOne)

      val retrievedStatementBalanceTwo = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testAccountOneSubAccountOne.id)

      assertThat(retrievedStatementBalanceTwo?.amount).isEqualTo(latestCreatedStatementBalance.amount)
      assertThat(retrievedStatementBalanceTwo?.balanceDateTime).isEqualTo(latestCreatedStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalanceTwo?.subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }

    @Test
    fun `Should only return the most recent statement balance for the provided sub account`() {
      val statementBalanceForTestAccountOne = repoTestHelpers.createStatementBalance(amount = 100, balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC), subAccount = testAccountOneSubAccountOne)
      val moreRecentBalanceForAnotherAccount = repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testAccountTwoSubAccountOne)

      val latestCreatedStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testAccountOneSubAccountOne.id)

      assertThat(latestCreatedStatementBalance?.amount).isEqualTo(statementBalanceForTestAccountOne.amount)
      assertThat(latestCreatedStatementBalance?.balanceDateTime).isEqualTo(statementBalanceForTestAccountOne.balanceDateTime)
      assertThat(latestCreatedStatementBalance?.subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }

    @Test
    fun `Should only return the most recent balance before the timestamp provided`() {
      val statementBalanceFirst = repoTestHelpers.createStatementBalance(amount = 100, balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC), subAccount = testAccountOneSubAccountOne)
      repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testAccountOneSubAccountOne)

      val statementBalance = statementBalanceDataRepository
        .getLatestStatementBalanceForSubAccountId(
          testAccountOneSubAccountOne.id,
          statementBalanceFirst.balanceDateTime.plusSeconds(1),
        )

      assertThat(statementBalance?.amount).isEqualTo(statementBalanceFirst.amount)
      assertThat(statementBalance?.balanceDateTime).isEqualTo(statementBalanceFirst.balanceDateTime)
      assertThat(statementBalance?.subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }
  }

  @Nested
  inner class GetLatestStatementBalancesForAccount {

    @Test
    fun `Should return an emptyList if no statement balance exists for the sub account`() {
      val statementBalance = statementBalanceDataRepository.getLatestStatementBalancesForAccountId(UUID.randomUUID())

      assertThat(statementBalance).hasSize(0)
    }

    @Test
    fun `Should return a statement balance when one exists`() {
      val createdStatementBalance = repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testAccountOneSubAccountOne)

      val retrievedStatementBalance = statementBalanceDataRepository.getLatestStatementBalancesForAccountId(testAccountOneSubAccountOne.parentAccountEntity.id)

      assertThat(retrievedStatementBalance).hasSize(1)
      assertThat(retrievedStatementBalance.first().amount).isEqualTo(createdStatementBalance.amount)
      assertThat(retrievedStatementBalance.first().balanceDateTime).isEqualTo(createdStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalance.first().subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }

    @Test
    fun `Should return the latest statement balance for each subAccount when multiple exist`() {
      val olderStatementBalance = repoTestHelpers.createStatementBalance(
        amount = 10,
        balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC),
        subAccount =
          testAccountOneSubAccountOne,
      )

      val retrievedStatementBalancesAccountOne = statementBalanceDataRepository.getLatestStatementBalancesForAccountId(testAccountOneSubAccountOne.parentAccountEntity.id)

      assertThat(retrievedStatementBalancesAccountOne).hasSize(1)
      assertThat(retrievedStatementBalancesAccountOne.first().amount).isEqualTo(olderStatementBalance.amount)
      assertThat(retrievedStatementBalancesAccountOne.first().balanceDateTime).isEqualTo(olderStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalancesAccountOne.first().subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)

      val latestCreatedStatementBalance = repoTestHelpers.createStatementBalance(amount = 20, balanceDateTime = Instant.now(), subAccount = testAccountOneSubAccountOne)

      val retrievedStatementBalanceSecondTime = statementBalanceDataRepository.getLatestStatementBalancesForAccountId(testAccountOneSubAccountOne.parentAccountEntity.id)

      assertThat(retrievedStatementBalanceSecondTime).hasSize(1)
      assertThat(retrievedStatementBalanceSecondTime.first().amount).isEqualTo(latestCreatedStatementBalance.amount)
      assertThat(retrievedStatementBalanceSecondTime.first().balanceDateTime).isEqualTo(latestCreatedStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalanceSecondTime.first().subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }

    @Test
    fun `Should only return the most recent statement balance for the provided sub account`() {
      val statementBalanceForTestAccountOne = repoTestHelpers.createStatementBalance(amount = 100, balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC), subAccount = testAccountOneSubAccountOne)
      val moreRecentBalanceForAnotherAccount = repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testAccountTwoSubAccountOne)

      val latestCreatedStatementBalance = statementBalanceDataRepository.getLatestStatementBalancesForAccountId(testAccountOneSubAccountOne.parentAccountEntity.id)

      assertThat(latestCreatedStatementBalance).hasSize(1)
      assertThat(latestCreatedStatementBalance.first().amount).isEqualTo(statementBalanceForTestAccountOne.amount)
      assertThat(latestCreatedStatementBalance.first().balanceDateTime).isEqualTo(statementBalanceForTestAccountOne.balanceDateTime)
      assertThat(latestCreatedStatementBalance.first().subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }

    @Test
    fun `Should only return the most recent balance before the timestamp provided`() {
      val statementBalanceFirst = repoTestHelpers.createStatementBalance(amount = 100, balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC), subAccount = testAccountOneSubAccountOne)
      repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testAccountOneSubAccountOne)

      val statementBalance = statementBalanceDataRepository
        .getLatestStatementBalancesForAccountId(
          testAccountOneSubAccountOne.parentAccountEntity.id,
          statementBalanceFirst.balanceDateTime.plusSeconds(1),
        )

      assertThat(statementBalance).hasSize(1)
      assertThat(statementBalance.first().amount).isEqualTo(statementBalanceFirst.amount)
      assertThat(statementBalance.first().balanceDateTime).isEqualTo(statementBalanceFirst.balanceDateTime)
      assertThat(statementBalance.first().subAccountEntity?.id).isEqualTo(testAccountOneSubAccountOne.id)
    }

    @Test
    fun `Should return the latest statement balances for each subAccount in the account when they exist`() {
      val parentAccount = repoTestHelpers.createAccount("ABX123XZ")
      val cashSubAccount = repoTestHelpers.createSubAccount("CASH", parentAccount)
      val savingsSubAccount = repoTestHelpers.createSubAccount("SAVINGS", parentAccount)

      val statementBalanceCashOld = repoTestHelpers.createStatementBalance(amount = 123, balanceDateTime = Instant.now().minusSeconds(60), subAccount = cashSubAccount)
      val statementBalanceCashNew = repoTestHelpers.createStatementBalance(amount = 500, balanceDateTime = Instant.now(), subAccount = cashSubAccount)

      val statementBalanceSavingsOld = repoTestHelpers.createStatementBalance(amount = 222, balanceDateTime = Instant.now().minusSeconds(60), subAccount = savingsSubAccount)
      val statementBalanceSavingsNew = repoTestHelpers.createStatementBalance(amount = 333, balanceDateTime = Instant.now(), subAccount = savingsSubAccount)

      val statementBalance = statementBalanceDataRepository
        .getLatestStatementBalancesForAccountId(
          parentAccount.id,
          Instant.now()
        )

      assertThat(statementBalance).hasSize(2)

      val cashStatementBalance = statementBalance.first { it.subAccountEntity.id == cashSubAccount.id }
      val savingsStatementBalance = statementBalance.first { it.subAccountEntity.id == savingsSubAccount.id }

      assertThat(cashStatementBalance.amount).isEqualTo(statementBalanceCashNew.amount)
      assertThat(cashStatementBalance.balanceDateTime).isEqualTo(statementBalanceCashNew.balanceDateTime)
      assertThat(cashStatementBalance.subAccountEntity.id).isEqualTo(cashSubAccount.id)

      assertThat(savingsStatementBalance.amount).isEqualTo(statementBalanceSavingsNew.amount)
      assertThat(savingsStatementBalance.balanceDateTime).isEqualTo(statementBalanceSavingsNew.balanceDateTime)
      assertThat(savingsStatementBalance.subAccountEntity.id).isEqualTo(savingsSubAccount.id)
    }
  }
}
