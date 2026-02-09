package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import java.util.UUID

@Repository
interface StatementBalanceDataRepository : JpaRepository<StatementBalanceEntity, UUID> {

  @Query(
    """
    SELECT * FROM statement_balances 
    WHERE statement_balances.sub_account_id = :subAccountId
    ORDER BY balance_date_time DESC 
    LIMIT 1
    """,
    nativeQuery = true,
  )
  fun getLatestStatementBalanceForSubAccountId(subAccountId: UUID): StatementBalanceEntity?
}
