package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "idempotency_keys")
class IdempotencyEntity(

  @Id
  @Column(name = "id", nullable = false, unique = true)
  val idempotencyKey: UUID,

  @OneToOne(optional = false, fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false)
  val transaction: TransactionEntity,

)
