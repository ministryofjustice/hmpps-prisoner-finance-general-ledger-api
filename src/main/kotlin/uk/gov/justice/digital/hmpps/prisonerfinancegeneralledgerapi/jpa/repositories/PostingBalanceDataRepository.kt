package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import java.time.Instant
import java.util.UUID

@Repository
interface PostingBalanceDataRepository : JpaRepository<PostingBalanceEntity, Long> {
  @Query(
    """
    SELECT * FROM (
        SELECT pb.*,
               ROW_NUMBER() OVER (
                   PARTITION BY p.sub_account_id
                   ORDER BY t.timestamp DESC,
                            t.entry_sequence DESC,
                            p.entry_sequence DESC,
                            p.posting_id DESC
               ) as rn
        FROM posting_balance pb
        INNER JOIN postings p ON pb.posting_id = p.posting_id
        INNER JOIN sub_accounts sa ON p.sub_account_id = sa.sub_account_id
        INNER JOIN transactions t ON p.transaction_id = t.transaction_id
        WHERE sa.account_id = :accountId
          AND t.timestamp <= :transactionTimestamp
          AND p.posting_id <> :postingId
    ) as ranked_balances
    WHERE rn = 1
  """,
  nativeQuery = true,
  )
  fun getPreviousPostingBalancesByAccount(
    postingId: UUID,
    accountId: UUID,
    transactionTimestamp: Instant,
  ): List<PostingBalanceEntity>

  fun findByPostingEntity(posting: PostingEntity): PostingBalanceEntity?
}
