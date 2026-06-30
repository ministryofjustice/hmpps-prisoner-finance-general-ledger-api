package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.LogSqsBalancesStatusType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "log_sqs_calculated_balances")
class LogSqsCalculatedBalances(

  @Id
  val id: UUID = UUID.randomUUID(),

  val postingId: UUID,

  val accountId: UUID,

  @Enumerated(EnumType.STRING)
  val status: LogSqsBalancesStatusType,

  val timestamp: Instant,
)
