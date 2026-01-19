package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import java.util.*

@Repository
interface AccountDataRepository : JpaRepository<AccountEntity, UUID> {
  fun findAccountById(id: UUID): AccountEntity?
  fun findAccountsByReference(
    reference: String,
  ): List<AccountEntity>
}
