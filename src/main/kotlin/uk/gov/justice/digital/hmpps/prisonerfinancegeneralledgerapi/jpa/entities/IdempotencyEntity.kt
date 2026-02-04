package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "idempotency_keys")
class IdempotencyEntity(

  @Id
  @Column(name = "id", nullable = false, unique = true)
  val idempotencyKey: UUID,

)
