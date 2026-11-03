package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import java.util.*

@Repository
interface AccountDataRepository : JpaRepository<AccountEntity, UUID> {
  @EntityGraph(
    attributePaths = [
      "subAccounts",
    ],
  )
  fun findAccountById(id: UUID): AccountEntity?

  @EntityGraph(
    attributePaths = [
      "subAccounts",
    ],
  )
  fun findAccountByReference(
    reference: String,
  ): AccountEntity?

  @EntityGraph(
    attributePaths = [
      "subAccounts",
    ],
  )
  fun findAccountEntitiesByReferenceIn(references: List<String>): List<AccountEntity>
}
