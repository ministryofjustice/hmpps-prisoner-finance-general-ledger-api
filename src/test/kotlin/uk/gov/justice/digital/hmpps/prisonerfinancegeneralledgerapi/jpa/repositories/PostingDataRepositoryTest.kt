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
  val postingsDataRepository: PostingsDataRepository,
) {

  private lateinit var accountOne: AccountEntity
  private lateinit var accountOneSubAccountOne: SubAccountEntity

  private lateinit var accountTwo: AccountEntity
  private lateinit var accountTwoSubAccountOne: SubAccountEntity

  private lateinit var accountThree: AccountEntity
  private lateinit var accountThreeSubAccountOne: SubAccountEntity

  private lateinit var testTransactionEntity: TransactionEntity

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

      testTransactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF", description = "TEST_DESCRIPTION", amount = 4, timestamp = LocalDateTime.now())
      entityManager.persist(testTransactionEntity)

      for (i in 1..10) {
        val evenNumber = i % 2 == 0
        val subAccountForPosting = if (evenNumber) accountOneSubAccountOne else accountTwoSubAccountOne
        val postingType = if (evenNumber) PostingType.CR else PostingType.DR

        val newPostingEntity = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = postingType, amount = 1, subAccountEntity = subAccountForPosting, transactionEntity = testTransactionEntity)

        testTransactionEntity.postings.add(newPostingEntity)
        entityManager.persist(newPostingEntity)
      }

//    At this point accountOneSubAccountOne should have 5 credits and accountTwoSubAccountOne should have 5 debits
//    Totals should be 5 and -5 respectively

//    Postings in the opposite direction to prove things work with varied posting types on a single subAccount
//    This puts each at 4 and -4 respectively
      val debitOnePence = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = PostingType.DR, amount = 1, subAccountEntity = accountOneSubAccountOne, transactionEntity = testTransactionEntity)
      val creditOnePence = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = PostingType.CR, amount = 1, subAccountEntity = accountTwoSubAccountOne, transactionEntity = testTransactionEntity)

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
  }

  @Nested
  inner class GetBalanceForAccount {

    // Account | SubAccount   | Amount
    // --------|--------------|--------
    // 1       | SubAccount1  | 5 CR
    //         | TOTAL        | 5
    //
    // 2       | SubAccount1  | 5 DR
    // 2       | SubAccount2  | 3 CR
    //         | TOTAL        | -2
    //
    // 3       | SubAccount1  | 0 CR
    //         | TOTAL        | 0
    //
    // 4       | N/A          |
    //         | TOTAL        | 0

    lateinit var testAccountTwoSubAccountTwo: SubAccountEntity
    lateinit var accountFour: AccountEntity

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
        reference = "TEST_SUB_ACCOUNT_REF_1",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
        parentAccountEntity = accountTwo,
      )
      entityManager.persist(accountTwoSubAccountOne)

      testAccountTwoSubAccountTwo = SubAccountEntity(
        reference = "TEST_SUB_ACCOUNT_REF_2",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
        parentAccountEntity = accountTwo,
      )
      entityManager.persist(testAccountTwoSubAccountTwo)

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

      accountFour = AccountEntity(
        reference = "TEST_ACCOUNT_REF_4",
        createdBy = "TEST_USERNAME",
        id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
      )

      entityManager.persist(accountFour)

      testTransactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF", description = "TEST_DESCRIPTION", amount = 14, timestamp = LocalDateTime.now())
      entityManager.persist(testTransactionEntity)

      for (i in 1..10) {
        val evenNumber = i % 2 == 0
        val subAccountForPosting = if (evenNumber) accountOneSubAccountOne else accountTwoSubAccountOne
        val postingType = if (evenNumber) PostingType.CR else PostingType.DR

        val newPostingEntity = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = postingType, amount = 1, subAccountEntity = subAccountForPosting, transactionEntity = testTransactionEntity)
        testTransactionEntity.postings.add(newPostingEntity)
        entityManager.persist(newPostingEntity)
      }

      val testAccountTwoSubAccountTwoPosting = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = PostingType.CR, amount = 3, subAccountEntity = testAccountTwoSubAccountTwo, transactionEntity = testTransactionEntity)
      testTransactionEntity.postings.add(testAccountTwoSubAccountTwoPosting)
      entityManager.persist(testAccountTwoSubAccountTwoPosting)

      entityManager.flush()
    }

    @Test
    fun `Should return an account with one sub-account with a zero balance `() {
      val testAccountThreeBalance = postingsDataRepository.getBalanceForAccount(accountThreeSubAccountOne.id)
      assertThat(testAccountThreeBalance).isEqualTo(0)
    }

    @Test
    fun `Should return an account with one sub-account with multiple postings and credits`() {
      val testAccountOneBalance = postingsDataRepository.getBalanceForAccount(accountOne.id)
      assertThat(testAccountOneBalance).isEqualTo(5)
    }

    @Test
    fun `Should return an account with multiple sub-account with multiple postings and credits`() {
      val testAccountTwoBalance = postingsDataRepository.getBalanceForAccount(accountTwo.id)
      assertThat(testAccountTwoBalance).isEqualTo(-2)
    }

    @Test
    fun `Should return an account balance with no sub-accounts`() {
      val testAccountFourBalance = postingsDataRepository.getBalanceForAccount(accountFour.id)
      assertThat(testAccountFourBalance).isEqualTo(0)
    }

    @Test
    fun `Should return an account balance of 0 when the UUID does not reference an account `() {
      val noExistUUID = UUID.randomUUID()
      val noExistAccountBalance = postingsDataRepository.getBalanceForAccount(noExistUUID)
      assertThat(noExistAccountBalance).isEqualTo(0)
    }
  }
}
