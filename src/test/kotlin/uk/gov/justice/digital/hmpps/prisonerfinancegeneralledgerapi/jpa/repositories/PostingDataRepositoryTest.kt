package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.ContainersConfig
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@DataJpaTest
@Import(RepoTestHelpers::class, ContainersConfig::class)
class PostingDataRepositoryTest @Autowired constructor(
  val postingsDataRepository: PostingsDataRepository,
  val repoTestHelpers: RepoTestHelpers,
) {

  private lateinit var accountOne: AccountEntity
  private lateinit var accountOneSubAccountOne: SubAccountEntity
  private lateinit var accountOneSubAccountTwo: SubAccountEntity

  private lateinit var accountTwo: AccountEntity
  private lateinit var accountTwoSubAccountOne: SubAccountEntity

  private lateinit var accountThree: AccountEntity
  private lateinit var accountThreeSubAccountOne: SubAccountEntity

  private val pageReq = PageRequest.of(
    0,
    25,
    Sort.by(
      Sort.Order.desc("transactionEntity.timestamp"),
      Sort.Order.desc("transactionEntity.entrySequence"),
      Sort.Order.desc("entrySequence"),
    ),
  )

  @BeforeEach
  fun setup() {
    repoTestHelpers.clearDb()
  }

  @Nested
  inner class GetPostingsForAccount {
    @Test
    fun `Should return an empty list when an account has no postings`() {
      val parentAccountId = UUID.randomUUID()
      val postings = postingsDataRepository.getPostingsByAccountId(parentAccountId, pageReq)
      assertThat(postings).isEmpty()
    }

    @Test
    fun `Should return a single sub account posting for a prisoner when is a single posting`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now(),
        transactionTimeStamp = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq)
      assertThat(postings).hasSize(1)
    }

    @Test
    fun `Should return all postings across multiple sub accounts `() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq)
      assertThat(postings).hasSize(2)
    }

    @Test
    fun `Should return all postings on or after the start date supplied`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
      val timeTodayAtOneAM = LocalDate.now().atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)

      // TX from 1 day ago
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeYesterdayAtOneAM,
        postingCreatedAt = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      val txAtMidnight = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTodayAtMidnight,
        postingCreatedAt = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from at one AM
      val txAtOneAM = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTodayAtOneAM,
        postingCreatedAt = timeTodayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, startDate = timeTodayAtMidnight).content

      // two tx today with 4 postings for the account/subaccount transfer == 4 posting
      assertThat(postings).hasSize(4)

      assertThat(postings.last().transactionEntity.id).isEqualTo(txAtMidnight.id)
      assertThat(postings.first().transactionEntity.id).isEqualTo(txAtOneAM.id)
    }

    @Test
    fun `Should return all postings on or before the end date supplied`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
      val timeTomorrowAtMidnight = LocalDate.now().atStartOfDay().plusDays(1).toInstant(ZoneOffset.UTC)

      // TX from 1 day ago
      val txFromYesterday = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeYesterdayAtOneAM,
        postingCreatedAt = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      val txFromMidnight = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTodayAtMidnight,
        postingCreatedAt = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from tomorrow at midnight
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTomorrowAtMidnight,
        postingCreatedAt = timeTomorrowAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, endDate = timeTodayAtMidnight).content

      // two tx today with 4 postings for the account/subaccount transfer == 4 posting
      assertThat(postings).hasSize(4)

      assertThat(postings.last().transactionEntity.id).isEqualTo(txFromYesterday.id)
      assertThat(postings.first().transactionEntity.id).isEqualTo(txFromMidnight.id)
    }

    @Test
    fun `should return all posting between or on start and end date supplied`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
      val timeTodayAtNoon = LocalDate.now().atStartOfDay().plusHours(12).toInstant(ZoneOffset.UTC)
      val timeTomorrowAtMidnight = LocalDate.now().atStartOfDay().plusDays(1).toInstant(ZoneOffset.UTC)
      val timeTomorrowAtOneAM = LocalDate.now().atStartOfDay().plusDays(1).plusHours(1).toInstant(ZoneOffset.UTC)

      // TX from 1 day ago
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeYesterdayAtOneAM,
        postingCreatedAt = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      val txFromMidnight = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTodayAtMidnight,
        postingCreatedAt = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at noon
      val txFromTodayAtNoon = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTodayAtNoon,
        postingCreatedAt = timeTodayAtNoon,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from tomorrow at one AM
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTomorrowAtOneAM,
        postingCreatedAt = timeTomorrowAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, startDate = timeTodayAtMidnight, endDate = timeTomorrowAtMidnight).content

      // two tx today with 4 postings for the account/subaccount transfer == 4 posting
      assertThat(postings).hasSize(4)

      assertThat(postings.last().transactionEntity.id).isEqualTo(txFromMidnight.id)
      assertThat(postings.first().transactionEntity.id).isEqualTo(txFromTodayAtNoon.id)
    }

    @Test
    fun `should return empty list if start and end date are transposed`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
      val timeTodayAtNoon = LocalDate.now().atStartOfDay().plusHours(12).toInstant(ZoneOffset.UTC)
      val timeTomorrowAtMidnight = LocalDate.now().atStartOfDay().plusDays(1).toInstant(ZoneOffset.UTC)
      val timeTomorrowAtOneAM = LocalDate.now().atStartOfDay().plusDays(1).plusHours(1).toInstant(ZoneOffset.UTC)

      // TX from 1 day ago
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeYesterdayAtOneAM,
        postingCreatedAt = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTodayAtMidnight,
        postingCreatedAt = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at noon
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTodayAtNoon,
        postingCreatedAt = timeTodayAtNoon,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from tomorrow at one AM
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = timeTomorrowAtOneAM,
        postingCreatedAt = timeTomorrowAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, startDate = timeTomorrowAtMidnight, endDate = timeTodayAtMidnight).content

      assertThat(postings).hasSize(0)
    }

    @Test
    fun `should return statement entries by timestamp descending regardless of order of insertion`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val februaryTransactionsOne = listOf(
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 2, 23).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 2, 23).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = accountOneSubAccountOne,
          creditSubAccount = accountOneSubAccountTwo,
        ),
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 2, 22).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 2, 22).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = accountOneSubAccountOne,
          creditSubAccount = accountOneSubAccountTwo,
        ),
      )

      listOf(
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 3, 15).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 3, 15).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = accountOneSubAccountOne,
          creditSubAccount = accountOneSubAccountTwo,
        ),
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 3, 16).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 3, 16).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = accountOneSubAccountOne,
          creditSubAccount = accountOneSubAccountTwo,
        ),
      )

      listOf(
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 2, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 2, 11).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = accountOneSubAccountOne,
          creditSubAccount = accountOneSubAccountTwo,
        ),
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 2, 10).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 2, 10).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = accountOneSubAccountOne,
          creditSubAccount = accountOneSubAccountTwo,
        ),
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq)

      assertThat(postings.content).hasSize(12)

      val sortedPostings = postings.content.sortedByDescending { it.transactionEntity.timestamp }

      postings.content.forEachIndexed { index, posting ->
        assertThat(posting.id).isEqualTo(sortedPostings[index].id)
        assertThat(posting.transactionEntity.timestamp).isEqualTo(sortedPostings[index].transactionEntity.timestamp)
      }
    }

    @Test
    fun `should return statement entries based on page size`() {
      val pageSize = 5

      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountTwo = repoTestHelpers.createAccount(ref = "ABC124YY")
      val subAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      val subAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountTwo)
      val transactions = mutableListOf<TransactionEntity>()
      repeat(pageSize + 1) { index ->
        transactions.add(
          repoTestHelpers.createOneToOneTransaction(
            transactionAmount = 1,
            transactionTimeStamp = LocalDate.of(2026, 2, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
            postingCreatedAt = LocalDate.of(2026, 2, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
            debitSubAccount = subAccountOne,
            creditSubAccount = subAccountTwo,
          ),
        )
      }

      val postings = postingsDataRepository.getPostingsByAccountId(
        accountId = accountOne.id,
        page = PageRequest.of(
          0,
          pageSize,
          Sort.by(
            Sort.Order.desc("transactionEntity.timestamp"),
            Sort.Order.desc("transactionEntity.entrySequence"),
            Sort.Order.desc("entrySequence"),
          ),
        ),
      )

      assertThat(postings.content).hasSize(pageSize)

      assertThat(postings.content.any { it.transactionEntity.id == transactions.first().id }).isFalse
    }

    @Test
    fun `should return statement entries based on page number`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountTwo = repoTestHelpers.createAccount(ref = "ABC124YY")
      val subAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      val subAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountTwo)

      repeat(25) { index ->
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 2, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 2, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = subAccountOne,
          creditSubAccount = subAccountTwo,
        )
      }
      repeat(25) { index ->
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 3, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 3, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = subAccountOne,
          creditSubAccount = subAccountTwo,
        )
      }

      val pageZero = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq)
      assertThat(pageZero.size).isEqualTo(25)
      val feb28 = LocalDate.of(2026, 2, 28).atStartOfDay().toInstant(ZoneOffset.UTC)
      assertThat(pageZero.content.all { it.transactionEntity.timestamp.isAfter(feb28) }).isTrue

      val pageOne = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, PageRequest.of(1, 25, Sort.Direction.DESC, "transactionEntity.timestamp"))
      assertThat(pageOne.size).isEqualTo(25)
      val march1 = LocalDate.of(2026, 3, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
      assertThat(pageOne.content.all { it.transactionEntity.timestamp.isBefore(march1) }).isTrue
    }

    @Test
    fun `should return empty page if page number is out of bounds`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountTwo = repoTestHelpers.createAccount(ref = "ABC124YY")
      val subAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      val subAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountTwo)

      repeat(25) { index ->
        repoTestHelpers.createOneToOneTransaction(
          transactionAmount = 1,
          transactionTimeStamp = LocalDate.of(2026, 2, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
          postingCreatedAt = LocalDate.of(2026, 2, 1 + index).atStartOfDay().toInstant(ZoneOffset.UTC),
          debitSubAccount = subAccountOne,
          creditSubAccount = subAccountTwo,
        )
      }

      val pageTen = postingsDataRepository.getPostingsByAccountId(
        accountId = accountOne.id,
        PageRequest.of(
          10,
          25,
          Sort.by(
            Sort.Order.desc("transactionEntity.timestamp"),
            Sort.Order.desc("transactionEntity.entrySequence"),
            Sort.Order.desc("entrySequence"),
          ),
        ),
      )

      assertThat(pageTen.content.size).isEqualTo(0)
    }

    @Test
    fun `should return all postings filtered by credit for prisoner`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, credit = true).content

      assertThat(postings).hasSize(2)

      assertThat(postings.all { posting -> posting.type == PostingType.CR }).isTrue()
    }

    @Test
    fun `should return all postings filtered by credit for prison`() {
      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val prisonerAccountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      val prisonerAccountOneSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountOne)

      val prisonerAccountTwo = repoTestHelpers.createAccount(ref = "ZXC123XX")
      val prisonerAccountTwoSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountTwo)

      repoTestHelpers.createOneToManyTransaction(
        ref = "CANT spends",
        manyToOneSubAccounts = listOf(prisonerAccountOneSubAccount, prisonerAccountTwoSubAccount),
        oneToManySubAccount = prisonCanteenSubAccount,
        amountPerSubAccount = 1,
        oneToManyPostingType = PostingType.CR,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = prisonAccount.id, pageReq, credit = true).content

      assertThat(postings).hasSize(1)

      assertThat(postings.all { posting -> posting.type == PostingType.CR }).isTrue()
    }

    @Test
    fun `should return all postings filtered by debit for prisoner`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, debit = true).content

      assertThat(postings).hasSize(2)

      assertThat(postings.all { posting -> posting.type == PostingType.DR }).isTrue()
    }

    @Test
    fun `should return all postings filtered by debit for prison`() {
      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val prisonerAccountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      val prisonerAccountOneSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountOne)

      val prisonerAccountTwo = repoTestHelpers.createAccount(ref = "ZXC123XX")
      val prisonerAccountTwoSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountTwo)

      repoTestHelpers.createOneToManyTransaction(
        ref = "CANT spends",
        oneToManySubAccount = prisonCanteenSubAccount,
        manyToOneSubAccounts = listOf(prisonerAccountOneSubAccount, prisonerAccountTwoSubAccount),
        amountPerSubAccount = 1,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = prisonAccount.id, pageReq, debit = true).content

      assertThat(postings).hasSize(1)

      assertThat(postings.all { posting -> posting.type == PostingType.DR }).isTrue()
    }

    @Test
    fun `should return all postings filtered by credit & debit for prisoner`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postingsBothFilteredIn = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, credit = true, debit = true).content

      assertThat(postingsBothFilteredIn).hasSize(4)

      val postingsBothFilteredOut = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, pageReq, credit = false, debit = false).content

      assertThat(postingsBothFilteredOut).hasSize(4)
    }

    @Test
    fun `should return all postings filtered by credit & debit for prison`() {
      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)

      val prisonerAccountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      val prisonerAccountOneSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountOne)

      val prisonerAccountTwo = repoTestHelpers.createAccount(ref = "ZXC123XX")
      val prisonerAccountTwoSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountTwo)

      repoTestHelpers.createOneToManyTransaction(
        ref = "CANT spends",
        oneToManySubAccount = prisonCanteenSubAccount,
        manyToOneSubAccounts = listOf(prisonerAccountOneSubAccount, prisonerAccountTwoSubAccount),
        amountPerSubAccount = 1,
      )

      repoTestHelpers.createOneToManyTransaction(
        ref = "CANT spends",
        manyToOneSubAccounts = listOf(prisonerAccountOneSubAccount, prisonerAccountTwoSubAccount),
        oneToManySubAccount = prisonCanteenSubAccount,
        amountPerSubAccount = 1,
        oneToManyPostingType = PostingType.DR,
      )

      val postingsBothFilteredIn = postingsDataRepository.getPostingsByAccountId(accountId = prisonAccount.id, pageReq, debit = true, credit = true).content

      assertThat(postingsBothFilteredIn).hasSize(2)

      val postingsBothFilteredOut = postingsDataRepository.getPostingsByAccountId(accountId = prisonAccount.id, pageReq, debit = false, credit = false).content

      assertThat(postingsBothFilteredOut).hasSize(2)
    }

    @Test
    fun `should return all postings filtered by subAccount for prison`() {
      val prisonAccount = repoTestHelpers.createAccount(ref = "LEI")
      val prisonCanteenSubAccount = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = prisonAccount)
      val prisonAdvanceSubAccount = repoTestHelpers.createSubAccount(ref = "1502:ADV", account = prisonAccount)

      val prisonerAccountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      val prisonerAccountOneSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountOne)

      val prisonerAccountTwo = repoTestHelpers.createAccount(ref = "ZXC123XX")
      val prisonerAccountTwoSubAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = prisonerAccountTwo)

      repoTestHelpers.createOneToManyTransaction(
        ref = "CANT spends",
        oneToManySubAccount = prisonCanteenSubAccount,
        manyToOneSubAccounts = listOf(prisonerAccountOneSubAccount, prisonerAccountTwoSubAccount),
        amountPerSubAccount = 1,
      )

      repoTestHelpers.createOneToManyTransaction(
        ref = "ADV spends",
        oneToManySubAccount = prisonAdvanceSubAccount,
        manyToOneSubAccounts = listOf(prisonerAccountOneSubAccount, prisonerAccountTwoSubAccount),
        amountPerSubAccount = 1,
      )

      val postingsCanteen = postingsDataRepository.getPostingsByAccountId(accountId = prisonAccount.id, subAccountId = prisonCanteenSubAccount.id, page = pageReq).content

      assertThat(postingsCanteen).hasSize(1)
      assertThat(postingsCanteen.all { posting -> posting.subAccountEntity.id == prisonCanteenSubAccount.id }).isTrue()

      val postingsAdvance = postingsDataRepository.getPostingsByAccountId(accountId = prisonAccount.id, subAccountId = prisonAdvanceSubAccount.id, page = pageReq).content

      assertThat(postingsAdvance).hasSize(1)
      assertThat(postingsAdvance.all { posting -> posting.subAccountEntity.id == prisonAdvanceSubAccount.id }).isTrue()
    }

    @Test
    fun `should return all postings filtered by subAccount for prisoner`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionTimeStamp = Instant.now(),
        postingCreatedAt = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postingsSubOne = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, subAccountId = accountOneSubAccountOne.id, page = pageReq).content

      assertThat(postingsSubOne).hasSize(2)
      assertThat(postingsSubOne.all { posting -> posting.subAccountEntity.id == accountOneSubAccountOne.id }).isTrue()

      val postingsSubTwo = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, subAccountId = accountOneSubAccountTwo.id, page = pageReq).content

      assertThat(postingsSubTwo).hasSize(2)
      assertThat(postingsSubTwo.all { posting -> posting.subAccountEntity.id == accountOneSubAccountTwo.id }).isTrue()
    }
  }

  @Nested
  inner class GetBalanceForSubAccount {

    // Account | SubAccount   | Amount
    // --------|--------------|--------
    // 1       | SubAccount1  | 4 CR
    //         | TOTAL        | 4
    //
    // 2       | SubAccount1  | 4 DR
    //         | TOTAL        | -4
    //
    // 3       | SubAccount1  | 0 CR
    //         | TOTAL        | 0

    @BeforeEach
    fun setupEntities() {
      accountOne = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_1")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_1", accountOne)

      accountTwo = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_2")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_2", accountTwo)

      accountThree = repoTestHelpers.createAccount("TEST_ACCOUNT_REF_3")
      accountThreeSubAccountOne = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF_3", accountThree)

      repeat(5) {
        repoTestHelpers.createOneToOneTransaction(1, Instant.now(), accountTwoSubAccountOne, accountOneSubAccountOne, transactionTimeStamp = Instant.now())
      }

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), accountOneSubAccountOne, accountTwoSubAccountOne, transactionTimeStamp = Instant.now())
    }

    @Test
    fun `Should return a balance of all credits minus all debits for a sub account`() {
      val subOneBalance = postingsDataRepository.getBalanceForSubAccount(accountOneSubAccountOne.id)
      val subTwoBalance = postingsDataRepository.getBalanceForSubAccount(accountTwoSubAccountOne.id)

      assertThat(subOneBalance).isEqualTo(4)
      assertThat(subTwoBalance).isEqualTo(-4)
    }

    @Test
    fun `Should return 0 if no postings found for the subaccount Id`() {
      val subThreeBalance = postingsDataRepository.getBalanceForSubAccount(accountThreeSubAccountOne.id)
      assertThat(subThreeBalance).isEqualTo(0)
    }

    @Test
    fun `Should return the balance of all postings after a datetime if provided`() {
      val accountWithNoMoney = accountThreeSubAccountOne
      val zeroBalance = postingsDataRepository.getBalanceForSubAccount(accountWithNoMoney.id)
      assertThat(zeroBalance).isEqualTo(0)

      // txToIgnoreFromTwoDaysAgo
      val twoDaysAgo = LocalDateTime.now().minusDays(2).toInstant(ZoneOffset.UTC)
      repoTestHelpers.createOneToOneTransaction(transactionAmount = 100, postingCreatedAt = twoDaysAgo, debitSubAccount = accountOneSubAccountOne, creditSubAccount = accountWithNoMoney, transactionTimeStamp = twoDaysAgo)

      val statementBalanceFromYesterday = StatementBalanceEntity(amount = 0, subAccountEntity = accountOneSubAccountOne, balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.UTC))

      // txFromTodayToInclude
      repoTestHelpers.createOneToOneTransaction(50, Instant.now(), accountOneSubAccountOne, accountWithNoMoney, transactionTimeStamp = Instant.now())

      val subAccountBalance = postingsDataRepository.getBalanceForSubAccount(accountWithNoMoney.id, latestStatementBalanceDateTime = statementBalanceFromYesterday.balanceDateTime)

      assertThat(subAccountBalance).isEqualTo(50)
    }
  }

  @Nested
  inner class GetBalanceForAPrisonerAtAPrison {

    @Test
    fun `Should return zero if a prisoner has no transactions with that prison`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonerOne = repoTestHelpers.createAccount("123456")

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)

      assertThat(balanceForPrisonerAtPrison).isEqualTo(0)
    }

    @Test
    fun `Should return the balance of a prisoner sub account postings when they are all at one prison`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonACanteen = repoTestHelpers.createSubAccount("AAA:Canteen", prisonA)
      val prisonerOne = repoTestHelpers.createAccount("123456")
      val prisonerOneCash = repoTestHelpers.createSubAccount("CASH", prisonerOne)

      repoTestHelpers.createOneToOneTransaction(10, Instant.now(), prisonerOneCash, prisonACanteen, transactionTimeStamp = Instant.now())
      repoTestHelpers.createOneToOneTransaction(5, Instant.now(), prisonACanteen, prisonerOneCash, transactionTimeStamp = Instant.now())

      val prisonerBalAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)

      assertThat(prisonerBalAtPrison).isEqualTo(-5)
    }

    @Test
    fun `Should return the balance of a prisoner at the queried prison when transactions exist at another prison`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonACanteen = repoTestHelpers.createSubAccount("AAA:Canteen", prisonA)

      val prisonB = repoTestHelpers.createAccount("BBB")
      val prisonBCatalogue = repoTestHelpers.createSubAccount("BBB:Catalog", prisonB)

      val prisoner = repoTestHelpers.createAccount("123456")
      val prisonerCashAccount = repoTestHelpers.createSubAccount("CASH", prisoner)

      repoTestHelpers.createOneToOneTransaction(15, Instant.now(), prisonerCashAccount, prisonACanteen, transactionTimeStamp = Instant.now())
      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), prisonerCashAccount, prisonBCatalogue, transactionTimeStamp = Instant.now())

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisoner.id)
      assertThat(balanceForPrisonerAtPrison).isEqualTo(-15)
    }

    @Test
    fun `Should return the balance of a prisoner at the queried prison when transactions exist with many to one or one to many relationships`() {
      val prisonA = repoTestHelpers.createAccount("AAA")
      val prisonACash = repoTestHelpers.createSubAccount("AAA:CASH", prisonA)

      val prisonerOne = repoTestHelpers.createAccount("123456")
      val prisonerOneCashAccount = repoTestHelpers.createSubAccount("CASH", prisonerOne)

      val prisonerTwo = repoTestHelpers.createAccount("7891011")
      val prisonerTwoCashAccount = repoTestHelpers.createSubAccount("CASH", prisonerTwo)

      repoTestHelpers.createOneToManyTransaction(
        "BONUS",
        prisonACash,
        listOf(prisonerOneCashAccount, prisonerTwoCashAccount),
        30,
      )

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), prisonerTwoCashAccount, prisonACash, transactionTimeStamp = Instant.now())

      repoTestHelpers.createOneToOneTransaction(5, Instant.now(), prisonACash, prisonerOneCashAccount, transactionTimeStamp = Instant.now())

      val balanceForPrisonerAtPrison =
        postingsDataRepository.getBalanceForAPrisonerAtAPrison(prisonId = prisonA.id, prisonerId = prisonerOne.id)
      assertThat(balanceForPrisonerAtPrison).isEqualTo(35)
    }
  }

  @Nested
  inner class CalculateBalanceFromPostings {

    @Test
    fun `Should sum credits positively`() {
      val postings = listOf(
        PostingEntity(amount = 1, type = PostingType.CR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF", entrySequence = 1), entrySequence = 1),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(1)
    }

    @Test
    fun `Should sum debits negatively`() {
      val postings = listOf(
        PostingEntity(amount = 1, type = PostingType.DR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF", entrySequence = 1), entrySequence = 1),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(-1)
    }

    @Test
    fun `Should handle multiple postings of varied type`() {
      val postings = listOf(
        PostingEntity(
          amount = 3,
          type = PostingType.CR,
          subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"),
          transactionEntity = TransactionEntity(
            reference =
            "TEST_TRANSACTION_REF",
            entrySequence = 1,
          ),
          entrySequence = 1,
        ),
        PostingEntity(
          amount = 1,
          type = PostingType.DR,
          subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"),
          transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF", entrySequence = 1),
          entrySequence = 2,
        ),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(2)
    }
  }

  @Nested
  inner class GetTheNextAccountPostingOrNull {
    @Test
    fun `Should default to null when there is not a next posting`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      val transaction = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(60),
        transactionTimeStamp = Instant.now().minusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val posting = transaction.postings.first()
      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = posting.id,
        accountId = posting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = posting.transactionEntity.timestamp,
        transactionEntrySequence = posting.transactionEntity.entrySequence,
        postingEntrySequence = posting.entrySequence,
      )

      assertThat(nextPosting).isNull()
    }

    @Test
    fun `Should return the next account Posting from the next transaction when the initial transaction contains no more postings for that account`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(60),
        transactionTimeStamp = Instant.now().minusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val transactionTwo = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now(),
        transactionTimeStamp = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val posting = transactionOne.postings.first()
      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = posting.id,
        accountId = posting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = posting.transactionEntity.timestamp,
        transactionEntrySequence = posting.transactionEntity.entrySequence,
        postingEntrySequence = posting.entrySequence,
      )

      assertThat(nextPosting).isEqualTo(transactionTwo.postings.first())
    }

    @Test
    fun `Should return the next posting from another transaction when the next posting has a lower entry sequence than the initial posting`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)
      val transactionTimestamp = Instant.now()

      val initialTransaction = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
        debitEntrySequence = 2,
        creditEntrySequence = 1,
        transactionEntrySequence = 1,
      )

      val nextTransaction = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp.plusSeconds(60),
        transactionTimeStamp = transactionTimestamp.plusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
        transactionEntrySequence = 1,
      )

      val initialPosting = initialTransaction.postings.first()

      assertThat(initialPosting.entrySequence).isEqualTo(2)
      assertThat(initialPosting.subAccountEntity.id).isEqualTo(accountOneSubAccountOne.id)

      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = initialPosting.id,
        accountId = initialPosting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = initialPosting.transactionEntity.timestamp,
        transactionEntrySequence = initialPosting.transactionEntity.entrySequence,
        postingEntrySequence = initialPosting.entrySequence,
      )

      assertThat(nextPosting?.entrySequence).isEqualTo(1)
      assertThat(nextPosting?.subAccountEntity?.id).isEqualTo(accountOneSubAccountOne.id)
      assertThat(nextPosting?.transactionEntity?.id).isEqualTo(nextTransaction.id)
    }

    @Test
    fun `Should return the next posting from the a one to many transaction when the transaction contains multiple postings for the same account`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "DEF456YY")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountTwo)

      accountThree = repoTestHelpers.createAccount(ref = "LEI")
      accountThreeSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountThree)

      val transaction = repoTestHelpers.createOneToManyTransaction(
        ref = "PAYROLL",
        oneToManySubAccount = accountThreeSubAccountOne,
        manyToOneSubAccounts = listOf(
//          we need postings before and after the posting we want to find the next posting to prove posting entry sequence
          accountOneSubAccountOne, // This should be ignored
          accountTwoSubAccountOne,
          accountOneSubAccountOne, // This is the initial posting
          accountTwoSubAccountOne,
          accountOneSubAccountOne, // This is the next posting
          accountTwoSubAccountOne,
        ),
        amountPerSubAccount = 1,
        oneToManyPostingType = PostingType.CR,
      )

      val initialPosting = transaction.postings[3]
      assertThat(initialPosting.entrySequence).isEqualTo(4)
      assertThat(initialPosting.subAccountEntity.id).isEqualTo(accountOneSubAccountOne.id)

      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = initialPosting.id,
        accountId = initialPosting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = initialPosting.transactionEntity.timestamp,
        transactionEntrySequence = initialPosting.transactionEntity.entrySequence,
        postingEntrySequence = initialPosting.entrySequence,
      )

      assertThat(nextPosting?.entrySequence).isEqualTo(6)
      assertThat(nextPosting?.subAccountEntity?.id).isEqualTo(accountOneSubAccountOne.id)
      assertThat(nextPosting?.transactionEntity?.id).isEqualTo(transaction.id)
    }

    @Test
    fun `Should return the next posting from a batch transaction split across 2 transactions`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)
      val transactionTimestamp = Instant.now()

