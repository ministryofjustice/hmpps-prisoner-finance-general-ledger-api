package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun LocalDate.toUtcStartOfDay(): Instant = this.atStartOfDay().atZone(ZoneOffset.UTC).toInstant()
fun LocalDate.toUtcEndOfDay(): Instant = this.atStartOfDay().plusDays(1).minusSeconds(1).atZone(ZoneOffset.UTC).toInstant()
