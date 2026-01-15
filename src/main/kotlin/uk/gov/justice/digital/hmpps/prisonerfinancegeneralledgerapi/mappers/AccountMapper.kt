package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.mappers

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.dto.AccountDTO

class AccountMapper {

  fun toDTO(accountEntity: AccountEntity): AccountDTO = AccountDTO(accountEntity.id, accountEntity.reference, accountEntity.createdAt, accountEntity.createdBy)
}
