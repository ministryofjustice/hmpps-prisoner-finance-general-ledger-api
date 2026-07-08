package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import java.util.UUID

@Repository
interface TransactionDataRepository : JpaRepository<TransactionEntity, UUID> {

  @EntityGraph(
    attributePaths = [
      "postings",
      "postings.subAccountEntity",
      "postings.subAccountEntity.parentAccountEntity",
    ],
  )
  fun findTransactionById(id: UUID): TransactionEntity?

  @EntityGraph(
    attributePaths = [
      "postings",
      "postings.subAccountEntity",
      "postings.subAccountEntity.parentAccountEntity",
    ],
  )
  @Query(
    """
    SELECT t
    FROM TransactionEntity t
    WHERE t.id IN(:transactionIds)
    ORDER BY t.timestamp DESC, t.entrySequence DESC, t.id DESC
  """,
  )
  fun findTransactionsByIds(transactionIds: List<UUID>, page: Pageable): Page<TransactionEntity>

  @EntityGraph(
    attributePaths = [
      "postings",
      "postings.subAccountEntity",
      "postings.subAccountEntity.parentAccountEntity",
    ],
  )
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
  ORDER BY t.timestamp DESC, t.entrySequence DESC
  """,
  )
  fun findTransactionsByAccountId(@Param("accountId") accountId: UUID): List<TransactionEntity>
}
