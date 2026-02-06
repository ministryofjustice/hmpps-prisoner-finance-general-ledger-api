package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers

import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.boot.test.context.TestConfiguration
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.IdempotencyKeyDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import java.time.LocalDateTime
import java.util.UUID

@TestConfiguration
class RepoTestHelpers(
  private val entityManager: TestEntityManager,
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

  fun createOneToOneTransaction(transactionAmount: Long, transactionDateTime: LocalDateTime, debitSubAccount: SubAccountEntity, creditSubAccount: SubAccountEntity): TransactionEntity {
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

  fun createStatementBalance(subAccount: SubAccountEntity, amount: Long, balanceDateTime: LocalDateTime): StatementBalanceEntity {
    val statementBalance = StatementBalanceEntity(amount = amount, balanceDateTime = balanceDateTime, subAccountEntity = subAccount)
    entityManager.persist(statementBalance)
    return statementBalance
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
