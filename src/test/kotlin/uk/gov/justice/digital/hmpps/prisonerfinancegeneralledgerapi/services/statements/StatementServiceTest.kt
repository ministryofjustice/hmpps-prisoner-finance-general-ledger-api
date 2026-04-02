package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.statements

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.StatementService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class StatementServiceTest {

  @Mock
  lateinit var postingsDataRepository: PostingsDataRepository

  @InjectMocks
  lateinit var statementService: StatementService

  @Mock
  lateinit var accountService: AccountService

  private val serviceTestHelpers = ServiceTestHelpers()

  private val pageReq = PageRequest.of(0, 25, Sort.Direction.DESC, "transactionEntity.timestamp")

  @Nested
  inner class GetStatement {

    @Test
    fun `should return null if account does not exist`() {
      val prisonerId = UUID.randomUUID()
      whenever { accountService.readAccount(prisonerId) }.thenReturn(null)

      val postings = statementService.listStatementEntries(prisonerId)

      assertThat(postings).isNull()
    }

    @Test
    fun `should return empty list when no postings for an account`() {
      val prisonerId = UUID.randomUUID()
      whenever { accountService.readAccount(prisonerId) }.thenReturn(AccountEntity(id = prisonerId))
      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId, pageReq) }.thenReturn(Page.empty())

      val postings = statementService.listStatementEntries(prisonerId)

      assertThat(postings!!.content).isEmpty()
    }

    @Test
    fun `should return list of postings for a single account with one transaction between sub-accounts`() {
      val prisonerId = UUID.randomUUID()
      whenever { accountService.readAccount(prisonerId) }.thenReturn(AccountEntity(id = prisonerId))

      val accountEntity = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val subAccountCashEntity = serviceTestHelpers.createSubAccount("CASH", accountEntity)
      val subAccountSpendsEntity = serviceTestHelpers.createSubAccount("SPENDS", accountEntity)

      val transactionEntity =
        serviceTestHelpers.createOneToOneTransaction(1L, Instant.now(), subAccountCashEntity, subAccountSpendsEntity)

      val posting1 = transactionEntity.postings[0]
      val posting2 = transactionEntity.postings[1]

      val page = PageImpl(listOf(posting1, posting2))

      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId, pageReq) }
        .thenReturn(page)

      val statementEntries = statementService.listStatementEntries(prisonerId)?.content!!

      assertThat(statementEntries).hasSize(2)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountCashEntity.id)
      assertThat(statementEntries[0].oppositePostings[0].id).isEqualTo(posting2.id)
      assertThat(statementEntries[1].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[1].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[1].postingCreatedAt).isEqualTo(posting2.createdAt)
      assertThat(statementEntries[1].subAccount.id).isEqualTo(subAccountSpendsEntity.id)
      assertThat(statementEntries[1].oppositePostings[0].id).isEqualTo(posting1.id)
    }

    @Test
    fun `should return list of postings for a one to one transaction between accounts`() {
      val prisonerId = UUID.randomUUID()

      val accountEntity = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      whenever { accountService.readAccount(any<UUID>()) }.thenReturn(AccountEntity())

      val subAccountCashEntity = serviceTestHelpers.createSubAccount("CASH", accountEntity)

      val prisonAccountEntity = serviceTestHelpers.createAccount("LEI", AccountType.PRISON)
      val subAccountPrisonEntity = serviceTestHelpers.createSubAccount("1001:CANT", prisonAccountEntity)

      val transactionEntity =
        serviceTestHelpers.createOneToOneTransaction(1L, Instant.now(), subAccountCashEntity, subAccountPrisonEntity)

      val posting1 = transactionEntity.postings[0]
      val posting2 = transactionEntity.postings[1]

      val page = PageImpl(listOf(posting1))

      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId, pageReq) }
        .thenReturn(page)

      val statementEntries = statementService.listStatementEntries(prisonerId)?.content!!

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountCashEntity.id)
      assertThat(statementEntries[0].oppositePostings[0].id).isEqualTo(posting2.id)
    }

    @Test
    fun `should return list of multiple postings for many to one transaction`() {
      whenever { accountService.readAccount(any<UUID>()) }.thenReturn(AccountEntity())

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

      val page = PageImpl(listOf(prisonPosting))

      whenever { postingsDataRepository.getPostingsByAccountId(accountId = prisonAccountEntity.id, pageReq) }
        .thenReturn(page)

      val statementEntries = statementService.listStatementEntries(accountId = prisonAccountEntity.id)?.content!!

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(prisonPosting.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountPrisonEntity.id)
      assertThat(statementEntries[0].oppositePostings).hasSize(2)
      assertThat(statementEntries[0].oppositePostings.map { posting -> posting.id }).isEqualTo(listOf(posting1.id, posting2.id))
    }

    @Test
    fun `should return list of multiple postings for many to one transaction for an account`() {
      whenever { accountService.readAccount(any<UUID>()) }.thenReturn(AccountEntity())

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

      val page = PageImpl(listOf(posting1))

      whenever { postingsDataRepository.getPostingsByAccountId(accountId = accountEntityOne.id, pageReq) }
        .thenReturn(page)

      val statementEntries = statementService.listStatementEntries(accountId = accountEntityOne.id)?.content!!

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountCashEntityOne.id)
      assertThat(statementEntries[0].oppositePostings[0].id).isEqualTo(prisonPosting.id)
    }

    @Test
    fun `should pass null to the repository if no dates are provided`() {
      val prisonerId = UUID.randomUUID()
      whenever { accountService.readAccount(accountUUID = prisonerId) }.thenReturn(AccountEntity(id = prisonerId))
      whenever { postingsDataRepository.getPostingsByAccountId(accountId = prisonerId, pageReq) }.thenReturn(PageImpl(emptyList()))

      val postings = statementService.listStatementEntries(accountId = prisonerId)?.content!!

      assertThat(postings).isEmpty()
      verify(postingsDataRepository, times(1)).getPostingsByAccountId(
        accountId = prisonerId,
        page = pageReq,
        startDate = null,
        endDate = null,
      )
    }

    @Test
    fun `should pass midnight of start date to the repository if start date is provided`() {
      val prisonerId = UUID.randomUUID()
      val christmasEveTenAM = LocalDateTime.of(2025, 12, 24, 10, 30, 0).toLocalDate()
      val christmasEveMidnight = LocalDateTime.of(2025, 12, 24, 0, 0, 0).toInstant(ZoneOffset.UTC)

      whenever { accountService.readAccount(accountUUID = prisonerId) }.thenReturn(AccountEntity(id = prisonerId))
      whenever { postingsDataRepository.getPostingsByAccountId(accountId = prisonerId, pageReq, startDate = christmasEveMidnight) }.thenReturn(PageImpl(emptyList()))

      val postings = statementService.listStatementEntries(accountId = prisonerId, startDate = christmasEveTenAM)?.content!!

      assertThat(postings).isEmpty()
      verify(postingsDataRepository, times(numInvocations = 1)).getPostingsByAccountId(
        accountId = prisonerId,
        pageReq,
        startDate = christmasEveMidnight,
        endDate = null,
      )
    }

    @Test
    fun `should pass 11,59,59 of start date to the repository if end date is provided`() {
      val prisonerId = UUID.randomUUID()
      val christmasEveTenAM = LocalDateTime.of(2025, 12, 24, 10, 30, 0).toLocalDate()
      val christmasElevenFiftyNine = LocalDateTime.of(2025, 12, 24, 23, 59, 59).toInstant(ZoneOffset.UTC)

      whenever { accountService.readAccount(accountUUID = prisonerId) }.thenReturn(AccountEntity(id = prisonerId))
      whenever {
        postingsDataRepository.getPostingsByAccountId(
          accountId = prisonerId,
          page = pageReq,
          startDate = null,
          endDate = christmasElevenFiftyNine,
        )
      }.thenReturn(PageImpl(emptyList()))

      val postings = statementService.listStatementEntries(
        accountId = prisonerId,
        startDate = null,
        endDate = christmasEveTenAM,
      )

      assertThat(postings!!.content).isEmpty()
      verify(postingsDataRepository, times(1)).getPostingsByAccountId(
        accountId = prisonerId,
        page = pageReq,
        startDate = null,
        endDate = christmasElevenFiftyNine,
      )
    }

    @Test
    fun `should call the repository with the correct page number and size`() {
      val prisonerId = UUID.randomUUID()

      whenever { accountService.readAccount(accountUUID = prisonerId) }.thenReturn(AccountEntity(id = prisonerId))
      whenever {
        postingsDataRepository.getPostingsByAccountId(
          accountId = prisonerId,
          page = pageReq,
        )
      }.thenReturn(PageImpl(emptyList()))

      statementService.listStatementEntries(
        accountId = prisonerId,
        pageNumber = 1,
        pageSize = 25,
      )

      verify(postingsDataRepository, times(1)).getPostingsByAccountId(
        accountId = prisonerId,
        page = pageReq,
      )
    }
  }
}
