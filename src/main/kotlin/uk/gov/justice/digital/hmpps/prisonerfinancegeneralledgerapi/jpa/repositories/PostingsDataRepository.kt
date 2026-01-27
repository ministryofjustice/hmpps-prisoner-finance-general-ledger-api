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
}
