package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import java.util.UUID

@Repository
interface TransactionDataRepository : JpaRepository<TransactionEntity, UUID> {
  fun findTransactionById(id: UUID): TransactionEntity?
}
