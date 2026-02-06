package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.IdempotencyEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.IdempotencyKeyDataRepository
import java.util.UUID

@Service
class IdempotencyService(
  private val idempotencyKeyDataRepository: IdempotencyKeyDataRepository,
) {

  fun readIdempotencyKey(idempotencyKey: UUID): IdempotencyEntity? = idempotencyKeyDataRepository.getIdempotencyEntityByIdempotencyKey(idempotencyKey)
}
