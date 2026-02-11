package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
@Import(RepoTestHelpers::class)
class StatementBalanceDataRepositoryTest @Autowired constructor(
  val statementBalanceDataRepository: StatementBalanceDataRepository,
  val repoTestHelpers: RepoTestHelpers,
) {

  lateinit var testAccountOne: AccountEntity
  lateinit var testSubAccountOne: SubAccountEntity

  lateinit var testAccountTwo: AccountEntity
  lateinit var testSubAccountTwo: SubAccountEntity

  @BeforeEach
  fun setup() {
    repoTestHelpers.clearDb()
    testAccountOne = repoTestHelpers.createAccount("TEST_ACCOUNT_REF")
    testSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF", testAccountOne)

    testAccountTwo = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_2")
    testSubAccountTwo = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_2", testAccountTwo)
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
      val createdStatementBalance = repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testSubAccountOne)

      val retrievedStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testSubAccountOne.id)

      assertThat(retrievedStatementBalance?.amount).isEqualTo(createdStatementBalance.amount)
      assertThat(retrievedStatementBalance?.balanceDateTime).isEqualTo(createdStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalance?.subAccountEntity?.id).isEqualTo(testSubAccountOne.id)
    }

    @Test
    fun `Should return the latest statement balance when multiple exist`() {
      val olderStatementBalance = repoTestHelpers.createStatementBalance(
        amount = 10,
        balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC),
        subAccount =
        testSubAccountOne,
      )

      val retrievedStatementBalanceOne = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testSubAccountOne.id)

      assertThat(retrievedStatementBalanceOne?.amount).isEqualTo(olderStatementBalance.amount)
      assertThat(retrievedStatementBalanceOne?.balanceDateTime).isEqualTo(olderStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalanceOne?.subAccountEntity?.id).isEqualTo(testSubAccountOne.id)

      val latestCreatedStatementBalance = repoTestHelpers.createStatementBalance(amount = 20, balanceDateTime = Instant.now(), subAccount = testSubAccountOne)

      val retrievedStatementBalanceTwo = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testSubAccountOne.id)

      assertThat(retrievedStatementBalanceTwo?.amount).isEqualTo(latestCreatedStatementBalance.amount)
      assertThat(retrievedStatementBalanceTwo?.balanceDateTime).isEqualTo(latestCreatedStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalanceTwo?.subAccountEntity?.id).isEqualTo(testSubAccountOne.id)
    }

    @Test
    fun `Should only return the most recent statement balance for the provided sub account`() {
      val statementBalanceForTestAccountOne = repoTestHelpers.createStatementBalance(amount = 100, balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC), subAccount = testSubAccountOne)
      val moreRecentBalanceForAnotherAccount = repoTestHelpers.createStatementBalance(amount = 10, balanceDateTime = Instant.now(), subAccount = testSubAccountTwo)

      val latestCreatedStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testSubAccountOne.id)

      assertThat(latestCreatedStatementBalance?.amount).isEqualTo(statementBalanceForTestAccountOne.amount)
      assertThat(latestCreatedStatementBalance?.balanceDateTime).isEqualTo(statementBalanceForTestAccountOne.balanceDateTime)
      assertThat(latestCreatedStatementBalance?.subAccountEntity?.id).isEqualTo(testSubAccountOne.id)
    }
  }
}
