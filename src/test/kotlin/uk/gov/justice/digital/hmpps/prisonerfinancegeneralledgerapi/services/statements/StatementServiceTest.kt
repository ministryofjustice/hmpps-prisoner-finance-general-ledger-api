package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.statements

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.StatementService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class StatementServiceTest {

  @Mock
  lateinit var postingsDataRepository: PostingsDataRepository

  @InjectMocks
  lateinit var statementService: StatementService

  private val serviceTestHelpers = ServiceTestHelpers()

  @Nested
  inner class GetStatement {

    @Test
    fun `should return empty list when no postings for prisoner`() {
      val prisonerId = UUID.randomUUID()
      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId) }.thenReturn(emptyList())

      val postings = statementService.listStatementEntries(prisonerId)

      assertThat(postings).isEmpty()
    }

    @Test
    fun `should return list of postings for prisoner to prisoner transaction`() {
      val prisonerId = UUID.randomUUID()

      val accountEntity = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val subAccountCashEntity = serviceTestHelpers.createSubAccount("CASH", accountEntity)
      val subAccountSpendsEntity = serviceTestHelpers.createSubAccount("SPENDS", accountEntity)

      val transactionEntity =
        serviceTestHelpers.createOneToOneTransaction(1L, Instant.now(), subAccountCashEntity, subAccountSpendsEntity)

      val posting1 = transactionEntity.postings[0]
      val posting2 = transactionEntity.postings[1]

      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId) }
        .thenReturn(
          listOf(
            posting1,
            posting2,
          ),
        )

      val statementEntries = statementService.listStatementEntries(prisonerId)

      assertThat(statementEntries).hasSize(2)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount).isEqualTo(subAccountCashEntity)
      assertThat(statementEntries[0].oppositePostings).isEqualTo(listOf(posting2))
      assertThat(statementEntries[1].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[1].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[1].postingCreatedAt).isEqualTo(posting2.createdAt)
      assertThat(statementEntries[1].subAccount).isEqualTo(subAccountSpendsEntity)
      assertThat(statementEntries[1].oppositePostings).isEqualTo(listOf(posting1))
    }

    @Test
    fun `should return list of postings for prisoner to prison transaction`() {
      val prisonerId = UUID.randomUUID()

      val accountEntity = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val subAccountCashEntity = serviceTestHelpers.createSubAccount("CASH", accountEntity)

      val prisonAccountEntity = serviceTestHelpers.createAccount("LEI", AccountType.PRISON)
      val subAccountPrisonEntity = serviceTestHelpers.createSubAccount("1001:CANT", prisonAccountEntity)

      val transactionEntity =
        serviceTestHelpers.createOneToOneTransaction(1L, Instant.now(), subAccountCashEntity, subAccountPrisonEntity)

      val posting1 = transactionEntity.postings[0]
      val posting2 = transactionEntity.postings[1]

      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId) }
        .thenReturn(
          listOf(
            posting1,
          ),
        )

      val statementEntries = statementService.listStatementEntries(prisonerId)

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount).isEqualTo(subAccountCashEntity)
      assertThat(statementEntries[0].oppositePostings).isEqualTo(listOf(posting2))
    }

    @Test
    fun `should return list of multiple postings for many to one transaction for prison account`() {
      val prisonerId = UUID.randomUUID()

      val accountEntityOne = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val accountEntityTwo = serviceTestHelpers.createAccount("ABC123DD", AccountType.PRISONER)
      val subAccountCashEntityOne = serviceTestHelpers.createSubAccount("CASH", accountEntityOne)
      val subAccountCashEntityTwo = serviceTestHelpers.createSubAccount("CASH", accountEntityTwo)

      val prisonAccountEntity = serviceTestHelpers.createAccount("LEI", AccountType.PRISON)
      val subAccountPrisonEntity = serviceTestHelpers.createSubAccount("1001:CANT", prisonAccountEntity)

      val transactionEntity =
        serviceTestHelpers.createOneToManyTransaction(
          ref = "CANTEEN",
          debitSubAccount = subAccountPrisonEntity,
          creditSubAccounts = listOf(subAccountCashEntityOne, subAccountCashEntityTwo),
          amountToCreditEachSubAccount = 10L,
        )

      val prisonPosting = transactionEntity.postings[0]
      val posting1 = transactionEntity.postings[1]
      val posting2 = transactionEntity.postings[2]

      whenever { postingsDataRepository.getPostingsByAccountId(accountId = prisonAccountEntity.id) }
        .thenReturn(
          listOf(
            prisonPosting,
          ),
        )

      val statementEntries = statementService.listStatementEntries(accountId = prisonAccountEntity.id)

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(prisonPosting.createdAt)
      assertThat(statementEntries[0].subAccount).isEqualTo(subAccountPrisonEntity)
      assertThat(statementEntries[0].oppositePostings).isEqualTo(listOf(posting1, posting2))
    }

    @Test
    fun `should return list of multiple postings for many to one transaction for prisoner account`() {
      val prisonerId = UUID.randomUUID()

      val accountEntityOne = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val accountEntityTwo = serviceTestHelpers.createAccount("ABC123DD", AccountType.PRISONER)
      val subAccountCashEntityOne = serviceTestHelpers.createSubAccount("CASH", accountEntityOne)
      val subAccountCashEntityTwo = serviceTestHelpers.createSubAccount("CASH", accountEntityTwo)

      val prisonAccountEntity = serviceTestHelpers.createAccount("LEI", AccountType.PRISON)
      val subAccountPrisonEntity = serviceTestHelpers.createSubAccount("1001:CANT", prisonAccountEntity)

      val transactionEntity =
        serviceTestHelpers.createOneToManyTransaction(
          ref = "CANTEEN",
          debitSubAccount = subAccountPrisonEntity,
          creditSubAccounts = listOf(subAccountCashEntityOne, subAccountCashEntityTwo),
          amountToCreditEachSubAccount = 10L,
        )

      val prisonPosting = transactionEntity.postings[0]
      val posting1 = transactionEntity.postings[1]
      val posting2 = transactionEntity.postings[2]

      whenever { postingsDataRepository.getPostingsByAccountId(accountId = accountEntityOne.id) }
        .thenReturn(
          listOf(
            posting1,
          ),
        )

      val statementEntries = statementService.listStatementEntries(accountId = accountEntityOne.id)

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount).isEqualTo(subAccountCashEntityOne)
      assertThat(statementEntries[0].oppositePostings).isEqualTo(listOf(prisonPosting))
    }
  }
}
