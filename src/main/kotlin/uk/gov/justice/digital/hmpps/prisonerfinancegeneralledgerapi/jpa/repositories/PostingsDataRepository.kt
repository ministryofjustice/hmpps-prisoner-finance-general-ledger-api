package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections.OppositePostingProjection
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections.StatementEntryProjection
import java.time.Instant
import java.util.UUID

@Repository
interface PostingsDataRepository : JpaRepository<PostingEntity, UUID> {

  /**
   * A page of statement entries for an account, projected straight into flat DTOs.
   *
   * The optional credit/debit flags collapse to a single nullable posting type; the
   * heavy lifting is the constructor-expression query below. Opposite postings can't
   * be built here (they're a nested collection, and fetching a collection with
   * pagination forces Hibernate to page in memory), so they are loaded separately via
   * [getOppositePostingsByTransactionIds] and stitched in by the service.
   */
  fun getPostingsByAccountId(
    accountId: UUID,
    page: Pageable,
    subAccountId: UUID? = null,
    startDate: Instant? = null,
    endDate: Instant? = null,
    credit: Boolean = false,
    debit: Boolean = false,
  ): Page<StatementEntryProjection> {
    val postingType = when {
      credit && !debit -> PostingType.CR
      debit && !credit -> PostingType.DR
      else -> null
    }
    // Ordering is fixed by the query below, so drop any caller-supplied sort to stop Spring
    // Data appending a second, conflicting ORDER BY to the projection query.
    val unsortedPage = PageRequest.of(page.pageNumber, page.pageSize)
    return findStatementEntriesByAccountId(accountId, subAccountId, startDate, endDate, postingType, unsortedPage)
  }

  @Query(
    value = """
      SELECT new uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections.StatementEntryProjection(
        t.id, p.createdAt, t.timestamp, t.description, p.amount, p.type,
        sa.id, sa.reference, sa.createdBy, sa.createdAt,
        acc.id, acc.reference, acc.createdBy, acc.createdAt, acc.type,
        pb.totalSubAccountBalance, pb.totalAccountBalance
      )
      FROM PostingEntity p
      JOIN p.transactionEntity t
      JOIN p.subAccountEntity sa
      JOIN sa.parentAccountEntity acc
      LEFT JOIN p.postingBalanceEntity pb
      WHERE acc.id = :accountId
        AND sa.id = COALESCE(:subAccountId, sa.id)
        AND t.timestamp >= COALESCE(:startDate, t.timestamp)
        AND t.timestamp <= COALESCE(:endDate, t.timestamp)
        AND p.type = COALESCE(:postingType, p.type)
      ORDER BY t.timestamp DESC, t.entrySequence DESC, p.entrySequence DESC
    """,
    countQuery = """
      SELECT COUNT(p)
      FROM PostingEntity p
      JOIN p.transactionEntity t
      JOIN p.subAccountEntity sa
      JOIN sa.parentAccountEntity acc
      WHERE acc.id = :accountId
        AND sa.id = COALESCE(:subAccountId, sa.id)
        AND t.timestamp >= COALESCE(:startDate, t.timestamp)
        AND t.timestamp <= COALESCE(:endDate, t.timestamp)
        AND p.type = COALESCE(:postingType, p.type)
    """,
  )
  fun findStatementEntriesByAccountId(
    accountId: UUID,
    subAccountId: UUID?,
    startDate: Instant?,
    endDate: Instant?,
    postingType: PostingType?,
    pageable: Pageable,
  ): Page<StatementEntryProjection>

  /**
   * Every posting on the given transactions, projected into flat DTOs. Callers group by
   * transaction id and keep the postings of the opposite type to form each statement
   * entry's "other side". Ordered to match the entity's `@OrderBy("entrySequence DESC")`.
   */
  @Query(
    """
      SELECT new uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections.OppositePostingProjection(
        t.id, p.id, p.createdBy, p.createdAt, p.type, p.amount,
        sa.id, sa.reference, sa.createdBy, sa.createdAt,
        acc.id, acc.reference, acc.createdBy, acc.createdAt, acc.type
      )
      FROM PostingEntity p
      JOIN p.transactionEntity t
      JOIN p.subAccountEntity sa
      JOIN sa.parentAccountEntity acc
      WHERE t.id IN :transactionIds
      ORDER BY t.id, p.entrySequence DESC
    """,
  )
  fun getOppositePostingsByTransactionIds(transactionIds: Collection<UUID>): List<OppositePostingProjection>

  @EntityGraph(
    attributePaths = [
      "transactionEntity",
      "subAccountEntity",
      "subAccountEntity.parentAccountEntity",
      "subAccountEntity.parentAccountEntity.subAccounts",
      "postingBalanceEntity",
    ],
  )
  @Query(
    """
        SELECT p
        FROM PostingEntity AS p
        WHERE 
            p.postingBalanceEntity IS NULL AND 
            p.subAccountEntity.parentAccountEntity.id = :accountId
        ORDER BY p.transactionEntity.timestamp, p.transactionEntity.entrySequence, p.entrySequence, p.id
        LIMIT 1
      """,
  )
  fun getFirstMissingPostingBalanceByAccountId(accountId: UUID): PostingEntity?

