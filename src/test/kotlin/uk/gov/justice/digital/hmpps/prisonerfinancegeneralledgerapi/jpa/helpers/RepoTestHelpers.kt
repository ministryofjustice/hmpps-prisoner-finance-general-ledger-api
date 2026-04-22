package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.ContainersConfig
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.oppositePostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.IdempotencyKeyDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingBalanceDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import java.time.Instant
import java.util.UUID

@Import(ContainersConfig::class)
@TestConfiguration
class RepoTestHelpers(
  private val entityManager: TestEntityManager,
  private val postingBalanceDataRepository: PostingBalanceDataRepository,
  private val postingsDataRepository: PostingsDataRepository,
  private val transactionDataRepository: TransactionDataRepository,
  private val subAccountDataRepository: SubAccountDataRepository,
  private val accountDataRepository: AccountDataRepository,
  private val idempotencyKeyDataRepository: IdempotencyKeyDataRepository,
) {
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

  fun createOneToOneTransaction(transactionAmount: Long, postingCreatedAt: Instant, debitSubAccount: SubAccountEntity, creditSubAccount: SubAccountEntity, debitEntrySequence: Long = 1, creditEntrySequence: Long = 2, transactionEntrySequence: Long = 1, transactionTimeStamp: Instant): TransactionEntity {
    val txInThePast = TransactionEntity(
      reference = UUID.randomUUID().toString(),
      description = "TEST_DESCRIPTION_PAST",
      amount = transactionAmount,
      timestamp = transactionTimeStamp,
      entrySequence = transactionEntrySequence,
    )
    val postingsInThePast = listOf(
      PostingEntity(
        createdAt = postingCreatedAt,
        type = PostingType.DR,
        amount = transactionAmount,
        subAccountEntity = debitSubAccount,
        transactionEntity = txInThePast,
        entrySequence = debitEntrySequence,
      ),
      PostingEntity(
        createdAt = postingCreatedAt,
        type = PostingType.CR,
        amount = transactionAmount,
        subAccountEntity = creditSubAccount,
        transactionEntity = txInThePast,
        entrySequence = creditEntrySequence,
      ),
    )

    txInThePast.postings.addAll(postingsInThePast)
    entityManager.persist(txInThePast)
    entityManager.persist(postingsInThePast[0])
    entityManager.persist(postingsInThePast[1])

    return txInThePast
  }

  fun createOneToManyTransaction(
    ref: String,
    oneToManySubAccount: SubAccountEntity,
    manyToOneSubAccounts: List<SubAccountEntity>,
    amountPerSubAccount: Long,
    oneToManyPostingType: PostingType = PostingType.DR,
  ): TransactionEntity {
    val overallOneToManyAmount = amountPerSubAccount * manyToOneSubAccounts.size

    val transaction = TransactionEntity(
      reference = ref,
      amount = overallOneToManyAmount,
      entrySequence = 1,
    )

    val postings = mutableListOf<PostingEntity>(
      PostingEntity(
        subAccountEntity = oneToManySubAccount,
        amount = overallOneToManyAmount,
        transactionEntity = transaction,
        type = oneToManyPostingType,
        entrySequence = 1,
      ),
    )

    for (i in 0..<manyToOneSubAccounts.size) {
      val posting = PostingEntity(
        subAccountEntity = manyToOneSubAccounts[i],
        transactionEntity = transaction,
        type = oneToManyPostingType.oppositePostingType(),
        amount = amountPerSubAccount,
        entrySequence = i + 2L,
      )
      postings.add(posting)
    }

    transaction.postings.addAll(postings)
    entityManager.persist(transaction)
    postings.forEach { postingEntity ->
      entityManager.persist(postingEntity)
    }

    return transaction
  }

  fun createStatementBalance(subAccount: SubAccountEntity, amount: Long, balanceDateTime: Instant): StatementBalanceEntity {
    val statementBalance = StatementBalanceEntity(amount = amount, balanceDateTime = balanceDateTime, subAccountEntity = subAccount)
    entityManager.persist(statementBalance)
    return statementBalance
  }

  fun createPostingBalancePrisoner(
    subAccount1: SubAccountEntity,
    subAccount2: SubAccountEntity,
    transactionTimeStamp: Instant,
    transactionAmount: Long,
    subAccountBalance1: Long,
    subAccountBalance2: Long,
    totalAccountBalance: Long, // todo think about this
    transactionEntrySequence: Long = 1,
    postingsEntrySequences: Pair<Long, Long> = Pair(1, 2),
  ) {
    val transactionEntity = TransactionEntity(
      id = UUID.randomUUID(),
      reference = "TEST_REF",
      amount = transactionAmount,
      timestamp = transactionTimeStamp,
      postings = mutableListOf(),
      entrySequence = transactionEntrySequence,
    )

    val postingEntity1 = PostingEntity(
      id = UUID.randomUUID(),
      createdAt = Instant.now(),
      type = PostingType.DR,
      amount = subAccountBalance1,
      subAccountEntity = subAccount1,
      transactionEntity = transactionEntity,
      entrySequence = postingsEntrySequences.first,
    )

    val postingEntity2 = PostingEntity(
      id = UUID.randomUUID(),
      createdAt = Instant.now(),
      type = PostingType.DR,
      amount = subAccountBalance2,
      subAccountEntity = subAccount2,
      transactionEntity = transactionEntity,
      entrySequence = postingsEntrySequences.second,
    )

    transactionEntity.postings.add(postingEntity1)
    transactionEntity.postings.add(postingEntity2)

    val postingBalance1 = PostingBalanceEntity(
      id = UUID.randomUUID(),
      postingEntity = postingEntity1,
      totalSubAccountBalance = subAccountBalance1,
    )

    val postingBalance2 = PostingBalanceEntity(
      id = UUID.randomUUID(),
      postingEntity = postingEntity2,
      totalSubAccountBalance = subAccountBalance2,
    )

    entityManager.persist(transactionEntity)
    entityManager.persist(postingEntity1)
    entityManager.persist(postingEntity2)
    entityManager.persist(postingBalance1)
    entityManager.persist(postingBalance2)
  }

  fun clearDb() {
    idempotencyKeyDataRepository.deleteAll()
    postingsDataRepository.deleteAll()
    transactionDataRepository.deleteAll()
    subAccountDataRepository.deleteAll()
    accountDataRepository.deleteAll()
    entityManager.clear()
  }
}
