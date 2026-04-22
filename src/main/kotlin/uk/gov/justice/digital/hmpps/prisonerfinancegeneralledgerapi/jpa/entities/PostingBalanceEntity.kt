package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "posting_balance")
class PostingBalanceEntity(
  @Id
  @Column(name = "posting_balance_id", nullable = false, unique = true)
  val id: UUID = UUID.randomUUID(),

  @OneToOne(optional = false, fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "posting_id", nullable = false)
  val postingEntity: PostingEntity,

  @Column(name = "total_sub_account_balance", nullable = false)
  val totalSubAccountBalance: Long = 0,

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now(),
)
