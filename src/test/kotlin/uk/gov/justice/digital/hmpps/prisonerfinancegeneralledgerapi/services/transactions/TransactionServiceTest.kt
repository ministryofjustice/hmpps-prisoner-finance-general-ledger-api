package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.transactions

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.TransactionService

private const val TEST_TREF = "TEST_ACCOUNT_REF"

@ExtendWith(MockitoExtension::class)
class TransactionServiceTest {

  @Mock
  lateinit var transactionDataRepository: TransactionDataRepository

  @InjectMocks
  lateinit var transactionService: TransactionService

  lateinit var dummyTransaction: TransactionEntity

  @BeforeEach
  fun setupDummyTransaction() {
    dummyTransaction = TransactionEntity()
  }

  @Nested
  inner class CreateTransaction {
    @Test
    fun `Should call the repository to save the transaction and return it`() {
      whenever(transactionDataRepository.save(any())).thenReturn(dummyTransaction)
    }
  }
}
