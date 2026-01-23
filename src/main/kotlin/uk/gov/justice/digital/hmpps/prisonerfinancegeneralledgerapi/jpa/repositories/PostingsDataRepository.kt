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
        ), 0) AS net_amount
    FROM postings as p
    WHERE p.sub_account_id = :subAccountId;
  """,
    nativeQuery = true,
  )
  fun getNetAmountForSubAccount(@Param("subAccountId") subAccountId: UUID): Long
}