//      2 part transaction with the same timestamp as
//      Batch transactions will be ordered by transactionEntrySequence
      val batchTransactionPart1 = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = accountOneSubAccountOne,
        debitEntrySequence = 1,
        creditSubAccount = accountTwoSubAccountOne,
        creditEntrySequence = 2,
        transactionEntrySequence = 1,
      )

      val batchTransactionPart2 = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = accountOneSubAccountOne,
        debitEntrySequence = 3,
        creditSubAccount = accountTwoSubAccountOne,
        creditEntrySequence = 4,
        transactionEntrySequence = 2,
      )

      val initialPosting = batchTransactionPart1.postings.first()
      assertThat(initialPosting.transactionEntity.entrySequence).isEqualTo(1)
      assertThat(initialPosting.entrySequence).isEqualTo(1)

      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = initialPosting.id,
        accountId = initialPosting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = initialPosting.transactionEntity.timestamp,
        transactionEntrySequence = initialPosting.transactionEntity.entrySequence,
        postingEntrySequence = initialPosting.entrySequence,
      )

      assertThat(nextPosting?.transactionEntity?.id).isEqualTo(batchTransactionPart2.id)
      assertThat(nextPosting?.entrySequence).isEqualTo(3)
      assertThat(nextPosting?.transactionEntity?.entrySequence).isEqualTo(2)
    }

    @Test
    fun `Should return the next posting by id when transaction entrySequence and postingEntry sequence are zero`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)
      val transactionTimestamp = Instant.now()

      //      2 part transaction with the same timestamp as
      //      Batch transactions will be ordered by transactionEntrySequence
      //      For old data we need to be able to find the next posting by id
      //      because we migrated entrySequences as zeros
      val postingOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = accountOneSubAccountOne,
        debitEntrySequence = 0,
        creditSubAccount = accountTwoSubAccountOne,
        creditEntrySequence = 0,
        transactionEntrySequence = 0,
      ).postings.first()

      val postingTwo = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = accountOneSubAccountOne,
        debitEntrySequence = 0,
        creditSubAccount = accountTwoSubAccountOne,
        creditEntrySequence = 0,
        transactionEntrySequence = 0,
      ).postings.first()

      val firstPostingId = UUID.fromString(listOf(postingOne.id, postingTwo.id).minOf { it.toString() })
      val lastPostingId = UUID.fromString(listOf(postingOne.id, postingTwo.id).maxOf { it.toString() })

      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = firstPostingId,
        accountId = accountOne.id,
        transactionTimestamp = transactionTimestamp,
        transactionEntrySequence = 0,
        postingEntrySequence = 0,
      )

      assertThat(nextPosting?.subAccountEntity?.parentAccountEntity).isEqualTo(accountOne)
      assertThat(nextPosting?.id).isEqualTo(lastPostingId)
    }

    @Test
    fun `Should return the next posting when it is for a different sub account in a different transaction`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SAVINGS", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(60),
        transactionTimeStamp = Instant.now().minusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val transactionTwo = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now(),
        transactionTimeStamp = Instant.now(),
        debitSubAccount = accountOneSubAccountTwo,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val posting = transactionOne.postings.first()
      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = posting.id,
        accountId = posting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = posting.transactionEntity.timestamp,
        transactionEntrySequence = posting.transactionEntity.entrySequence,
        postingEntrySequence = posting.entrySequence,
      )

      assertThat(nextPosting).isEqualTo(transactionTwo.postings.first())
    }

    @Test
    fun `Should return the next posting when it is for a different sub account in the same transaction`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SAVINGS", account = accountOne)

      val transaction = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(60),
        transactionTimeStamp = Instant.now().minusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
      )

      val posting = transaction.postings.first()
      val nextPosting = postingsDataRepository.getTheNextAccountPostingOrNull(
        postingId = posting.id,
        accountId = posting.subAccountEntity.parentAccountEntity.id,
        transactionTimestamp = posting.transactionEntity.timestamp,
        transactionEntrySequence = posting.transactionEntity.entrySequence,
        postingEntrySequence = posting.entrySequence,
      )

      assertThat(nextPosting).isEqualTo(transaction.postings[1])
      assertThat(nextPosting?.entrySequence).isEqualTo(2)
    }
  }

  @Nested
  inner class GetFirstPostingForAccountIdAfterDateTime {

    @Test
    fun `Should return the next subAccount Posting when there is one`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(60),
        transactionTimeStamp = Instant.now().minusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val posting = transactionOne.postings.first()
      val nextPosting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        dateTime = transactionOne.timestamp.minusSeconds(60),
        accountId = posting.subAccountEntity.parentAccountEntity.id,
      )

      assertThat(nextPosting).isEqualTo(transactionOne.postings.first())
    }

    @Test
    fun `Should return the next posting and order by timestamp`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      val transactionTimestamp = Instant.now()

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp.minusSeconds(180),
        transactionTimeStamp = transactionTimestamp.minusSeconds(180),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )
      val transactionInTheFuture = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp.plusSeconds(60),
        transactionTimeStamp = transactionTimestamp.plusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val posting = transactionOne.postings.first()

      val nextPosting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        dateTime = transactionOne.timestamp,
        accountId = posting.subAccountEntity.parentAccountEntity.id,
      )

      assertThat(nextPosting).isEqualTo(transactionInTheFuture.postings.first())
      assertThat(nextPosting?.entrySequence).isEqualTo(1)
      assertThat(nextPosting?.transactionEntity?.entrySequence).isEqualTo(1)
    }

    @Test
    fun `Should return the next posting and order by timestamp, transaction entrySequence`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(180),
        transactionTimeStamp = Instant.now().minusSeconds(180),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val transactionTimestamp = Instant.now()

      val transactionInTheFuture = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp.plusSeconds(60),
        transactionTimeStamp = transactionTimestamp.plusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val transactionEntryTwo = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = accountOneSubAccountOne,
        debitEntrySequence = 3,
        creditSubAccount = accountTwoSubAccountOne,
        creditEntrySequence = 4,
        transactionEntrySequence = 2,
      )

      val transactionEntryOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = accountOneSubAccountOne,
        debitEntrySequence = 1,
        creditSubAccount = accountTwoSubAccountOne,
        creditEntrySequence = 2,
        transactionEntrySequence = 1,
      )

      val posting = transactionEntryOne.postings.first()
      val nextPosting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        dateTime = transactionEntryOne.timestamp.minusSeconds(60),
        accountId = posting.subAccountEntity.parentAccountEntity.id,
      )

      assertThat(nextPosting).isEqualTo(transactionEntryOne.postings.first())
      assertThat(nextPosting?.entrySequence).isEqualTo(1)
      assertThat(nextPosting?.transactionEntity?.entrySequence).isEqualTo(1)
    }

    @Test
    fun `Should return the next posting and order by posting entrySequence`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(180),
        transactionTimeStamp = Instant.now().minusSeconds(180),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
        transactionEntrySequence = 1,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
      )


      val posting = transactionOne.postings.first()
      val nextPosting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        dateTime = transactionOne.timestamp.minusSeconds(60),
        accountId = posting.subAccountEntity.parentAccountEntity.id,
      )

      assertThat(nextPosting).isEqualTo(posting)
      assertThat(nextPosting?.entrySequence).isEqualTo(1)
      assertThat(nextPosting?.transactionEntity?.entrySequence).isEqualTo(1)
    }

    @Test
    fun `Should return the next posting and order by id`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(180),
        transactionTimeStamp = Instant.now().minusSeconds(180),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
        transactionEntrySequence = 0,
        debitEntrySequence = 0,
        creditEntrySequence = 0,
      )


      val posting = transactionOne.postings.minBy{ it.id.toString() }
      val nextPosting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        dateTime = transactionOne.timestamp.minusSeconds(60),
        accountId = posting.subAccountEntity.parentAccountEntity.id,
      )

      assertThat(nextPosting).isEqualTo(posting)
      assertThat(nextPosting?.entrySequence).isEqualTo(0)
      assertThat(nextPosting?.transactionEntity?.entrySequence).isEqualTo(0)
    }

    @Test
    fun `Should default to null when there is not a next posting`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)

      accountTwo = repoTestHelpers.createAccount(ref = "LEI")
      accountTwoSubAccountOne = repoTestHelpers.createSubAccount(ref = "1001:CANT", account = accountTwo)

      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now().minusSeconds(60),
        transactionTimeStamp = Instant.now().minusSeconds(60),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val nextPosting = postingsDataRepository.getFirstPostingForAccountIdAfterDateTime(
        dateTime = transactionOne.timestamp.plusSeconds(10),
        accountId = transactionOne.postings.first().subAccountEntity.parentAccountEntity.id,
      )

      assertThat(nextPosting).isNull()
    }
  }

  @Nested
  inner class GetFirstPostingsForAllAccounts {

    @Test
    fun `Should return the first posting for accounts by timestamp`() {
      val accountPrisoner = repoTestHelpers.createAccount(ref = "ABC123XX")
      val cashAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = accountPrisoner)

      val accountPrison = repoTestHelpers.createAccount(ref = "LEI")
      val canteenAccount = repoTestHelpers.createSubAccount(ref = "CANT:1001", account = accountPrison)

      val transactionTimestamp = Instant.now()
      // tx entry 1
      val transactionOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp.minusSeconds(60),
        transactionTimeStamp = transactionTimestamp.minusSeconds(60),
        debitSubAccount = cashAccount,
        creditSubAccount = canteenAccount,
        transactionEntrySequence = 1,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
      )

      // tx entry 2
      val transactionTwo = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionTimestamp,
        transactionTimeStamp = transactionTimestamp,
        debitSubAccount = cashAccount,
        creditSubAccount = canteenAccount,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
      )

      val firstPostingPrisoner = transactionOne.postings[0].id
      val firstPostingPrison = transactionOne.postings[1].id
      val firstPostings = postingsDataRepository.getFirstPostingsForAllAccounts()

      assertThat(firstPostings.size).isEqualTo(2)
      assertThat(firstPostings).contains(firstPostingPrisoner)
      assertThat(firstPostings).contains(firstPostingPrison)
    }

    @Test
    fun `Should return the first posting for accounts by timestamp, transaction entrySequence`() {
      val accountPrisoner = repoTestHelpers.createAccount(ref = "ABC123XX")
      val cashAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = accountPrisoner)

      val accountPrison = repoTestHelpers.createAccount(ref = "LEI")
      val canteenAccount = repoTestHelpers.createSubAccount(ref = "CANT:1001", account = accountPrison)

      // tx entry 1
      val transactionBatchTimestamp = Instant.now()

      val transactionEntryTwo = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 2,
        postingCreatedAt = transactionBatchTimestamp,
        transactionTimeStamp = transactionBatchTimestamp,
        debitSubAccount = cashAccount,
        creditSubAccount = canteenAccount,
        transactionEntrySequence = 2,
        debitEntrySequence = 3,
        creditEntrySequence = 4,
      )

      val transactionEntryOne = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionBatchTimestamp,
        transactionTimeStamp = transactionBatchTimestamp,
        debitSubAccount = cashAccount,
        creditSubAccount = canteenAccount,
        transactionEntrySequence = 1,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
      )

      val transactionNow = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = Instant.now(),
        transactionTimeStamp = Instant.now(),
        debitSubAccount = cashAccount,
        creditSubAccount = canteenAccount,
        debitEntrySequence = 1,
        creditEntrySequence = 2,
      )

      val firstPostingPrisoner = transactionEntryOne.postings[0].id
      val firstPostingPrison = transactionEntryOne.postings[1].id
      val firstPostings = postingsDataRepository.getFirstPostingsForAllAccounts()

      assertThat(firstPostings.size).isEqualTo(2)
      assertThat(firstPostings).contains(firstPostingPrisoner)
      assertThat(firstPostings).contains(firstPostingPrison)
    }

    @Test
    fun `Should return the first posting for accounts by posting entrySequence`() {
      val accountPrisoner = repoTestHelpers.createAccount(ref = "ABC123XX")
      val cashAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = accountPrisoner)

      val accountPrison = repoTestHelpers.createAccount(ref = "LEI")
      val canteenAccount = repoTestHelpers.createSubAccount(ref = "CANT:1001", account = accountPrison)

      val transactionEntity = TransactionEntity(
        id = UUID.randomUUID(),
        reference = "TEST_REF",
        amount = 1,
        timestamp = Instant.now(),
        postings = mutableListOf(),
        entrySequence = 1,
        description = "CANTEEN Transactions",
      )

      val postingEntity1 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = cashAccount,
        transactionEntity = transactionEntity,
        entrySequence = 1,
      )
      val postingBalance1 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity1,
        totalSubAccountBalance = 1,
      )

      val postingEntity2 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = canteenAccount,
        transactionEntity = transactionEntity,
        entrySequence = 2,
      )
      val postingBalance2 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity2,
        totalSubAccountBalance = 1,
      )

      val postingEntity3 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = cashAccount,
        transactionEntity = transactionEntity,
        entrySequence = 3,
      )
      val postingBalance3 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity3,
        totalSubAccountBalance = 1,
      )

      val postingEntity4 = PostingEntity(
        id = UUID.randomUUID(),
        createdAt = Instant.now(),
        type = PostingType.DR,
        amount = 1,
        subAccountEntity = canteenAccount,
        transactionEntity = transactionEntity,
        entrySequence = 4,
      )
      val postingBalance4 = PostingBalanceEntity(
        id = UUID.randomUUID(),
        postingEntity = postingEntity4,
        totalSubAccountBalance = 1,
      )

      transactionEntity.postings.add(postingEntity1)
      transactionEntity.postings.add(postingEntity2)
      transactionEntity.postings.add(postingEntity3)
      transactionEntity.postings.add(postingEntity4)

      repoTestHelpers.persist(transactionEntity)

      repoTestHelpers.persist(postingEntity1)
      repoTestHelpers.persist(postingEntity2)
      repoTestHelpers.persist(postingEntity3)
      repoTestHelpers.persist(postingEntity4)

      repoTestHelpers.persist(postingBalance1)
      repoTestHelpers.persist(postingBalance2)
      repoTestHelpers.persist(postingBalance3)
      repoTestHelpers.persist(postingBalance4)

      val firstPostingPrisoner = postingEntity1.id
      val firstPostingPrison = postingEntity2.id
      val firstPostings = postingsDataRepository.getFirstPostingsForAllAccounts()

      assertThat(firstPostings.size).isEqualTo(2)
      assertThat(firstPostings).contains(firstPostingPrisoner)
      assertThat(firstPostings).contains(firstPostingPrison)
    }

    @Test
    fun `Should return the first posting id for accounts when entry sequences are zero`() {
      val accountPrisoner = repoTestHelpers.createAccount(ref = "ABC123XX")
      val cashAccount = repoTestHelpers.createSubAccount(ref = "CASH", account = accountPrisoner)

      val accountPrison = repoTestHelpers.createAccount(ref = "LEI")
      val canteenAccount = repoTestHelpers.createSubAccount(ref = "CANT:1001", account = accountPrison)

      // tx entry 1
      val transactionBatchTimestamp = Instant.now()

      val transactionFirst = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 2,
        postingCreatedAt = transactionBatchTimestamp,
        transactionTimeStamp = transactionBatchTimestamp,
        debitSubAccount = cashAccount,
        creditSubAccount = canteenAccount,
        transactionEntrySequence = 0,
        debitEntrySequence = 0,
        creditEntrySequence = 0,
      )

      val transactionSecond = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        postingCreatedAt = transactionBatchTimestamp,
        transactionTimeStamp = transactionBatchTimestamp,
        debitSubAccount = cashAccount,
        creditSubAccount = canteenAccount,
        transactionEntrySequence = 0,
        debitEntrySequence = 0,
        creditEntrySequence = 0,
      )

      val firstPostingPrisoner = listOf(transactionFirst.postings[0].id, transactionSecond.postings[0].id).minOf { it.toString() }
      val firstPostingPrison = listOf(transactionFirst.postings[1].id, transactionSecond.postings[1].id).minOf { it.toString() }
      val firstPostings = postingsDataRepository.getFirstPostingsForAllAccounts()

      assertThat(firstPostings.size).isEqualTo(2)
      assertThat(firstPostings).contains(UUID.fromString(firstPostingPrisoner))
      assertThat(firstPostings).contains(UUID.fromString(firstPostingPrison))
    }
  }
}
