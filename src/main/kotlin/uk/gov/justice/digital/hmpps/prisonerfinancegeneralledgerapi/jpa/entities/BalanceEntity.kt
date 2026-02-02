package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
  name = "balances",
  indexes = [Index(name = "idx_balance_sub_account_id", columnList = "sub_account_id")],
)
class BalanceEntity(

  @Id
  @Column(name = "balance_id", nullable = false, unique = true)
  val id: UUID = UUID.randomUUID(),

  @ManyToOne(optional = false, fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "sub_account_id", nullable = false)
  val subAccountEntity: SubAccountEntity,

  @Column(name = "balance_date_time", nullable = false)
  val balanceDateTime: LocalDateTime = LocalDateTime.now(),

  @Column(name = "amount", nullable = false)
  val amount: Long,
)
