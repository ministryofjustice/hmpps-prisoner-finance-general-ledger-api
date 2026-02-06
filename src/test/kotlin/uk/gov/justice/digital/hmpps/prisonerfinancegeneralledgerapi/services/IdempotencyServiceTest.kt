package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.IdempotencyEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.IdempotencyKeyDataRepository
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class IdempotencyServiceTest {
  @Mock
  lateinit var idempotencyKeyDataRepository: IdempotencyKeyDataRepository

  @InjectMocks
  lateinit var idempotencyService: IdempotencyService

  @Nested
  inner class ReaddIdempotencyKey {

    @Test
    fun `Should return null if no idempotency key exists`() {
      val idempotencyKey = UUID.randomUUID()

      whenever(idempotencyKeyDataRepository.getIdempotencyEntityByIdempotencyKey(idempotencyKey)).thenReturn(null)

      val idempotencyEntityOrNull = idempotencyService.readIdempotencyKey(idempotencyKey)

      assert(idempotencyEntityOrNull == null)
    }

    @Test
    fun `Should return idempotency entity if it exists`() {
      val idempotencyKey = UUID.randomUUID()
      val tx = TransactionEntity(reference = "TEST", amount = 0L)

      val idempotencyEntity = IdempotencyEntity(idempotencyKey, tx)

      whenever(idempotencyKeyDataRepository.getIdempotencyEntityByIdempotencyKey(idempotencyKey)).thenReturn(idempotencyEntity)

      val idempotencyEntityOrNull = idempotencyService.readIdempotencyKey(idempotencyKey)

      assertThat(idempotencyEntityOrNull!!.idempotencyKey).isEqualTo(idempotencyKey)
      assertThat(idempotencyEntityOrNull.transaction).isEqualTo(tx)
    }
  }
}