  @Query("SELECT p FROM PostingEntity p WHERE p.subAccountEntity.id = :subAccountId")
  fun getPostingsForSubAccountId(@Param("subAccountId") subAccountId: UUID): List<PostingEntity>

  @Query("SELECT p FROM PostingEntity p WHERE p.subAccountEntity.id = :subAccountId AND p.transactionEntity.timestamp > :dateTime")
  fun getPostingsForSubAccountIdAfterDateTime(@Param("subAccountId") subAccountId: UUID, @Param("dateTime") dateTime: Instant): List<PostingEntity>

  @EntityGraph(
    attributePaths = [
      "transactionEntity",
      "subAccountEntity",
      "subAccountEntity.parentAccountEntity",
      "subAccountEntity.parentAccountEntity.subAccounts",
      "postingBalanceEntity",
    ],
  )
  @Query(
    """
    SELECT p FROM PostingEntity p 
    WHERE 
        p.subAccountEntity.parentAccountEntity.id = :accountId AND
        p.transactionEntity.timestamp > :dateTime
    order by p.transactionEntity.timestamp, p.transactionEntity.entrySequence, p.entrySequence, p.id
    limit 1
    """,
  )
  fun getFirstPostingForAccountIdAfterDateTime(accountId: UUID, dateTime: Instant): PostingEntity?

  fun getBalanceForSubAccount(subAccountId: UUID, latestStatementBalanceDateTime: Instant? = null): Long {
    lateinit var postingsForSubAccount: List<PostingEntity>

    if (latestStatementBalanceDateTime == null) {
      postingsForSubAccount = getPostingsForSubAccountId(subAccountId)
    } else {
      postingsForSubAccount = getPostingsForSubAccountIdAfterDateTime(subAccountId, latestStatementBalanceDateTime)
    }

    val balance = calculateBalanceFromPostings(postingsForSubAccount)

    return balance
  }

  fun calculateBalanceFromPostings(postings: List<PostingEntity>): Long {
    val balance = postings.fold(0L) { acc, posting -> acc + (if (posting.type == PostingType.CR) posting.amount else -posting.amount) }
    return balance
  }

  @Query(
    """
WITH qualifying_transactions AS (  
SELECT p.transaction_id  
FROM postings p 
JOIN sub_accounts sa ON sa.sub_account_id = p.sub_account_id  
WHERE sa.account_id IN (:prisonerId, :prisonId)  
GROUP BY p.transaction_id  
HAVING COUNT(DISTINCT sa.account_id) = 2)  
SELECT COALESCE(SUM(
     CASE
         WHEN p.type = 'CR' THEN p.amount   
         WHEN p.type = 'DR' THEN -p.amount
         ELSE 0
      END
        ), 0) AS balance  
FROM postings p 
JOIN sub_accounts sa ON sa.sub_account_id = p.sub_account_id  
JOIN qualifying_transactions qt ON qt.transaction_id = p.transaction_id  
WHERE sa.account_id = :prisonerId
""",
    nativeQuery = true,
  )
  fun getBalanceForAPrisonerAtAPrison(@Param("prisonId") prisonId: UUID, @Param("prisonerId") prisonerId: UUID): Long

  @EntityGraph(
    attributePaths = [
      "transactionEntity",
      "subAccountEntity",
      "subAccountEntity.parentAccountEntity",
      "subAccountEntity.parentAccountEntity.subAccounts",
      "postingBalanceEntity",
    ],
  )
  // the last OR is a workaround entrySequences that zero due to old data in dev
  @Query(
    """
    SELECT p
    FROM PostingEntity p
    WHERE p.subAccountEntity.parentAccountEntity.id = :accountId
      AND (
          p.transactionEntity.timestamp > :transactionTimestamp
          OR (
              p.transactionEntity.timestamp = :transactionTimestamp
              AND p.transactionEntity.entrySequence > :transactionEntrySequence
          )
          OR (
              p.transactionEntity.timestamp = :transactionTimestamp
              AND p.transactionEntity.entrySequence = :transactionEntrySequence
              AND p.entrySequence > :postingEntrySequence
          )
          OR (
              p.transactionEntity.timestamp = :transactionTimestamp
              AND p.transactionEntity.entrySequence = :transactionEntrySequence
              AND p.entrySequence = :postingEntrySequence
              AND p.id > :postingId
        )
      )
    ORDER BY
        p.transactionEntity.timestamp ASC,
        p.transactionEntity.entrySequence ASC,
        p.entrySequence ASC,
        p.id ASC
    LIMIT 1
  """,
  )
  fun getTheNextAccountPostingOrNull(
    postingId: UUID,
    accountId: UUID,
    transactionTimestamp: Instant,
    transactionEntrySequence: Long,
    postingEntrySequence: Long,
  ): PostingEntity?
}
