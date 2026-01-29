package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import java.util.UUID

@Repository
interface PostingsDataRepository : JpaRepository<PostingEntity, UUID> {

  @Query(
    """
    SELECT COALESCE(SUM(
     CASE
         WHEN p.type = 'CR' THEN p.amount
         WHEN p.type = 'DR' THEN -p.amount
         ELSE 0
      END
        ), 0) AS balance
    FROM postings as p
    WHERE p.sub_account_id = :subAccountId;
  """,
    nativeQuery = true,
  )
  fun getBalanceForSubAccount(@Param("subAccountId") subAccountId: UUID): Long

  @Query(
    """
          SELECT COALESCE(SUM(
     CASE
         WHEN p.type = 'CR' THEN p.amount
         WHEN p.type = 'DR' THEN -p.amount
         ELSE 0
      END
        ), 0) AS balance FROM accounts as a 
        JOIN sub_accounts as s ON a.account_id = s.account_id 
        JOIN postings as p ON p.sub_account_id = s.sub_account_id
        WHERE a.account_id = :accountId 
    """,
    nativeQuery = true,
  )
  fun getBalanceForAccount(@Param("accountId") accountId: UUID): Long

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
}
