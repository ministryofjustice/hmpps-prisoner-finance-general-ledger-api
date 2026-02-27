package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

class ServiceTestHelpers {
  fun createAccount(ref: String): AccountEntity {
    val account = AccountEntity(
      reference = ref,
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

  fun createOneToOneTransaction(transactionAmount: Long, transactionDateTime: Instant, debitSubAccount: SubAccountEntity, creditSubAccount: SubAccountEntity, timeStamp: Instant? = null, description: String = "TEST_DESCRIPTION_PAST"): TransactionEntity {
    val txInThePast = TransactionEntity(
      reference = UUID.randomUUID().toString(),
      description = description,
      amount = transactionAmount,
      timestamp = timeStamp ?: transactionDateTime,
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

    return transaction
  }

  fun createStatementBalance(subAccount: SubAccountEntity, amount: Long, balanceDateTime: Instant): StatementBalanceEntity {
    val statementBalance = StatementBalanceEntity(amount = amount, balanceDateTime = balanceDateTime, subAccountEntity = subAccount)
    return statementBalance
  }
}
