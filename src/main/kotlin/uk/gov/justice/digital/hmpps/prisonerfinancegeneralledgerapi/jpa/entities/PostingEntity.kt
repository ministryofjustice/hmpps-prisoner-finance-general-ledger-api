package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "postings", indexes = [Index(name = "index_postings_transactions_id", columnList = "transaction_id")])
class PostingEntity(

  @Id
  @Column(name = "posting_id", nullable = false, unique = true)
  val id: UUID = UUID.randomUUID(),

  @Column(name = "created_by", nullable = false)
  val createdBy: String = "",

  @Column(name = "created_at", nullable = false)
  val createdAt: LocalDateTime = LocalDateTime.now(),

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  val type: PostingType = PostingType.CR,

  @Column(name = "amount", nullable = false)
  val amount: Long = 0,

  @ManyToOne(optional = false, fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "sub_account_id", nullable = false)
  val subAccountEntity: SubAccountEntity = SubAccountEntity(),

  @ManyToOne(optional = false, fetch = jakarta.persistence.FetchType.LAZY)
  @JoinColumn(name = "transaction_id", nullable = false)
  val transactionEntity: TransactionEntity = TransactionEntity(),
)
