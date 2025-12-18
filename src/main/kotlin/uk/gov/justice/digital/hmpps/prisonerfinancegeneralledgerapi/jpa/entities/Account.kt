package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "accounts")
data class Account(

  @Id
  @Column(name = "account_id", nullable = false, unique = true)
  val uuid: UUID = UUID.randomUUID(),

  @Column(name = "created_by", nullable = false)
  val createdBy: String,

  @Column(name = "created_at", nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @Column(name = "reference", nullable = false, unique = true)
  val reference: String,

)
