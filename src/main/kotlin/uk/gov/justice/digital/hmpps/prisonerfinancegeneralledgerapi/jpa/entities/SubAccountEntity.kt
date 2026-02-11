package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
  name = "sub_accounts",
  uniqueConstraints = [
    UniqueConstraint(
      name = "sub_account_unique_within_account",
      columnNames = ["account_id", "reference"],
    ),
  ],
)
class SubAccountEntity(

  @Id
  @Column(name = "sub_account_id", nullable = false, unique = true)
  val id: UUID = UUID.randomUUID(),

  @ManyToOne(optional = false, fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  val parentAccountEntity: AccountEntity = AccountEntity(),

  @Column(name = "reference", nullable = false)
  val reference: String = "",

  @Column(name = "created_by", nullable = false)
  val createdBy: String = "",

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now(),

)
