package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.PagedResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class StatementIntegrationTest : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun resetDB() {
    integrationTestHelpers.clearDB()
  }

  @Nested
  inner class GetStatement {
    @ParameterizedTest
    @ValueSource(
      strings = [
        ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO,
        ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW,
      ],
    )
    fun `return an empty list when sent a valid account id that has no postings using either role`(role: String) {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)

      val statementListResponse = webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus().isOk
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(statementListResponse.content).hasSize(0)
    }

    @Test
    fun `returns a list of one statement response with one opposite posting for one to one transaction between accounts`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val prisonerSubAccount = integrationTestHelpers.createSubAccount(prisonerAccount.id, "CASH")
      val prisonSubAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "CANTEEN")

      val transaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = prisonerSubAccount.id,
        creditSubAccountId = prisonSubAccount.id,
        transactionReference = "TX",
        description = "MARS BAR",
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].subAccount.id).isEqualTo(prisonerSubAccount.id)
      assertThat(content[0].amount).isEqualTo(transaction.amount)
      assertThat(content[0].postingType).isEqualTo(PostingType.DR)
      assertThat(content[0].oppositePostings).hasSize(1)
      assertThat(content[0].oppositePostings[0].subAccount.id).isEqualTo(prisonSubAccount.id)
      assertThat(content[0].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(content[0].oppositePostings[0].type).isEqualTo(PostingType.CR)
    }

    @Test
    fun `return a list of statement entries for both postings when the account has a single one to one transaction between its sub-accounts`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val cashSubAccount = integrationTestHelpers.createSubAccount(prisonerAccount.id, "CASH")
      val spendsSubAccount = integrationTestHelpers.createSubAccount(prisonerAccount.id, "SPENDS")

      val transaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = cashSubAccount.id,
        creditSubAccountId = spendsSubAccount.id,
        transactionReference = "TX",
        description = "CASH to SPENDS transaction",
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)

      assertThat(content[0].subAccount.id).isEqualTo(cashSubAccount.id)
      assertThat(content[0].amount).isEqualTo(transaction.amount)
      assertThat(content[0].postingType).isEqualTo(PostingType.DR)
      assertThat(content[0].oppositePostings).hasSize(1)
      assertThat(content[0].oppositePostings[0].subAccount.id).isEqualTo(spendsSubAccount.id)
      assertThat(content[0].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(content[0].oppositePostings[0].type).isEqualTo(PostingType.CR)

      assertThat(content[1].subAccount.id).isEqualTo(spendsSubAccount.id)
      assertThat(content[1].amount).isEqualTo(transaction.amount)
      assertThat(content[1].postingType).isEqualTo(PostingType.CR)
      assertThat(content[1].oppositePostings).hasSize(1)
      assertThat(content[1].oppositePostings[0].subAccount.id).isEqualTo(cashSubAccount.id)
      assertThat(content[1].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(content[1].oppositePostings[0].type).isEqualTo(PostingType.DR)
    }

    @Test
    fun `Should return postings ordered by transaction timestamp, transaction entrySequence, and posting entrySequence`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val timestamp = Instant.now()

      val subAccountTransactionEntry1Posting1 = integrationTestHelpers.createSubAccount(prisonerAccount.id, "T1-P1")
      val subAccountTransactionEntry1Posting2 = integrationTestHelpers.createSubAccount(prisonerAccount.id, "T1-P2")
      val transactionFirst = integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        creditSubAccountId = subAccountTransactionEntry1Posting1.id,
        debitSubAccountId = subAccountTransactionEntry1Posting2.id,
        transactionReference = "TX",
        description = "Transaction Entry Sequence 1",
        timestamp = timestamp,
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      val subAccountTransactionEntry2Posting3 = integrationTestHelpers.createSubAccount(prisonerAccount.id, "T2-P3")
      val subAccountTransactionEntry2Posting4 = integrationTestHelpers.createSubAccount(prisonerAccount.id, "T2-P4")
      val transactionSecond = integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        creditSubAccountId = subAccountTransactionEntry2Posting3.id,
        debitSubAccountId = subAccountTransactionEntry2Posting4.id,
        transactionReference = "TX",
        description = "Transaction Entry Sequence 2",
        timestamp = timestamp,
        transactionEntrySequence = 2,
        postingEntrySequence = Pair(3L, 4L),
      )

      val subAccountTransactionInThePastPostin1 = integrationTestHelpers.createSubAccount(prisonerAccount.id, "TPast-P1")
      val subAccountTransactionInThePastPostin2 = integrationTestHelpers.createSubAccount(prisonerAccount.id, "TPast-P2")
      val transactionInThePast = integrationTestHelpers.createOneToOneTransaction(
        amount = 3L,
        creditSubAccountId = subAccountTransactionInThePastPostin1.id,
        debitSubAccountId = subAccountTransactionInThePastPostin2.id,
        transactionReference = "TX",
        description = "Transaction in the past",
        timestamp = timestamp.minusSeconds(1000),
        transactionEntrySequence = 1,
        postingEntrySequence = Pair(1L, 2L),
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(6)

      // Transaction EntrySequence 2 Posting 4
      assertThat(content[0].description).isEqualTo(transactionSecond.description)
      assertThat(content[0].amount).isEqualTo(transactionSecond.amount)
      assertThat(content[0].subAccount.id).isEqualTo(subAccountTransactionEntry2Posting4.id)

      // Transaction EntrySequence 2 Posting 3
      assertThat(content[1].description).isEqualTo(transactionSecond.description)
      assertThat(content[1].amount).isEqualTo(transactionSecond.amount)
      assertThat(content[1].subAccount.id).isEqualTo(subAccountTransactionEntry2Posting3.id)

      // Transaction EntrySequence 1 Posting 2
      assertThat(content[2].description).isEqualTo(transactionFirst.description)
      assertThat(content[2].amount).isEqualTo(transactionFirst.amount)
      assertThat(content[2].subAccount.id).isEqualTo(subAccountTransactionEntry1Posting2.id)

      // Transaction EntrySequence 1 Posting 1
      assertThat(content[3].description).isEqualTo(transactionFirst.description)
      assertThat(content[3].amount).isEqualTo(transactionFirst.amount)
      assertThat(content[3].subAccount.id).isEqualTo(subAccountTransactionEntry1Posting1.id)

      // Transaction in the past posting 2
      assertThat(content[4].description).isEqualTo(transactionInThePast.description)
      assertThat(content[4].amount).isEqualTo(transactionInThePast.amount)
      assertThat(content[4].subAccount.id).isEqualTo(subAccountTransactionInThePastPostin2.id)

      // Transaction in the past posting 1
      assertThat(content[5].description).isEqualTo(transactionInThePast.description)
      assertThat(content[5].amount).isEqualTo(transactionInThePast.amount)
      assertThat(content[5].subAccount.id).isEqualTo(subAccountTransactionInThePastPostin1.id)
    }

    @Test
    fun `return a list of one statement response with one opposite posting for a one to many transaction where the account id is in the many side`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonerTwoAccount = integrationTestHelpers.createAccount("A1234XX", AccountType.PRISONER)

      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val cashSubAccountTwo = integrationTestHelpers.createSubAccount(prisonerTwoAccount.id, "CASH")

      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = canteenAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "CASH to SPENDS transaction",
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].subAccount.id).isEqualTo(cashSubAccountOne.id)
      assertThat(content[0].amount).isEqualTo(1L)
      assertThat(content[0].postingType).isEqualTo(PostingType.CR)
      assertThat(content[0].oppositePostings).hasSize(1)
      assertThat(content[0].oppositePostings[0].subAccount.id).isEqualTo(canteenAccount.id)
      assertThat(content[0].oppositePostings[0].amount).isEqualTo(2L)
      assertThat(content[0].oppositePostings[0].type).isEqualTo(PostingType.DR)
    }

    @Test
    fun `return a list of one statement response with multiple opposite postings for a one to many transaction where the account id is the one side`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonerTwoAccount = integrationTestHelpers.createAccount("A1234XX", AccountType.PRISONER)

      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val cashSubAccountTwo = integrationTestHelpers.createSubAccount(prisonerTwoAccount.id, "CASH")

      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = canteenAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "CASH to SPENDS transaction",
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)
      assertThat(content[0].subAccount.id).isEqualTo(canteenAccount.id)
      assertThat(content[0].amount).isEqualTo(2L)
      assertThat(content[0].postingType).isEqualTo(PostingType.DR)
      assertThat(content[0].oppositePostings).hasSize(2)

      assertThat(content[0].oppositePostings[0].subAccount.id).isEqualTo(cashSubAccountTwo.id)
      assertThat(content[0].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(content[0].oppositePostings[0].type).isEqualTo(PostingType.CR)

      assertThat(content[0].oppositePostings[1].subAccount.id).isEqualTo(cashSubAccountOne.id)
      assertThat(content[0].oppositePostings[1].amount).isEqualTo(1L)
      assertThat(content[0].oppositePostings[1].type).isEqualTo(PostingType.CR)
    }

    @Test
    fun `return a list of statements after and on the startDate`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      val christmasDayInstant = LocalDateTime.of(2025, 12, 25, 0, 0, 0).toInstant(ZoneOffset.UTC)
      val boxingDayInstant = LocalDateTime.of(2025, 12, 26, 0, 0, 0).toInstant(ZoneOffset.UTC)
      val twentySeventhDayInstant = LocalDateTime.of(2025, 12, 27, 0, 0, 0).toInstant(ZoneOffset.UTC)

      integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "CHRISTMAS DAY",
        timestamp = christmasDayInstant,
      )

      val boxingDayTransaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "BOXING DAY",
        timestamp = boxingDayInstant,
      )

      val twentySeventhTransaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "DAY AFTER BOXING DAY",
        timestamp = twentySeventhDayInstant,
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?startDate=2025-12-26")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].transactionId).isEqualTo(twentySeventhTransaction.id)
      assertThat(content[1].transactionId).isEqualTo(boxingDayTransaction.id)
    }

    @Test
    fun `return a list of statements before or on the endDate`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      val christmasDayInstant = LocalDateTime.of(2025, 12, 25, 0, 0, 0).toInstant(ZoneOffset.UTC)
      val boxingDayInstant = LocalDateTime.of(2025, 12, 26, 23, 0, 0).toInstant(ZoneOffset.UTC)
      val twentySeventhDayInstant = LocalDateTime.of(2025, 12, 27, 0, 0, 0).toInstant(ZoneOffset.UTC)

      val christmasDayTransaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "CHRISTMAS DAY",
        timestamp = christmasDayInstant,
      )

      val boxingDayTransaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "BOXING DAY",
        timestamp = boxingDayInstant,
      )

      integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "DAY AFTER BOXING DAY",
        timestamp = twentySeventhDayInstant,
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?endDate=2025-12-26")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].transactionId).isEqualTo(boxingDayTransaction.id)
      assertThat(content[1].transactionId).isEqualTo(christmasDayTransaction.id)
    }

    @Test
    fun `return a list of statements between startDate and endDate (inclusive)`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      val christmasEveInstant = LocalDateTime.of(2025, 12, 24, 0, 0, 0).toInstant(ZoneOffset.UTC)
      val christmasDayInstant = LocalDateTime.of(2025, 12, 25, 0, 0, 0).toInstant(ZoneOffset.UTC)
      val boxingDayInstant = LocalDateTime.of(2025, 12, 26, 23, 0, 0).toInstant(ZoneOffset.UTC)
      val twentySeventhDayInstant = LocalDateTime.of(2025, 12, 27, 0, 0, 0).toInstant(ZoneOffset.UTC)

      integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "CHRISTMAS EVE",
        timestamp = christmasEveInstant,
      )

      val christmasDayTransaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "CHRISTMAS DAY",
        timestamp = christmasDayInstant,
      )

      val boxingDayTransaction = integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "BOXING DAY",
        timestamp = boxingDayInstant,
      )

      integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "DAY AFTER BOXING DAY",
        timestamp = twentySeventhDayInstant,
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?startDate=2025-12-25&endDate=2025-12-26")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)
      assertThat(content[0].transactionId).isEqualTo(boxingDayTransaction.id)
      assertThat(content[1].transactionId).isEqualTo(christmasDayTransaction.id)
    }

    @Test
    fun `should return a default page when given no page queries`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      repeat(26) {
        integrationTestHelpers.createOneToOneTransaction(
          amount = 1L,
          debitSubAccountId = canteenAccount.id,
          creditSubAccountId = cashSubAccountOne.id,
          transactionReference = "TX",
          description = "TEST",
          timestamp = LocalDateTime.of(2025, 12, 24, 0, 0, 0).toInstant(ZoneOffset.UTC),
        )
      }

      val statementEntryPage = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(statementEntryPage.pageSize).isEqualTo(25)
      assertThat(statementEntryPage.pageNumber).isEqualTo(1)
      assertThat(statementEntryPage.isLastPage).isEqualTo(false)
      assertThat(statementEntryPage.totalPages).isEqualTo(2)
      assertThat(statementEntryPage.totalElements).isEqualTo(26)
    }

    @Test
    fun `should return the page number requested`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      repeat(26) {
        integrationTestHelpers.createOneToOneTransaction(
          amount = 1L,
          debitSubAccountId = canteenAccount.id,
          creditSubAccountId = cashSubAccountOne.id,
          transactionReference = "TX",
          description = "TEST",
          timestamp = LocalDateTime.of(2025, 12, 24, 0, 0, 0).toInstant(ZoneOffset.UTC),
        )
      }

      val statementEntryPage = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?pageNumber=2")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(statementEntryPage.content.size).isEqualTo(1)
      assertThat(statementEntryPage.pageSize).isEqualTo(25)
      assertThat(statementEntryPage.pageNumber).isEqualTo(2)
      assertThat(statementEntryPage.isLastPage).isEqualTo(true)
      assertThat(statementEntryPage.totalPages).isEqualTo(2)
      assertThat(statementEntryPage.totalElements).isEqualTo(26)
    }

    @Test
    fun `should return a page with specific page size requested`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      repeat(26) {
        integrationTestHelpers.createOneToOneTransaction(
          amount = 1L,
          debitSubAccountId = canteenAccount.id,
          creditSubAccountId = cashSubAccountOne.id,
          transactionReference = "TX",
          description = "TEST",
          timestamp = LocalDateTime.of(2025, 12, 24, 0, 0, 0).toInstant(ZoneOffset.UTC),
        )
      }

      val statementEntryPage = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?pageNumber=1&pageSize=2")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(statementEntryPage.pageSize).isEqualTo(2)
      assertThat(statementEntryPage.pageNumber).isEqualTo(1)
      assertThat(statementEntryPage.isLastPage).isEqualTo(false)
      assertThat(statementEntryPage.totalPages).isEqualTo(13)
      assertThat(statementEntryPage.totalElements).isEqualTo(26)
    }

    @Test
    fun `return a list of statements filtered on credit type for prisoner`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val spendsSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "SPENDS")

      integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 1",
        timestamp = Instant.now(),
      )

      integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 2",
        timestamp = Instant.now(),
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?credit=true")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)

      assertThat(content.all { posting -> posting.postingType == PostingType.CR }).isTrue()
    }

    @Test
    fun `return a list of statements filtered on credit type for prison`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")

      val prisonerTwoAccount = integrationTestHelpers.createAccount("Z9234BC", AccountType.PRISONER)
      val cashSubAccountTwo = integrationTestHelpers.createSubAccount(prisonerTwoAccount.id, "CASH")

      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)
      val canteenSubAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "CANT")

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = canteenSubAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "Transaction 1",
        oneToManyPostingType = PostingType.CR,
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonAccount.id}/statement?credit=true")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)

      assertThat(content.all { posting -> posting.postingType == PostingType.CR }).isTrue()
    }

    @Test
    fun `return a list of statements filtered on debit type for prisoner`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val spendsSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "SPENDS")

      integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 1",
        timestamp = Instant.now(),
      )

      integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 2",
        timestamp = Instant.now(),
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?debit=true")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)

      assertThat(content.all { posting -> posting.postingType == PostingType.DR }).isTrue()
    }

    @Test
    fun `return a list of statements filtered on debit type for prison`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")

      val prisonerTwoAccount = integrationTestHelpers.createAccount("Z9234BC", AccountType.PRISONER)
      val cashSubAccountTwo = integrationTestHelpers.createSubAccount(prisonerTwoAccount.id, "CASH")

      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)
      val canteenSubAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "CANT")

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = canteenSubAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "Transaction 1",
        oneToManyPostingType = PostingType.DR,
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonAccount.id}/statement?debit=true")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)

      assertThat(content.all { posting -> posting.postingType == PostingType.DR }).isTrue()
    }

    @Test
    fun `return a list of statements filtered on credit & debit type for prisoner`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val spendsSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "SPENDS")

      integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 1",
        timestamp = Instant.now(),
      )

      integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 2",
        timestamp = Instant.now(),
      )

      var statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?debit=true&credit=true")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val contentFilteredOnBoth = statementEntryResponse.content

      assertThat(contentFilteredOnBoth).hasSize(4)

      statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?debit=false&credit=false")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val contentNotFilteredOnBoth = statementEntryResponse.content

      assertThat(contentNotFilteredOnBoth).hasSize(4)
    }

    @Test
    fun `return a list of statements filtered on credit & debit type for prison`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")

      val prisonerTwoAccount = integrationTestHelpers.createAccount("Z9234BC", AccountType.PRISONER)
      val cashSubAccountTwo = integrationTestHelpers.createSubAccount(prisonerTwoAccount.id, "CASH")

      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)
      val canteenSubAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "CANT")

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = canteenSubAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "Transaction 1",
        oneToManyPostingType = PostingType.DR,
      )

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = canteenSubAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "Transaction 2",
        oneToManyPostingType = PostingType.CR,
      )

      val statementEntryResponseForBoth = webTestClient.get()
        .uri("/accounts/${prisonAccount.id}/statement?debit=true&credit=true")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      var content = statementEntryResponseForBoth.content

      assertThat(content).hasSize(2)

      val statementEntryResponseForNone = webTestClient.get()
        .uri("/accounts/${prisonAccount.id}/statement?debit=false&credit=false")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      content = statementEntryResponseForNone.content

      assertThat(content).hasSize(2)
    }

    @Test
    fun `return a list of statements filtered by subAccount for prisoner`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val spendsSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "SPENDS")

      integrationTestHelpers.createOneToOneTransaction(
        amount = 1L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 1",
        timestamp = Instant.now(),
      )

      integrationTestHelpers.createOneToOneTransaction(
        amount = 2L,
        debitSubAccountId = spendsSubAccountOne.id,
        creditSubAccountId = cashSubAccountOne.id,
        transactionReference = "TX",
        description = "Transaction 2",
        timestamp = Instant.now(),
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?subAccountId=${cashSubAccountOne.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(2)

      assertThat(content.all { posting -> posting.subAccount.id == cashSubAccountOne.id }).isTrue()
    }

    @Test
    fun `return a list of statements filtered by subAccount for prison`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")

      val prisonerTwoAccount = integrationTestHelpers.createAccount("Z9234BC", AccountType.PRISONER)
      val cashSubAccountTwo = integrationTestHelpers.createSubAccount(prisonerTwoAccount.id, "CASH")

      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)
      val canteenSubAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")
      val advanceSubAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1502:ADV")

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = canteenSubAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "Transaction Cant",
        oneToManyPostingType = PostingType.DR,
      )

      integrationTestHelpers.createOneToManyTransaction(
        amountPerAccount = 1L,
        oneToManySubAccountId = advanceSubAccount.id,
        manyToOneSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
        transactionReference = "TX",
        description = "Transaction Adv",
        oneToManyPostingType = PostingType.DR,
      )

      val statementEntryResponse = webTestClient.get()
        .uri("/accounts/${prisonAccount.id}/statement?subAccountId=${canteenSubAccount.id}")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<PagedResponse<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      val content = statementEntryResponse.content

      assertThat(content).hasSize(1)

      assertThat(content.all { posting -> posting.subAccount.id == canteenSubAccount.id }).isTrue()
    }

    @Test
    fun `should return 400 if subAccountId is not valid`() {
      webTestClient.get()
        .uri("/accounts/${UUID.randomUUID()}/statement?subAccountId=0")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when page requested is out of range`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      val prisonAccount = integrationTestHelpers.createAccount("LEI", AccountType.PRISON)

      val cashSubAccountOne = integrationTestHelpers.createSubAccount(prisonerOneAccount.id, "CASH")
      val canteenAccount = integrationTestHelpers.createSubAccount(prisonAccount.id, "1001:CANT")

      repeat(26) {
        integrationTestHelpers.createOneToOneTransaction(
          amount = 1L,
          debitSubAccountId = canteenAccount.id,
          creditSubAccountId = cashSubAccountOne.id,
          transactionReference = "TX",
          description = "TEST",
          timestamp = LocalDateTime.of(2025, 12, 24, 0, 0, 0).toInstant(ZoneOffset.UTC),
        )
      }

      webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?pageNumber=10&pageSize=25")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 if page number is less than one`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)

      webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?pageNumber=0")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 if page size is less than one`() {
      val prisonerOneAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)

      webTestClient.get()
        .uri("/accounts/${prisonerOneAccount.id}/statement?pageSize=0")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when account id is invalid`() {
      webTestClient.get()
        .uri("/accounts/INVALID_STRING/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO)))
        .exchange()
        .expectStatus().isBadRequest
        .expectBody()
        .returnResult()
        .responseBody!!
    }

    @Test
    fun `should return 400 when startDate query is malformed`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement?startDate=XXXX")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when endDate query is malformed`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement?endDate=XXXX")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 401 when requesting account without authorisation headers`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting account with incorrect role`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `should return 404 when sent a valid UUID that doesn't exist`() {
      val uuid = UUID.randomUUID()
      webTestClient.get()
        .uri("/accounts/$uuid/statement")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO)))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return 400 when credit query is malformed`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement?credit=XXXX")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when debit query is malformed`() {
      val prisonerAccount = integrationTestHelpers.createAccount("A1234BC", AccountType.PRISONER)
      webTestClient.get()
        .uri("/accounts/${prisonerAccount.id}/statement?debit=XXXX")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RO)))
        .exchange()
        .expectStatus().isBadRequest
    }
  }
}
