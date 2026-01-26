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

  private lateinit var testAccountEntityOne: AccountEntity
  private lateinit var testAccountEntityTwo: AccountEntity
  private lateinit var testSubAccountEntityOne: SubAccountEntity
  private lateinit var testSubAccountEntityTwo: SubAccountEntity
  private lateinit var testTransactionEntity: TransactionEntity

  private lateinit var testAccountEntityThree: AccountEntity
  private lateinit var testSubAccountEntityThree: SubAccountEntity

  @BeforeEach
  fun setupEntities() {
//    Account and sub account one will have only credits
    testAccountEntityOne = AccountEntity(
      reference = "TEST_ACCOUNT_REF_1",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    )
    entityManager.persist(testAccountEntityOne)

    testSubAccountEntityOne = SubAccountEntity(
      reference = "TEST_SUB_ACCOUNT_REF_1",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      parentAccountEntity = testAccountEntityOne,
    )
    entityManager.persist(testSubAccountEntityOne)

// Account and sub account two will have only debits
    testAccountEntityTwo = AccountEntity(
      reference = "TEST_ACCOUNT_REF_2",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
    )
    entityManager.persist(testAccountEntityTwo)
    testSubAccountEntityTwo = SubAccountEntity(
      reference = "TEST_SUB_ACCOUNT_REF_2",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
      parentAccountEntity = testAccountEntityTwo,
    )
    entityManager.persist(testSubAccountEntityTwo)

//    Account and sub account three will have no postings
    testAccountEntityThree = AccountEntity(
      reference = "TEST_ACCOUNT_REF_3",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
    )
    entityManager.persist(testAccountEntityThree)

    testSubAccountEntityThree = SubAccountEntity(
      reference = "TEST_SUB_ACCOUNT_REF_3",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
      parentAccountEntity = testAccountEntityThree,
    )
    entityManager.persist(testSubAccountEntityThree)

//    Needs multiple transactions on both subaccount one and two
    testTransactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF", description = "TEST_DESCRIPTION", amount = 4, timestamp = LocalDateTime.now())
    entityManager.persist(testTransactionEntity)

    for (i in 1..10) {
      //  testSubAccountEntityOne will get 5 £1 credits, testSubAccountEntityTwo will get 5 £1 debits
      val evenNumber = i % 2 == 0
      val subAccountForPosting = if (evenNumber) testSubAccountEntityOne else testSubAccountEntityTwo
      val postingType = if (evenNumber) PostingType.CR else PostingType.DR

      val newPostingEntity = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = postingType, amount = 1, subAccountEntity = subAccountForPosting, transactionEntity = testTransactionEntity)

      testTransactionEntity.postings.add(newPostingEntity)
      entityManager.persist(newPostingEntity)
    }

//    At this point testSubAccountEntityOne should have 5 credits and testSubAccountEntityTwo should have 5 debits
//    Totals should be 5 and -5 respectively

//    Postings in the opposite direction to prove things work with varied posting types on a single subAccount
//    This puts each at 4 and -4 respectively
    val debitOnePoundFromSubAccountOne = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = PostingType.DR, amount = 1, subAccountEntity = testSubAccountEntityOne, transactionEntity = testTransactionEntity)
    val creditOnePoundToSubAccountTwo = PostingEntity(createdBy = "TEST_USERNAME", createdAt = LocalDateTime.now(), type = PostingType.CR, amount = 1, subAccountEntity = testSubAccountEntityTwo, transactionEntity = testTransactionEntity)

    testTransactionEntity.postings.add(debitOnePoundFromSubAccountOne)
    testTransactionEntity.postings.add(creditOnePoundToSubAccountTwo)
    entityManager.persist(debitOnePoundFromSubAccountOne)
    entityManager.persist(creditOnePoundToSubAccountTwo)

    entityManager.flush()
  }

  @Nested
  inner class GetNetAmountForSubAccount {

    @Test
    fun `Should return a balance of all credits minus all debits for a sub account`() {
      val subOneBalance = postingsDataRepository.getBalanceForSubAccount(testSubAccountEntityOne.id)
      val subTwoBalance = postingsDataRepository.getBalanceForSubAccount(testSubAccountEntityTwo.id)

      assertThat(subOneBalance).isEqualTo(4)
      assertThat(subTwoBalance).isEqualTo(-4)
    }

    @Test
    fun `Should return 0 if no postings found for the subaccount Id`() {
      val subThreeBalance = postingsDataRepository.getBalanceForSubAccount(testSubAccountEntityThree.id)

      assertThat(subThreeBalance).isEqualTo(0)
    }
  }
}
