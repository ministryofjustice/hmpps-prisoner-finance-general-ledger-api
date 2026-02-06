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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
class PostingDataRepositoryTest @Autowired constructor(
  val entityManager: TestEntityManager,
  val accountDataRepository: AccountDataRepository,
  val transactionDataRepository: TransactionDataRepository,
  val subAccountDataRepository: SubAccountDataRepository,
  @Autowired val postingsDataRepository: PostingsDataRepository,
  dataRepository: PostingsDataRepository,
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
      accountOne = AccountEntity(
        reference = "TEST_ACCOUNT_REF_1",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      )
      entityManager.persist(accountOne)

      accountOneSubAccountOne = SubAccountEntity(
        reference = "TEST_SUB_ACCOUNT_REF_1",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        parentAccountEntity = accountOne,
      )
      entityManager.persist(accountOneSubAccountOne)

      accountTwo = AccountEntity(
        reference = "TEST_ACCOUNT_REF_2",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
      )
      entityManager.persist(accountTwo)

      accountTwoSubAccountOne = SubAccountEntity(
        reference = "TEST_SUB_ACCOUNT_REF_2",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        parentAccountEntity = accountTwo,
      )
      entityManager.persist(accountTwoSubAccountOne)

      accountThree = AccountEntity(
        reference = "TEST_ACCOUNT_REF_3",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
      )
      entityManager.persist(accountThree)

      accountThreeSubAccountOne = SubAccountEntity(
        reference = "TEST_SUB_ACCOUNT_REF_3",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
        parentAccountEntity = accountThree,
      )
      entityManager.persist(accountThreeSubAccountOne)

      testTransactionEntity = TransactionEntity(
        reference = "TEST_TRANSACTION_REF",
        description = "TEST_DESCRIPTION",
        amount = 4,
        timestamp = LocalDateTime.now(),
      )
      entityManager.persist(testTransactionEntity)

      for (i in 1..10) {
        val evenNumber = i % 2 == 0
        val subAccountForPosting = if (evenNumber) accountOneSubAccountOne else accountTwoSubAccountOne
        val postingType = if (evenNumber) PostingType.CR else PostingType.DR

        val newPostingEntity = PostingEntity(
          createdBy = "TEST_USERNAME",
          createdAt = LocalDateTime.now(),
          type = postingType,
          amount = 1,
          subAccountEntity = subAccountForPosting,
          transactionEntity = testTransactionEntity,
        )

        testTransactionEntity.postings.add(newPostingEntity)
        entityManager.persist(newPostingEntity)
      }

//    At this point accountOneSubAccountOne should have 5 credits and accountTwoSubAccountOne should have 5 debits
//    Totals should be 5 and -5 respectively

//    Postings in the opposite direction to prove things work with varied posting types on a single subAccount
//    This puts each at 4 and -4 respectively
      val debitOnePence = PostingEntity(
        createdBy = "TEST_USERNAME",
        createdAt = LocalDateTime.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = accountOneSubAccountOne,
        transactionEntity = testTransactionEntity,
      )
      val creditOnePence = PostingEntity(
        createdBy = "TEST_USERNAME",
        createdAt = LocalDateTime.now(),
        type = PostingType.CR,
        amount = 1,
        subAccountEntity = accountTwoSubAccountOne,
        transactionEntity = testTransactionEntity,
      )

      testTransactionEntity.postings.add(debitOnePence)
      testTransactionEntity.postings.add(creditOnePence)
      entityManager.persist(debitOnePence)
      entityManager.persist(creditOnePence)

      entityManager.flush()
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

    fun createTransaction(transactionAmount: Long, transactionDateTime: LocalDateTime, debitSubAccount: SubAccountEntity, creditSubAccount: SubAccountEntity): TransactionEntity {
      val txInThePast = TransactionEntity(
        reference = UUID.randomUUID().toString(),
        description = "TEST_DESCRIPTION_PAST",
        amount = transactionAmount,
      )
      val postingsInThePast = listOf(
        PostingEntity(
          createdAt = transactionDateTime,
          type = PostingType.DR,
          amount = transactionAmount,
          subAccountEntity = debitSubAccount,
          transactionEntity = txInThePast,
        ),
        PostingEntity(
          createdAt = transactionDateTime,
          type = PostingType.CR,
          amount = transactionAmount,
          subAccountEntity = creditSubAccount,
          transactionEntity = txInThePast,
        ),
      )

      txInThePast.postings.addAll(postingsInThePast)
      entityManager.persist(txInThePast)
      entityManager.persist(postingsInThePast[0])
      entityManager.persist(postingsInThePast[1])

      return txInThePast
    }

    @Test
    fun `Should return the balance of all postings after a datetime if provided`() {
      val accountWithNoMoney = accountThreeSubAccountOne
      val zeroBalance = postingsDataRepository.getBalanceForSubAccount(accountWithNoMoney.id)
      assertThat(zeroBalance).isEqualTo(0)

      // txToIgnoreFromTwoDaysAgo
      createTransaction(100, LocalDateTime.now().minusDays(2), accountOneSubAccountOne, accountWithNoMoney)

      val statementBalanceFromYesterday = StatementBalanceEntity(amount = 0, subAccountEntity = accountOneSubAccountOne, balanceDateTime = LocalDateTime.now().minusDays(1))

      // txFromTodayToInclude
      createTransaction(50, LocalDateTime.now(), accountOneSubAccountOne, accountWithNoMoney)

      val subAccountBalance = postingsDataRepository.getBalanceForSubAccount(accountWithNoMoney.id, latestStatementBalanceDateTime = statementBalanceFromYesterday.balanceDateTime)

      assertThat(subAccountBalance).isEqualTo(50)
    }
  }

  @Nested
  inner class GetBalanceForAPrisonerAtAPrison {

    fun createAccount(ref: String): AccountEntity {
      val account = AccountEntity(
        reference = ref,
      )
      entityManager.persist(account)
      return account
    }

    fun createSubAccount(ref: String, account: AccountEntity): SubAccountEntity {
      val subAccountEntity = SubAccountEntity(
        reference = ref,
        parentAccountEntity = account,
      )
      entityManager.persist(subAccountEntity)
      return subAccountEntity
    }

    fun createTransaction(
      ref: String,
      debitSubAccount: SubAccountEntity,
      creditSubAccount: SubAccountEntity,
      amount: Long,
    ): TransactionEntity {
      val transaction = TransactionEntity(
        reference = ref,
        amount = amount,
      )

      val postings = listOf(
        PostingEntity(
          subAccountEntity = debitSubAccount,
          amount = amount,
          transactionEntity = transaction,
          type = PostingType.DR,
        ),
        PostingEntity(
          subAccountEntity = creditSubAccount,
          amount = amount,
          transactionEntity = transaction,
          type = PostingType.CR,
        ),
      )

      transaction.postings.addAll(postings)
      entityManager.persist(transaction)
      entityManager.persist(postings[0])
      entityManager.persist(postings[1])

      return transaction
    }

    fun createOneToManyTransaction(
      ref: String,
      debitSubAccount: SubAccountEntity,
      creditSubAccounts: List<SubAccountEntity>,
      amountToCreditEachSubAccount: Long,
    ): TransactionEntity {
      val overallDebitAmount = amountToCreditEachSubAccount * creditSubAccounts.size

      val transaction = TransactionEntity(
        reference = ref,
        amount = overallDebitAmount,
      )

      val postings = mutableListOf<PostingEntity>(
        PostingEntity(
          subAccountEntity = debitSubAccount,
          amount = overallDebitAmount,
          transactionEntity = transaction,
          type = PostingType.DR,
        ),
      )

      for (i in 0..<creditSubAccounts.size) {
        val creditPosting = PostingEntity(
          subAccountEntity = creditSubAccounts[i],
          transactionEntity = transaction,
          type = PostingType.CR,
          amount = amountToCreditEachSubAccount,
        )
        postings.add(creditPosting)
      }

      transaction.postings.addAll(postings)
      entityManager.persist(transaction)
      postings.forEach { postingEntity ->
        entityManager.persist(postingEntity)
      }

      return transaction
    }

    @Test
    fun `Should return zero if a prisoner has no transactions with that prison`() {
      val prisonA = createAccount("AAA")
      val prisonerOne = createAccount("123456")

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)

      assertThat(balanceForPrisonerAtPrison).isEqualTo(0)
    }

    @Test
    fun `Should return the balance of a prisoner sub account postings when they are all at one prison`() {
      val prisonA = createAccount("AAA")
      val prisonACanteen = createSubAccount("AAA:Canteen", prisonA)
      val prisonerOne = createAccount("123456")
      val prisonerOneCash = createSubAccount("CASH", prisonerOne)

      createTransaction("MEAL", prisonerOneCash, prisonACanteen, 10)
      createTransaction("MEAL-REFUND", prisonACanteen, prisonerOneCash, 5)

      val prisonerBalAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)

      assertThat(prisonerBalAtPrison).isEqualTo(-5)
    }

    @Test
    fun `Should return the balance of a prisoner at the queried prison when transactions exist at another prison`() {
      val prisonA = createAccount("AAA")
      val prisonACanteen = createSubAccount("AAA:Canteen", prisonA)

      val prisonB = createAccount("BBB")
      val prisonBCatalog = createSubAccount("BBB:Catalog", prisonB)

      val prisoner = createAccount("123456")
      val prisonerCashAccount = createSubAccount("CASH", prisoner)

      createTransaction("MEAL", prisonerCashAccount, prisonACanteen, 15)
      createTransaction("MARS-BAR", prisonerCashAccount, prisonBCatalog, 1)

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisoner.id)
      assertThat(balanceForPrisonerAtPrison).isEqualTo(-15)
    }

    @Test
    fun `Should return the balance of a prisoner at the queried prison when transactions exist with many to one or one to many relationships`() {
      val prisonA = createAccount("AAA")
      val prisonACash = createSubAccount("AAA:CASH", prisonA)

      val prisonerOne = createAccount("123456")
      val prisonerOneCashAccount = createSubAccount("CASH", prisonerOne)

      val prisonerTwo = createAccount("7891011")
      val prisonerTwoCashAccount = createSubAccount("CASH", prisonerTwo)

      createOneToManyTransaction(
        "BONUS",
        prisonACash,
        listOf(prisonerOneCashAccount, prisonerTwoCashAccount),
        30,
      )

      createTransaction("DAMAGES", prisonerTwoCashAccount, prisonACash, 1)

      createTransaction("WAGES", prisonACash, prisonerOneCashAccount, 5)

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
