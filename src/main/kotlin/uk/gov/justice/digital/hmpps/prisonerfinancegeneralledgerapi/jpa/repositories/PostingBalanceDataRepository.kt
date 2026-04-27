package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import java.time.Instant
import java.util.UUID

@Repository
interface PostingBalanceDataRepository : JpaRepository<PostingBalanceEntity, Long> {

  @Query(
    """
      select
        pb
      from PostingBalanceEntity pb 
      where pb.postingEntity.subAccountEntity.id = :subAccountId and 
            pb.postingEntity.transactionEntity.timestamp <= :transactionTimestamp
      order by 
            pb.postingEntity.transactionEntity.timestamp desc,
            pb.postingEntity.transactionEntity.entrySequence desc,
            pb.postingEntity.entrySequence desc
      limit 1
    """,
  )
  fun getSubAccountBalanceOrDefault(subAccountId: UUID, transactionTimestamp: Instant): PostingBalanceEntity?
}
