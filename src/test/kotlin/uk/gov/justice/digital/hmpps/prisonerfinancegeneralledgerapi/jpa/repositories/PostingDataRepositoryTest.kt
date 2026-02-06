package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.LocalDateTime

@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
@Import(RepoTestHelpers::class)
class PostingDataRepositoryTest @Autowired constructor(
  val entityManager: TestEntityManager,
  val accountDataRepository: AccountDataRepository,
  val transactionDataRepository: TransactionDataRepository,
  val subAccountDataRepository: SubAccountDataRepository,
  val postingsDataRepository: PostingsDataRepository,
  val repoTestHelpers: RepoTestHelpers,
) {

  private lateinit var accountOne: AccountEntity
  private lateinit var accountOneSubAccountOne: SubAccountEntity

  private lateinit var accountTwo: AccountEntity
  private lateinit var accountTwoSubAccountOne: SubAccountEntity

  private lateinit var accountThree: AccountEntity
  private lateinit var accountThreeSubAccountOne: SubAccountEntity

  private lateinit var testTransactionEntity: TransactionEntity

  @BeforeEach
  fun clearDb() {
    postingsDataRepository.deleteAll()
    transactionDataRepository.deleteAll()
    subAccountDataRepository.deleteAll()
    accountDataRepository.deleteAll()
    entityManager.clear()
  }

  @Nested
  inner class GetBalanceForSubAccount {

    // Account | SubAccount   | Amount
    // --------|--------------|--------
    // 1       | SubAccount1  | 4 CR
    //         | TOTAL        | 4
    //
    // 2       | SubAccount1  | 4 DR
    //         | TOTAL        | -4
    //
    // 3       | SubAccount1  | 0 CR
    //         | TOTAL        | 0

    @BeforeEach
    fun setupEntities() {
      accountOne = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_1")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_1", accountOne)

      accountTwo = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_2")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_2", accountTwo)

      accountThree = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_3")
      accountThreeSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_3", accountThree)

      for (_i in 1..5) {
        repoTestHelpers.createOneToOneTransaction(1, LocalDateTime.now(), accountTwoSubAccountOne, accountOneSubAccountOne)
      }

      repoTestHelpers.createOneToOneTransaction(1, LocalDateTime.now(), accountOneSubAccountOne, accountTwoSubAccountOne)
    }

    @Test
    fun `Should return a balance of all credits minus all debits for a sub account`() {
      val subOneBalance = postingsDataRepository.getBalanceForSubAccount(accountOneSubAccountOne.id)
      val subTwoBalance = postingsDataRepository.getBalanceForSubAccount(accountTwoSubAccountOne.id)

      assertThat(subOneBalance).isEqualTo(4)
      assertThat(subTwoBalance).isEqualTo(-4)
    }

    @Test
    fun `Should return 0 if no postings found for the subaccount Id`() {
      val subThreeBalance = postingsDataRepository.getBalanceForSubAccount(accountThreeSubAccountOne.id)
      assertThat(subThreeBalance).isEqualTo(0)
    }

    @Test
    fun `Should return the balance of all postings after a datetime if provided`() {
      val accountWithNoMoney = accountThreeSubAccountOne
      val zeroBalance = postingsDataRepository.getBalanceForSubAccount(accountWithNoMoney.id)
      assertThat(zeroBalance).isEqualTo(0)

      // txToIgnoreFromTwoDaysAgo
      repoTestHelpers.createOneToOneTransaction(100, LocalDateTime.now().minusDays(2), accountOneSubAccountOne, accountWithNoMoney)

      val statementBalanceFromYesterday = StatementBalanceEntity(amount = 0, subAccountEntity = accountOneSubAccountOne, balanceDateTime = LocalDateTime.now().minusDays(1))

      // txFromTodayToInclude
      repoTestHelpers.createOneToOneTransaction(50, LocalDateTime.now(), accountOneSubAccountOne, accountWithNoMoney)

      val subAccountBalance = postingsDataRepository.getBalanceForSubAccount(accountWithNoMoney.id, latestStatementBalanceDateTime = statementBalanceFromYesterday.balanceDateTime)

      assertThat(subAccountBalance).isEqualTo(50)
    }
  }

  @Nested
  inner class GetBalanceForAPrisonerAtAPrison {

    @Test
    fun `Should return zero if a prisoner has no transactions with that prison`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonerOne = repoTestHelpers.createAccount("123456")

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)

      assertThat(balanceForPrisonerAtPrison).isEqualTo(0)
    }

    @Test
    fun `Should return the balance of a prisoner sub account postings when they are all at one prison`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonACanteen = repoTestHelpers.createSubAccount("AAA:Canteen", prisonA)
      val prisonerOne = repoTestHelpers.createAccount("123456")
      val prisonerOneCash = repoTestHelpers.createSubAccount("CASH", prisonerOne)

      repoTestHelpers.createOneToOneTransaction(10, LocalDateTime.now(), prisonerOneCash, prisonACanteen)
      repoTestHelpers.createOneToOneTransaction(5, LocalDateTime.now(), prisonACanteen, prisonerOneCash)

      val prisonerBalAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)

      assertThat(prisonerBalAtPrison).isEqualTo(-5)
    }

    @Test
    fun `Should return the balance of a prisoner at the queried prison when transactions exist at another prison`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonACanteen = repoTestHelpers.createSubAccount("AAA:Canteen", prisonA)

      val prisonB = repoTestHelpers.createAccount("BBB")
      val prisonBCatalogue = repoTestHelpers.createSubAccount("BBB:Catalog", prisonB)

      val prisoner = repoTestHelpers.createAccount("123456")
      val prisonerCashAccount = repoTestHelpers.createSubAccount("CASH", prisoner)

      repoTestHelpers.createOneToOneTransaction(15, LocalDateTime.now(), prisonerCashAccount, prisonACanteen)
      repoTestHelpers.createOneToOneTransaction(1, LocalDateTime.now(), prisonerCashAccount, prisonBCatalogue)

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisoner.id)
      assertThat(balanceForPrisonerAtPrison).isEqualTo(-15)
    }

    @Test
    fun `Should return the balance of a prisoner at the queried prison when transactions exist with many to one or one to many relationships`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonACash = repoTestHelpers.createSubAccount("AAA:CASH", prisonA)

      val prisonerOne = repoTestHelpers.createAccount("123456")
      val prisonerOneCashAccount = repoTestHelpers.createSubAccount("CASH", prisonerOne)

      val prisonerTwo = repoTestHelpers.createAccount("7891011")
      val prisonerTwoCashAccount = repoTestHelpers.createSubAccount("CASH", prisonerTwo)

      repoTestHelpers.createOneToManyTransaction(
        "BONUS",
        prisonACash,
        listOf(prisonerOneCashAccount, prisonerTwoCashAccount),
        30,
      )

      repoTestHelpers.createOneToOneTransaction(1, LocalDateTime.now(), prisonerTwoCashAccount, prisonACash)

      repoTestHelpers.createOneToOneTransaction(5, LocalDateTime.now(), prisonACash, prisonerOneCashAccount)

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)
      assertThat(balanceForPrisonerAtPrison).isEqualTo(35)
    }
  }

  @Nested
  inner class CalculateBalanceFromPostings {

    @Test
    fun `Should sum credits positively`() {
      val postings = listOf(
        PostingEntity(amount = 1, type = PostingType.CR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF")),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(1)
    }

    @Test
    fun `Should sum debits negatively`() {
      val postings = listOf(
        PostingEntity(amount = 1, type = PostingType.DR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF")),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(-1)
    }

    @Test
    fun `Should handle multiple postings of varied type`() {
      val postings = listOf(
        PostingEntity(
          amount = 3,
          type = PostingType.CR,
          subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"),
          transactionEntity = TransactionEntity(
            reference =
            "TEST_TRANSACTION_REF",
          ),
        ),
        PostingEntity(amount = 1, type = PostingType.DR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF")),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(2)
    }
  }
}
