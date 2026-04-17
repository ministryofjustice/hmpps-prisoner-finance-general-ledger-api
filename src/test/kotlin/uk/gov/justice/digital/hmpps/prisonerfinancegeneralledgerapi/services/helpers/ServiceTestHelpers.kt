package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

class ServiceTestHelpers {
  fun createAccount(ref: String, type: AccountType): AccountEntity {
    val account = AccountEntity(
      reference = ref,
      type = type,
    )
    return account
  }

  fun createSubAccount(ref: String, account: AccountEntity): SubAccountEntity {
    val subAccountEntity = SubAccountEntity(
      reference = ref,
      parentAccountEntity = account,
    )
    return subAccountEntity
  }

  fun createOneToOneTransaction(
    transactionAmount: Long,
    transactionDateTime: Instant,
    debitSubAccount: SubAccountEntity,
    creditSubAccount: SubAccountEntity,
    timeStamp: Instant? = null,
    description: String = "TEST_DESCRIPTION_PAST",
  ): TransactionEntity {
    val txInThePast = TransactionEntity(
      reference = UUID.randomUUID().toString(),
      description = description,
      amount = transactionAmount,
      timestamp = timeStamp ?: transactionDateTime,
      entrySequence = 1,
    )
    val postings = listOf(
      PostingEntity(
        createdAt = transactionDateTime,
        type = PostingType.DR,
        amount = transactionAmount,
        subAccountEntity = debitSubAccount,
        transactionEntity = txInThePast,
        entrySequence = 1,
      ),
      PostingEntity(
        createdAt = transactionDateTime,
        type = PostingType.CR,
        amount = transactionAmount,
        subAccountEntity = creditSubAccount,
        transactionEntity = txInThePast,
        entrySequence = 2,
      ),
    )

    txInThePast.postings.addAll(postings)

    return txInThePast
  }

  fun createOneToManyTransaction(
    ref: String,
    debitSubAccount: SubAccountEntity,
    creditSubAccounts: List<SubAccountEntity>,
    amountToCreditEachSubAccount: Long,
    description: String = "TEST_DESCRIPTION_PAST",
  ): TransactionEntity {
    val overallDebitAmount = amountToCreditEachSubAccount * creditSubAccounts.size

    val transaction = TransactionEntity(
      reference = ref,
      amount = overallDebitAmount,
      description = description,
      entrySequence = 1,
    )

    val postings = mutableListOf<PostingEntity>(
      PostingEntity(
        subAccountEntity = debitSubAccount,
        amount = overallDebitAmount,
        transactionEntity = transaction,
        type = PostingType.DR,
        entrySequence = 1,
      ),
    )

    for (i in 0..<creditSubAccounts.size) {
      val creditPosting = PostingEntity(
        subAccountEntity = creditSubAccounts[i],
        transactionEntity = transaction,
        type = PostingType.CR,
        amount = amountToCreditEachSubAccount,
        entrySequence = i + 2L,
      )
      postings.add(creditPosting)
    }

    transaction.postings.addAll(postings)

    return transaction
  }

  fun createStatementBalance(subAccount: SubAccountEntity, amount: Long, balanceDateTime: Instant): StatementBalanceEntity {
    val statementBalance = StatementBalanceEntity(amount = amount, balanceDateTime = balanceDateTime, subAccountEntity = subAccount)
    return statementBalance
  }

  fun createPostingBalance(
    subAccount1: SubAccountEntity,
    subAccount2: SubAccountEntity,
    transactionTimeStamp: Instant,
    transactionAmount: Long,
    subAccountBalance1: Long,
    subAccountBalance2: Long,
    totalAccountBalance: Long, // todo think about this
    transactionEntrySequence: Long = 1,
    postingsEntrySequences: Pair<Long, Long> = Pair(1, 2),
    ): Pair<PostingBalanceEntity, PostingBalanceEntity>{
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

    val postingBalanceSubAccount1 = PostingBalanceEntity(
      id = UUID.randomUUID(),
      postingEntity = postingEntity1,
      totalSubAccountBalance = subAccountBalance1,
      totalAccountBalance = totalAccountBalance,
    )

    val postingBalanceSubAccount2 = PostingBalanceEntity(
      id = UUID.randomUUID(),
      postingEntity = postingEntity2,
      totalSubAccountBalance = subAccountBalance2,
      totalAccountBalance = totalAccountBalance,
    )

    return Pair(postingBalanceSubAccount1, postingBalanceSubAccount2)
  }
}
