package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import java.util.UUID
import kotlin.time.Instant


data class PostingBalanceData(
  val amount: Long,
  val timestamp: Instant
)

interface IPostingBalanceCalculator {
  val statementBalances: List<StatementBalanceEntity>
  val postingBalanceMap: Map<UUID, PostingBalanceData>

  fun main(
    statementBalances: List<StatementBalanceEntity>,
    startPosting: PostingEntity,
  )

  // return postingBalanceMap[subAccountId] if there is no more recent statementBalances
  // if there is a more recent statement balance, update the postingBalanceMap[subAccountId] with the new amount and timestamp
  fun get(subAccountId: UUID, posting: PostingEntity): PostingBalanceData?

  // will call this.get for each subAccountId
  fun getAll()

  // updates the postingBalanceMap[subAccountId] with the new amount
  fun set(subAccountId: UUID, posting: PostingEntity)
}


/*
 * Should instantiate class and get the default postingBalanceMap for subAccount
 *   // assert that gets previous postingBalances from the startPosting
 *   // assert that gets all statementBalances
 *   // assert that the GET is what we expect
 *
 * Should update postingBalanceData when we call SET
 *  // assert by calling GET after SET
 *
 * Should call getAll and get all postingBalanceData when there are no statementBalances
 *
 * Should call getAll and get all postingBalanceData when there are statementBalances
 */
