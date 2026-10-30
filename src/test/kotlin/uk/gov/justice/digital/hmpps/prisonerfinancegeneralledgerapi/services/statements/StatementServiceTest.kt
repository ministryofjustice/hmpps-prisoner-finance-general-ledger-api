package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.statements

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryPostingToTransactionType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryTransactionType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.StatementService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils.toUtcEndOfDay
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.utils.toUtcStartOfDay
import java.time.Instant
import java.time.LocalDate
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

  private val pageReq = PageRequest.of(
    0,
    25,
    Sort.by(
      Sort.Order.desc("transactionEntity.timestamp"),
      Sort.Order.desc("transactionEntity.entrySequence"),
      Sort.Order.desc("entrySequence"),
      Sort.Order.desc("id"),
    ),
  )

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
      val subAccountId = UUID.randomUUID()
      whenever { accountService.readAccount(prisonerId) }.thenReturn(
        AccountEntity(
          id = prisonerId,
          subAccounts = mutableListOf(
            SubAccountEntity(
              id = subAccountId,
              parentAccountEntity = mock(),
              reference = "",
              createdBy = "",
              createdAt = Instant.now(),
            ),
          ),
        ),
      )
      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          prisonerId,
          pageReq,
          subAccounts = listOf(subAccountId),
        )
      }.thenReturn(Page.empty())

      val postings = statementService.listStatementEntries(prisonerId)

      assertThat(postings!!.content).isEmpty()
    }

    @Test
    fun `should return list of postings for a single account with one transaction between sub-accounts`() {
      val accountEntity = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val subAccountCashEntity = serviceTestHelpers.createSubAccount("CASH", accountEntity)
      val subAccountSpendsEntity = serviceTestHelpers.createSubAccount("SPENDS", accountEntity)

      val prisonerId = accountEntity.id

      whenever { accountService.readAccount(prisonerId) }.thenReturn(
        accountEntity,
      )

      val transactionEntity =
        serviceTestHelpers.createOneToOneTransaction(1L, Instant.now(), subAccountCashEntity, subAccountSpendsEntity)

      val posting1 = transactionEntity.postings[0]
      val posting2 = transactionEntity.postings[1]
      val postings = listOf(posting1, posting2)

      val page = PageImpl(postings)

      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          prisonerId,
          pageReq,
          subAccounts = listOf(subAccountCashEntity.id, subAccountSpendsEntity.id),
        )
      }
        .thenReturn(page)

      whenever { postingsDataRepository.findTransactionTypeByPostingsId(eq(postings.map { it.id }.toList())) }
        .thenReturn(
          postings.map { StatementEntryPostingToTransactionType(it, StatementEntryTransactionType.SubAccountTransfer) },
        )

      val statementEntries = statementService.listStatementEntries(prisonerId)?.content!!

      assertThat(statementEntries).hasSize(2)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountCashEntity.id)
      assertThat(statementEntries[0].transactionType).isEqualTo(StatementEntryTransactionType.SubAccountTransfer)
      assertThat(statementEntries[1].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[1].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[1].postingCreatedAt).isEqualTo(posting2.createdAt)
      assertThat(statementEntries[1].subAccount.id).isEqualTo(subAccountSpendsEntity.id)
      assertThat(statementEntries[1].transactionType).isEqualTo(StatementEntryTransactionType.SubAccountTransfer)
    }

    @Test
    fun `should return list of postings for a one to one transaction between accounts`() {
      val accountEntity = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val prisonerId = accountEntity.id

      whenever { accountService.readAccount(any<UUID>()) }.thenReturn(accountEntity)

      val subAccountCashEntity = serviceTestHelpers.createSubAccount("CASH", accountEntity)

      val prisonAccountEntity = serviceTestHelpers.createAccount("LEI", AccountType.PRISON)
      val subAccountPrisonEntity = serviceTestHelpers.createSubAccount("1001:CANT", prisonAccountEntity)

      val transactionEntity =
        serviceTestHelpers.createOneToOneTransaction(1L, Instant.now(), subAccountCashEntity, subAccountPrisonEntity)

      val posting1 = transactionEntity.postings[0]
      val posting2 = transactionEntity.postings[1]

      val page = PageImpl(listOf(posting1))

      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          prisonerId,
          pageReq,
          subAccounts = listOf(subAccountCashEntity.id),
        )
      }
        .thenReturn(page)

      whenever { postingsDataRepository.findTransactionTypeByPostingsId(listOf(posting1.id)) }.thenReturn(
        listOf(StatementEntryPostingToTransactionType(posting1, StatementEntryTransactionType.SubAccountTransfer)),
      )

      val statementEntries = statementService.listStatementEntries(prisonerId)?.content!!

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountCashEntity.id)
      assertThat(statementEntries[0].transactionType).isEqualTo(StatementEntryTransactionType.SubAccountTransfer)
    }

    @Test
    fun `should return list of multiple postings for many to one transaction`() {
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

      whenever { accountService.readAccount(prisonAccountEntity.id) }.thenReturn(prisonAccountEntity)
      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          accountId = prisonAccountEntity.id,
          pageReq,
          subAccounts = listOf(subAccountPrisonEntity.id),
        )
      }.thenReturn(page)

      whenever { postingsDataRepository.findTransactionTypeByPostingsId(listOf(prisonPosting.id)) }.thenReturn(
        listOf(
          StatementEntryPostingToTransactionType(prisonPosting, StatementEntryTransactionType.PrisonerToPrison),
        ),
      )

      val statementEntries = statementService.listStatementEntries(accountId = prisonAccountEntity.id)?.content!!

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(prisonPosting.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountPrisonEntity.id)
      assertThat(statementEntries[0].transactionType).isEqualTo(StatementEntryTransactionType.PrisonerToPrison)
    }

    @Test
    fun `should return list of multiple postings for many to one transaction for an account`() {
      val accountEntityOne = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val accountEntityTwo = serviceTestHelpers.createAccount("ABC123DD", AccountType.PRISONER)
      val subAccountCashEntityOne = serviceTestHelpers.createSubAccount("CASH", accountEntityOne)
      val subAccountCashEntityTwo = serviceTestHelpers.createSubAccount("CASH", accountEntityTwo)

      val prisonAccountEntity = serviceTestHelpers.createAccount("LEI", AccountType.PRISON)
      val subAccountPrisonEntity = serviceTestHelpers.createSubAccount("1001:CANT", prisonAccountEntity)

      whenever { accountService.readAccount(accountEntityOne.id) }.thenReturn(accountEntityOne)

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

      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          accountId = accountEntityOne.id,
          pageReq,
          subAccounts = listOf(subAccountCashEntityOne.id),
        )
      }
        .thenReturn(page)

      whenever {
        postingsDataRepository.findTransactionTypeByPostingsId(listOf(posting1.id))
      }.thenReturn(
        listOf(
          StatementEntryPostingToTransactionType(posting1, StatementEntryTransactionType.PrisonerToPrison),
        ),
      )

      val statementEntries = statementService.listStatementEntries(accountId = accountEntityOne.id)?.content!!

      assertThat(statementEntries).hasSize(1)
      assertThat(statementEntries[0].transactionId).isEqualTo(transactionEntity.id)
      assertThat(statementEntries[0].description).isEqualTo(transactionEntity.description)
      assertThat(statementEntries[0].postingCreatedAt).isEqualTo(posting1.createdAt)
      assertThat(statementEntries[0].subAccount.id).isEqualTo(subAccountCashEntityOne.id)
      assertThat(statementEntries[0].transactionType).isEqualTo(StatementEntryTransactionType.PrisonerToPrison)
    }

    @Test
    fun `should pass midnight of start date to the repository if start date is provided`() {
      val prisonerId = UUID.randomUUID()
      val subAccountId = UUID.randomUUID()
      val christmasEveTenAM = LocalDateTime.of(2025, 12, 24, 10, 30, 0).toLocalDate()
      val christmasEveMidnight = LocalDateTime.of(2025, 12, 24, 0, 0, 0).toInstant(ZoneOffset.UTC)

      whenever { accountService.readAccount(accountUUID = prisonerId) }.thenReturn(
        AccountEntity(
          id = prisonerId,
          subAccounts = mutableListOf(
            SubAccountEntity(
              id = subAccountId,
              parentAccountEntity = mock(),
              reference = "",
              createdBy = "",
              createdAt = Instant.now(),
            ),
          ),
        ),
      )
      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          accountId = prisonerId,
          pageReq,
          startDate = christmasEveMidnight,
          subAccounts = listOf(subAccountId),
        )
      }.thenReturn(PageImpl(emptyList()))

      val postings = statementService.listStatementEntries(accountId = prisonerId, startDate = christmasEveTenAM)?.content!!

      assertThat(postings).isEmpty()
      verify(postingsDataRepository, times(numInvocations = 1)).getPostingsBySubAccounts(
        accountId = prisonerId,
        pageReq,
        startDate = christmasEveMidnight,
        endDate = null,
        subAccounts = listOf(subAccountId),
      )
    }

    @Test
    fun `should pass 11,59,59 of start date to the repository if end date is provided`() {
      val prisonerId = UUID.randomUUID()
      val subAccountId = UUID.randomUUID()
      val christmasEveTenAM = LocalDateTime.of(2025, 12, 24, 10, 30, 0).toLocalDate()
      val christmasElevenFiftyNine = LocalDateTime.of(2025, 12, 24, 23, 59, 59).toInstant(ZoneOffset.UTC)

      whenever {
        accountService.readAccount(
          accountUUID = prisonerId,
        )
      }.thenReturn(
        AccountEntity(
          id = prisonerId,
          subAccounts = mutableListOf(
            SubAccountEntity(
              id = subAccountId,
              parentAccountEntity = mock(),
              reference = "",
              createdBy = "",
              createdAt = Instant.now(),
            ),
          ),
        ),
      )
      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          accountId = prisonerId,
          page = pageReq,
          startDate = null,
          endDate = christmasElevenFiftyNine,
          subAccounts = listOf(subAccountId),
        )
      }.thenReturn(PageImpl(emptyList()))

      val postings = statementService.listStatementEntries(
        accountId = prisonerId,
        startDate = null,
        endDate = christmasEveTenAM,
      )

      assertThat(postings!!.content).isEmpty()
      verify(postingsDataRepository, times(1)).getPostingsBySubAccounts(
        accountId = prisonerId,
        page = pageReq,
        startDate = null,
        endDate = christmasElevenFiftyNine,
        subAccounts = listOf(subAccountId),
      )
    }

    @Test
    fun `should call the repository when the description is provided and return correct results`() {
      val prisonerId = UUID.randomUUID()
      val subAccountId = UUID.randomUUID()

      whenever { accountService.readAccount(accountUUID = prisonerId) }.thenReturn(
        AccountEntity(
          id = prisonerId,
          subAccounts = mutableListOf(
            SubAccountEntity(
              id = subAccountId,
              parentAccountEntity = mock(),
              reference = "",
              createdBy = "",
              createdAt = Instant.now(),
            ),
          ),
        ),
      )
      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          any<UUID>(),
          any<Pageable>(),
          anyOrNull<List<UUID>>(),
          anyOrNull<Instant>(),
          anyOrNull<Instant>(),
          anyOrNull<Boolean>(),
          anyOrNull<Boolean>(),
          description = anyOrNull<String>(),
        )
      }.thenReturn(PageImpl(emptyList()))

      val startDate = LocalDate.now()
      val endDate = LocalDate.now()
      val pageNumber = 1
      val pageSize = 25

      statementService.listStatementEntries(
        accountId = prisonerId,
        subAccountId = subAccountId,
        pageNumber = pageNumber,
        pageSize = pageSize,
        startDate = startDate,
        endDate = endDate,
        credit = true,
        debit = false,
        description = null,
      )

      val pageCapture = argumentCaptor<Pageable>()
      verify(postingsDataRepository, times(1)).getPostingsBySubAccounts(
        accountId = eq(prisonerId),
        page = pageCapture.capture(),
        subAccounts = eq(listOf(subAccountId)),
        startDate = eq(startDate.toUtcStartOfDay()),
        endDate = eq(endDate.toUtcEndOfDay()),
        credit = eq(true),
        debit = eq(false),
        description = eq(null),
      )
      assertThat(pageCapture.firstValue.pageNumber).isEqualTo(0)
      assertThat(pageCapture.firstValue.pageSize).isEqualTo(pageSize)
    }

    @Test
    fun `should call the repository with the correct parameters when default arguments are used`() {
      val prisonerId = UUID.randomUUID()
      val subAccountId = UUID.randomUUID()

      whenever { accountService.readAccount(accountUUID = prisonerId) }.thenReturn(
        AccountEntity(
          id = prisonerId,
          subAccounts = mutableListOf(
            SubAccountEntity(
              id = subAccountId,
              parentAccountEntity = mock(),
              reference = "",
              createdBy = "",
              createdAt = Instant.now(),
            ),
          ),
        ),
      )
      whenever {
        postingsDataRepository.getPostingsBySubAccounts(
          any<UUID>(),
          any<Pageable>(),
          anyOrNull<List<UUID>>(),
          anyOrNull<Instant>(),
          anyOrNull<Instant>(),
          anyOrNull<Boolean>(),
          anyOrNull<Boolean>(),
          description = anyOrNull<String>(),
        )
      }.thenReturn(PageImpl(emptyList()))

      statementService.listStatementEntries(
        accountId = prisonerId,
      )

      val pageCapture = argumentCaptor<Pageable>()
      verify(postingsDataRepository, times(1)).getPostingsBySubAccounts(
        accountId = eq(prisonerId),
        page = pageCapture.capture(),
        subAccounts = eq(listOf(subAccountId)),
        startDate = eq(null),
        endDate = eq(null),
        credit = eq(false),
        debit = eq(false),
        description = eq(null),
      )
      assertThat(pageCapture.firstValue.pageNumber).isEqualTo(0)
      assertThat(pageCapture.firstValue.pageSize).isEqualTo(25)
    }
  }
}
