package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "transactions")
class TransactionEntity(

  @Id
  @Column(name = "transaction_id", nullable = false, unique = true)
  val id: UUID = UUID.randomUUID(),

  @Column(name = "created_by", nullable = false)
  val createdBy: String = "",

  @Column(name = "created_at", nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @Column(name = "reference", nullable = false)
  val reference: String = "",

  @Column(name = "description")
  val description: String = "",

  @Column(name = "timestamp", nullable = false)
  val timestamp: LocalDateTime = LocalDateTime.now(),

  @Column(name = "amount", nullable = false, unique = true)
  val amount: BigInteger = BigInteger.ZERO,
){
  @OneToMany(mappedBy = "transactionEntity")
  val postings = mutableListOf<PostingEntity>()
}
