package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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
    SELECT pb
    FROM PostingBalanceEntity pb
    JOIN (
        SELECT 
            inner_pb.id as id,
            ROW_NUMBER() OVER (
                PARTITION BY inner_pb.postingEntity.subAccountEntity.id 
                ORDER BY 
                    inner_pb.postingEntity.transactionEntity.timestamp DESC,
                    inner_pb.postingEntity.transactionEntity.entrySequence DESC,
                    inner_pb.postingEntity.entrySequence DESC,
                    inner_pb.postingEntity.id DESC
            ) as rn
        FROM PostingBalanceEntity inner_pb 
        WHERE inner_pb.postingEntity.subAccountEntity.parentAccountEntity.id = :accountId  
          AND inner_pb.postingEntity.id <> :postingId
          AND (
              inner_pb.postingEntity.transactionEntity.timestamp < :transactionTimestamp
              OR (
                  inner_pb.postingEntity.transactionEntity.timestamp = :transactionTimestamp
                  AND inner_pb.postingEntity.transactionEntity.entrySequence < :transactionEntrySequence
              )
              OR (
                  inner_pb.postingEntity.transactionEntity.timestamp = :transactionTimestamp
                  AND inner_pb.postingEntity.transactionEntity.entrySequence = :transactionEntrySequence
                  AND inner_pb.postingEntity.entrySequence < :postingEntrySequence
              )
              OR (
                  inner_pb.postingEntity.transactionEntity.timestamp = :transactionTimestamp
                  AND inner_pb.postingEntity.transactionEntity.entrySequence = :transactionEntrySequence
                  AND inner_pb.postingEntity.entrySequence = :postingEntrySequence
                  AND inner_pb.postingEntity.id < :postingId
              )
          )
    ) AS ranked ON pb.id = ranked.id
    WHERE ranked.rn = 1
  """,
  )
  fun getPreviousPostingBalancesByAccount(
    postingId: UUID,
    accountId: UUID,
    transactionTimestamp: Instant,
    transactionEntrySequence: Long,
    postingEntrySequence: Long,
  ): List<PostingBalanceEntity>

  fun findByPostingEntity(posting: PostingEntity): PostingBalanceEntity?

  @Modifying
  @Query(
    value = """
      DELETE FROM posting_balance
      WHERE posting_id IN (
          SELECT p.posting_id
          FROM postings p
          JOIN sub_accounts sa ON sa.sub_account_id = p.sub_account_id
          JOIN transactions t ON t.transaction_id = p.transaction_id
          WHERE sa.account_id = :accountId
            AND t.timestamp >= :timestamp
      )
    """,
    nativeQuery = true,
  )
  fun deleteFromTimestampByAccountId(
    accountId: UUID,
    timestamp: Instant,
  )
}
