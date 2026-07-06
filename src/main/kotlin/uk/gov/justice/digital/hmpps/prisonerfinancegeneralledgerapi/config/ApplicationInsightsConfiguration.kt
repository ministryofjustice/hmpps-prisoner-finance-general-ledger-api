package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * TelemetryClient gets altered at runtime by the java agent and so is a no-op otherwise
 */

const val TELEMETRY_PREFIX = "prisoner-finance-general-ledger"

@Configuration
class ApplicationInsightsConfiguration {
  @Bean
  fun telemetryClient(): TelemetryClient = TelemetryClient()
}
