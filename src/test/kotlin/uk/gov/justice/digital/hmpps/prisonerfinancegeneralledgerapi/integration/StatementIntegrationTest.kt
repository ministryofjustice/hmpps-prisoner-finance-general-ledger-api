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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.StatementEntryResponse
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
      val accountId = UUID.randomUUID()

      val statementListResponse = webTestClient.get()
        .uri("/accounts/$accountId/statement")
        .headers(setAuthorisation(roles = listOf(role)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(statementListResponse).hasSize(0)
    }

    @Test
    fun `return a list of statement entry responses when sent a valid account id that has 2 postings`() {
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
        .expectBody<List<StatementEntryResponse>>()
        .returnResult()
        .responseBody!!

      assertThat(statementEntryResponse).hasSize(2)
      assertThat(statementEntryResponse[0].amount).isEqualTo(transaction.amount)
      assertThat(statementEntryResponse[0].postingType).isEqualTo(PostingType.CR)
      assertThat(statementEntryResponse[0].oppositePostings).hasSize(1)
      assertThat(statementEntryResponse[0].oppositePostings[0].subAccountEntity.id).isEqualTo(cashSubAccount.id)
      assertThat(statementEntryResponse[0].oppositePostings[0].amount).isEqualTo(1L)
      assertThat(statementEntryResponse[0].oppositePostings[0].type).isNotEqualTo(PostingType.DR)

      assertThat(statementEntryResponse[1].amount).isEqualTo(transaction.amount)
      assertThat(statementEntryResponse[1].postingType).isEqualTo(PostingType.DR)
    }
  }
}
