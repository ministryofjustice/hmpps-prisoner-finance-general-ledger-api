package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
class StatementBalanceDataRepositoryTest @Autowired constructor(
  val entityManager: TestEntityManager,
  val statementBalanceDataRepository: StatementBalanceDataRepository,
) {
  fun createStatementBalance(subAccount: SubAccountEntity, amount: Long, balanceDateTime: LocalDateTime): StatementBalanceEntity {
    val statementBalance = StatementBalanceEntity(amount = amount, balanceDateTime = balanceDateTime, subAccountEntity = subAccount)
    entityManager.persist(statementBalance)
    return statementBalance
  }

  fun createAccount(): AccountEntity {
    val accountEntity = AccountEntity(id = UUID.randomUUID(), reference = "test")
    entityManager.persist(accountEntity)
    return accountEntity
  }

  fun createSubAccount(account: AccountEntity): SubAccountEntity {
    val subAccountEntity = SubAccountEntity(id = UUID.randomUUID(), parentAccountEntity = account)
    entityManager.persist(subAccountEntity)
    return subAccountEntity
  }

  lateinit var testAccount: AccountEntity
  lateinit var testSubAccount: SubAccountEntity

  @BeforeEach
  fun setup() {
    entityManager.clear()
    testAccount = createAccount()
    testSubAccount = createSubAccount(testAccount)
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
      val createdStatementBalance = createStatementBalance(amount = 10, balanceDateTime = LocalDateTime.now(), subAccount = testSubAccount)

      val retrievedStatementBalance = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testSubAccount.id)

      assertThat(retrievedStatementBalance?.amount).isEqualTo(createdStatementBalance.amount)
      assertThat(retrievedStatementBalance?.balanceDateTime).isEqualTo(createdStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalance?.subAccountEntity?.id).isEqualTo(testSubAccount.id)
    }

    @Test
    fun `Should return the latest statement balance when multiple exist`() {
      val olderStatementBalance = createStatementBalance(amount = 10, balanceDateTime = LocalDateTime.now().minusDays(1), subAccount = testSubAccount)

      val retrievedStatementBalanceOne = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testSubAccount.id)

      assertThat(retrievedStatementBalanceOne?.amount).isEqualTo(olderStatementBalance.amount)
      assertThat(retrievedStatementBalanceOne?.balanceDateTime).isEqualTo(olderStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalanceOne?.subAccountEntity?.id).isEqualTo(testSubAccount.id)

      val latestCreatedStatementBalance = createStatementBalance(amount = 20, balanceDateTime = LocalDateTime.now(), subAccount = testSubAccount)

      val retrievedStatementBalanceTwo = statementBalanceDataRepository.getLatestStatementBalanceForSubAccountId(testSubAccount.id)

      assertThat(retrievedStatementBalanceTwo?.amount).isEqualTo(latestCreatedStatementBalance.amount)
      assertThat(retrievedStatementBalanceTwo?.balanceDateTime).isEqualTo(latestCreatedStatementBalance.balanceDateTime)
      assertThat(retrievedStatementBalanceTwo?.subAccountEntity?.id).isEqualTo(testSubAccount.id)
    }
  }
}
