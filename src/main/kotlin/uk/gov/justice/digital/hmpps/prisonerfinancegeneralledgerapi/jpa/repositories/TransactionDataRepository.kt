package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import java.util.UUID

@Repository
interface TransactionDataRepository : JpaRepository<TransactionEntity, UUID> {
  fun findTransactionById(id: UUID): TransactionEntity?

  @Query(
    """
    SELECT t
    FROM TransactionEntity t
    WHERE EXISTS (
        SELECT 1
        FROM PostingEntity p
        JOIN p.subAccountEntity sa
        WHERE p.transactionEntity = t
          AND sa.parentAccountEntity.id = :accountId 
    )
    ORDER BY t.timestamp DESC
    """,
  )
  fun findTransactionsByAccountId(@Param("accountId") accountId: UUID): List<TransactionEntity>
}
