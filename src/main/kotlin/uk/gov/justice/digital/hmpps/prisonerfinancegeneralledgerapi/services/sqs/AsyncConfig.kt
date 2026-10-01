package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.sqs

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
class AsyncConfig {

  @Bean(name = ["balanceProcessingExecutor"])
  fun balanceProcessingExecutor(): Executor {
    val executor = ThreadPoolTaskExecutor()
    executor.corePoolSize = 10 // Minimum number of threads to keep alive
    executor.maxPoolSize = 20 // Maximum number of threads to spawn
    executor.queueCapacity = 50 // How many tasks to queue before rejecting
    executor.setThreadNamePrefix("BalanceExec-")
    executor.initialize()
    return executor
  }
}
