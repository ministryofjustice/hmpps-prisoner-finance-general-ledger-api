package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import com.fasterxml.jackson.module.kotlin.jsonMapper
import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.config.ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import java.time.LocalDateTime
import java.util.*

class AccountIntegrationTest @Autowired constructor(
  var accountRepository: AccountRepository,
) : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun resetDB() {
    accountRepository.deleteAllInBatch()
  }

  @Nested
  inner class CreateAccount {

    @Test
    fun `should return 201 OK and the created account when the correct role is provided`() {
      val responseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(responseBody.id).isInstanceOf(UUID::class.java)
    }

    @Test
    fun `should return 400 Bad Request if the reference submitted already has an associated account`() {
      val responseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(responseBody.id).isInstanceOf(UUID::class.java)

      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 Bad Request if the reference submitted already has an associated account in a different casing`() {
      val responseBody = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(responseBody.id).isInstanceOf(UUID::class.java)

      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("test_account_ref"))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `createAccount should return 401 without authorisation headers`() {
      webTestClient.post()
        .uri("/accounts")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `createAccount should return 403 Forbidden when role is incorrect`() {
      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `createAccount should return 400 when sent a malformed body`() {
      webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(jsonMapper { "wrong_key" to "wrong_value" })
        .exchange()
        .expectStatus().isBadRequest
    }
  }

  @Nested
  inner class GetAccount {
    private fun seedDummyAccount(): UUID {
      val uuid = webTestClient.post()
        .uri("/accounts")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!.id

      return uuid
    }

    @Test
    fun `should return 200 OK and the correct account`() {
      val testUUID = seedDummyAccount()
      val responseBody = webTestClient.get()
        .uri("/accounts/$testUUID")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountResponse>()
        .returnResult()
        .responseBody!!

      assertThat(responseBody.reference).isEqualTo("TEST_ACCOUNT_REF")
      assertThat(responseBody.createdBy).isEqualTo("AUTH_ADM")
      assertThat(responseBody.createdAt).isInstanceOf(LocalDateTime::class.java)
      assertThat(responseBody.id).isEqualTo(testUUID)
    }

    @Test
    fun `should return 404 Not Found when the account does not exist`() {
      val incorrectUUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
      webTestClient.get()
        .uri("/accounts/$incorrectUUID")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `should return 400 Bad Request when passed an invalid UUID`() {
      webTestClient.get()
        .uri("/accounts/not-a-uuid")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__GENERAL_LEDGER__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `should return 401 when requesting account without authorisation headers`() {
      val uuid = seedDummyAccount()
      webTestClient.get()
        .uri("/accounts/$uuid")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `should return 403 when requesting account with incorrect role`() {
      val uuid = seedDummyAccount()
      webTestClient.get()
        .uri("/accounts/$uuid")
        .headers(setAuthorisation(roles = listOf("ROLE__WRONG_ROLE")))
        .exchange()
        .expectStatus().isForbidden
    }
  }
}
