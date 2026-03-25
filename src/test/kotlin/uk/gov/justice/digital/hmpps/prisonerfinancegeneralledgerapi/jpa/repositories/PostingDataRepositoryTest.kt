package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.ContainersConfig
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.StatementBalanceEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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

  @BeforeEach
  fun setup() {
    repoTestHelpers.clearDb()
  }

  @Nested
  inner class GetPostingsForAccount {
    @Test
    fun `Should return an empty list when an account has no postings`() {
      val parentAccountId = UUID.randomUUID()
      val postings = postingsDataRepository.getPostingsByAccountId(parentAccountId)
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
        transactionDateTime = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountTwoSubAccountOne,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id)
      assertThat(postings).hasSize(1)
    }

    @Test
    fun `Should return all postings across multiple sub accounts `() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = Instant.now(),
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id)
      assertThat(postings).hasSize(2)
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
        repoTestHelpers.createOneToOneTransaction(1, Instant.now(), accountTwoSubAccountOne, accountOneSubAccountOne)
      }

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), accountOneSubAccountOne, accountTwoSubAccountOne)
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
      repoTestHelpers.createOneToOneTransaction(100, LocalDateTime.now().minusDays(2).toInstant(java.time.ZoneOffset.UTC), accountOneSubAccountOne, accountWithNoMoney)

      val statementBalanceFromYesterday = StatementBalanceEntity(amount = 0, subAccountEntity = accountOneSubAccountOne, balanceDateTime = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC))

      // txFromTodayToInclude
      repoTestHelpers.createOneToOneTransaction(50, Instant.now(), accountOneSubAccountOne, accountWithNoMoney)

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

      repoTestHelpers.createOneToOneTransaction(10, Instant.now(), prisonerOneCash, prisonACanteen)
      repoTestHelpers.createOneToOneTransaction(5, Instant.now(), prisonACanteen, prisonerOneCash)

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

      repoTestHelpers.createOneToOneTransaction(15, Instant.now(), prisonerCashAccount, prisonACanteen)
      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), prisonerCashAccount, prisonBCatalogue)

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

      repoTestHelpers.createOneToOneTransaction(1, Instant.now(), prisonerTwoCashAccount, prisonACash)

      repoTestHelpers.createOneToOneTransaction(5, Instant.now(), prisonACash, prisonerOneCashAccount)

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
        PostingEntity(amount = 1, type = PostingType.CR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF")),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(1)
    }

    @Test
    fun `Should sum debits negatively`() {
      val postings = listOf(
        PostingEntity(amount = 1, type = PostingType.DR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF")),
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
          ),
        ),
        PostingEntity(amount = 1, type = PostingType.DR, subAccountEntity = SubAccountEntity(reference = "TEST_SUB_ACCOUNT_REF_1"), transactionEntity = TransactionEntity(reference = "TEST_TRANSACTION_REF")),
      )
      val balance = postingsDataRepository.calculateBalanceFromPostings(postings)
      assertThat(balance).isEqualTo(2)
    }
  }

  @Nested
  inner class GetPostingsWithSpec {

    @Test
    fun `Should return all postings on or after the start date supplied`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(java.time.ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
      val timeTodayAtOneAM = LocalDate.now().atStartOfDay().plusHours(1).toInstant(java.time.ZoneOffset.UTC)

      // TX from 1 day ago
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      val txAtMidnight = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from at one AM
      val txAtOneAM = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTodayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, startDate = timeTodayAtMidnight)

      // two tx today with 4 postings for the account/subaccount transfer == 4 posting
      assertThat(postings).hasSize(4)

      assertThat(postings.first().transactionEntity.id).isEqualTo(txAtMidnight.id)
      assertThat(postings.last().transactionEntity.id).isEqualTo(txAtOneAM.id)
    }

    @Test
    fun `Should return all postings on or before the end date supplied`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(java.time.ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
      val timeTomorrowAtMidnight = LocalDate.now().atStartOfDay().plusDays(1).toInstant(java.time.ZoneOffset.UTC)

      // TX from 1 day ago
      val txFromYesterday = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      val txFromMidnight = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from tomorrow at midnight
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTomorrowAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, endDate = timeTodayAtMidnight)

      // two tx today with 4 postings for the account/subaccount transfer == 4 posting
      assertThat(postings).hasSize(4)

      assertThat(postings.first().transactionEntity.id).isEqualTo(txFromYesterday.id)
      assertThat(postings.last().transactionEntity.id).isEqualTo(txFromMidnight.id)
    }

    @Test
    fun `should return all posting between or on start and end date supplied`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(java.time.ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
      val timeTodayAtNoon = LocalDate.now().atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC)
      val timeTomorrowAtMidnight = LocalDate.now().atStartOfDay().plusDays(1).toInstant(java.time.ZoneOffset.UTC)
      val timeTomorrowAtOneAM = LocalDate.now().atStartOfDay().plusDays(1).plusHours(1).toInstant(java.time.ZoneOffset.UTC)

      // TX from 1 day ago
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      val txFromMidnight = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at noon
      val txFromTodayAtNoon = repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTodayAtNoon,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from tomorrow at one AM
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTomorrowAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, startDate = timeTodayAtMidnight, endDate = timeTomorrowAtMidnight)

      // two tx today with 4 postings for the account/subaccount transfer == 4 posting
      assertThat(postings).hasSize(4)

      assertThat(postings.first().transactionEntity.id).isEqualTo(txFromMidnight.id)
      assertThat(postings.last().transactionEntity.id).isEqualTo(txFromTodayAtNoon.id)
    }

    @Test
    fun `should return empty list if start and end date are transposed`() {
      accountOne = repoTestHelpers.createAccount(ref = "ABC123XX")
      accountOneSubAccountOne = repoTestHelpers.createSubAccount(ref = "CASH", account = accountOne)
      accountOneSubAccountTwo = repoTestHelpers.createSubAccount(ref = "SPENDS", account = accountOne)

      val timeYesterdayAtOneAM = LocalDate.now().minusDays(1).atStartOfDay().plusHours(1).toInstant(java.time.ZoneOffset.UTC)
      val timeTodayAtMidnight = LocalDate.now().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
      val timeTodayAtNoon = LocalDate.now().atStartOfDay().plusHours(12).toInstant(java.time.ZoneOffset.UTC)
      val timeTomorrowAtMidnight = LocalDate.now().atStartOfDay().plusDays(1).toInstant(java.time.ZoneOffset.UTC)
      val timeTomorrowAtOneAM = LocalDate.now().atStartOfDay().plusDays(1).plusHours(1).toInstant(java.time.ZoneOffset.UTC)

      // TX from 1 day ago
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeYesterdayAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at midnight
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTodayAtMidnight,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from today at noon
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTodayAtNoon,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      // TX from tomorrow at one AM
      repoTestHelpers.createOneToOneTransaction(
        transactionAmount = 1,
        transactionDateTime = timeTomorrowAtOneAM,
        debitSubAccount = accountOneSubAccountOne,
        creditSubAccount = accountOneSubAccountTwo,
      )

      val postings = postingsDataRepository.getPostingsByAccountId(accountId = accountOne.id, startDate = timeTomorrowAtMidnight, endDate = timeTodayAtMidnight)

      assertThat(postings).hasSize(0)
    }
  }
}
