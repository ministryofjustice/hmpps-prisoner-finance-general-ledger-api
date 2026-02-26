package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import java.time.Instant
import java.util.UUID

@Entity
@Table(
  name = "accounts",
  indexes = [
    Index(name = "idx_reference", columnList = "reference"),
  ],
)
class AccountEntity(

  @Id
  @Column(name = "account_id", nullable = false, unique = true)
  val id: UUID = UUID.randomUUID(),

  @Column(name = "created_by", nullable = false)
  val createdBy: String = "",

  @Column(name = "created_at", nullable = false)
  val createdAt: Instant = Instant.now(),

  @Column(name = "reference", nullable = false, unique = true)
  val reference: String = "",

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = true)
  val type: AccountType = AccountType.PRISONER,

) {
  @OneToMany(mappedBy = "parentAccountEntity")
  val subAccounts: MutableList<SubAccountEntity> = mutableListOf()
}
