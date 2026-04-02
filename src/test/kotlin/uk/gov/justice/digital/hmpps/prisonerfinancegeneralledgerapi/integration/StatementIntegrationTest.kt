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
      assertThat(content[0].subAccount.id).isEqualTo(spendsSubAccount.id)
      assertThat(content[0].amount).isEqualTo(transaction.amount)
      assertThat(content[0].postingType).isEqualTo(PostingType.CR)
      assertThat(content[0].oppositePostings).hasSize(1)
      assertThat(content[0].oppositePostings[0].subAccount.id).isEqualTo(cashSubAccount.id)
      assertThat(content[0].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(content[0].oppositePostings[0].type).isEqualTo(PostingType.DR)

      assertThat(content[1].subAccount.id).isEqualTo(cashSubAccount.id)
      assertThat(content[1].amount).isEqualTo(transaction.amount)
      assertThat(content[1].postingType).isEqualTo(PostingType.DR)
      assertThat(content[1].oppositePostings).hasSize(1)
      assertThat(content[1].oppositePostings[0].subAccount.id).isEqualTo(spendsSubAccount.id)
      assertThat(content[1].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(content[1].oppositePostings[0].type).isEqualTo(PostingType.CR)
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
        amountToCreditEachAccount = 1L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
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
        amountToCreditEachAccount = 1L,
        debitSubAccountId = canteenAccount.id,
        creditSubAccountIds = listOf(cashSubAccountOne.id, cashSubAccountTwo.id),
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
      assertThat(content[0].oppositePostings[0].subAccount.id).isEqualTo(cashSubAccountOne.id)
      assertThat(content[0].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(content[0].oppositePostings[0].type).isEqualTo(PostingType.CR)

      assertThat(content[0].oppositePostings[1].subAccount.id).isEqualTo(cashSubAccountTwo.id)
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
  }
}
