package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import jakarta.transaction.Transactional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.SubAccountDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.responses.SubAccountResponse
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.UUID

class TransactionIntegrationTest @Autowired constructor(
  var transactionDataRepository: TransactionDataRepository,
  var subAccountDataRepository: SubAccountDataRepository,
  var postingsDataRepository: PostingsDataRepository,
  var accountDataRepository: AccountDataRepository,
) : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun resetDB() {
    postingsDataRepository.deleteAllInBatch()
    transactionDataRepository.deleteAllInBatch()
    subAccountDataRepository.deleteAllInBatch()
    accountDataRepository.deleteAllInBatch()
  }

  @Nested
  inner class CreateTransaction {

    var accounts : MutableList<AccountResponse> = mutableListOf()
    var subAccounts : MutableList<SubAccountResponse> = mutableListOf()

    @BeforeEach
    fun seedParentAccounts() {

      for (i in 1 downTo 0 step 1) {
        val accountResponseBody = webTestClient.post()
          .uri("/accounts")
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CreateAccountRequest("$i"))
          .exchange()
          .expectBody<AccountResponse>()
          .returnResult()
          .responseBody!!
        accounts.add(accountResponseBody)
      }

      for(account in accounts) {
        val subAccountResponseBody = webTestClient.post()
          .uri("/accounts/${account.id}/sub-accounts")
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(CreateSubAccountRequest(account.reference))
          .exchange()
          .expectBody<SubAccountResponse>()
          .returnResult()
          .responseBody!!
        subAccounts.add(subAccountResponseBody)
      }

      println(accounts.size)
      assert(accounts.size == 2)
      assert(subAccounts.size == 2)

    }

    @Test
    fun `return a 201 with posted transaction with postings when sent a valid transaction`() {
      // createTransaction(reference: String, createdBy: String, description: String, amount: BigInteger, timestamp: LocalDateTime, postings: List<PostingRequest>): TransactionEntity

      val createPostingRequests: List<CreatePostingRequest> = listOf(CreatePostingRequest(subAccountId = UUID.fromString("00000000-0000-0000-0000-000000000001"), type = PostingType.CR, amount = BigInteger.ONE), CreatePostingRequest(subAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002"), type = PostingType.DR, amount = BigInteger.ONE))

      val transactionResponseBody = webTestClient.post()
        .uri("/transaction")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateTransactionRequest(reference = "TX", description = "DESCRIPTION", amount = BigInteger.ONE, timestamp = LocalDateTime.now(), postings = createPostingRequests))
        .exchange()
        .expectBody<SubAccountResponse>()
        .returnResult()
        .responseBody!!

    }
  }
}