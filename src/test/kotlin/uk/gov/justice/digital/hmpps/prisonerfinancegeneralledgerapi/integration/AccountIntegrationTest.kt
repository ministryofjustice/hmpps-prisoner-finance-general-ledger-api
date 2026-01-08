package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import com.fasterxml.jackson.module.kotlin.jsonMapper
import jakarta.transaction.Transactional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import java.time.LocalDateTime
import java.util.UUID

class AccountIntegrationTest @Autowired constructor(
  var accountRepository: AccountRepository,
) : IntegrationTestBase() {

  @Transactional
  @BeforeEach
  fun resetDB() {
    accountRepository.deleteAllInBatch()
    accountRepository.flush()
  }

  @Nested
  inner class CreateAccount {

    @Test
    fun `should return 201 OK and the created account when the correct role is provided`() {
      webTestClient.post()
        .uri("/account")
        .headers(setAuthorisation(scopes = listOf("write")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.reference").isEqualTo("TEST_ACCOUNT_REF")
        .jsonPath("$.createdBy").isEqualTo("AUTH_ADM")
        .jsonPath("$.createdAt").value { it: String -> LocalDateTime.parse(it) }
        .jsonPath("$.uuid").value { it: String -> UUID.fromString(it) }
    }

    @Test
    fun `should return 400 Bad Request if the reference submitted already has an associated account`() {
      webTestClient.post()
        .uri("/account")
        .headers(setAuthorisation(scopes = listOf("write")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.reference").isEqualTo("TEST_ACCOUNT_REF")
        .jsonPath("$.createdBy").isEqualTo("AUTH_ADM")
        .jsonPath("$.createdAt").value { it: String -> LocalDateTime.parse(it) }
        .jsonPath("$.uuid").value { it: String -> UUID.fromString(it) }

      webTestClient.post()
        .uri("/account")
        .headers(setAuthorisation(scopes = listOf("write")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `createAccount should return 401 without authorisation headers`() {
      webTestClient.post()
        .uri("/account")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun `createAccount should return 403 Forbidden when role is incorrect`() {
      webTestClient.post()
        .uri("/account")
        .headers(setAuthorisation(scopes = listOf("read")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest("TEST_ACCOUNT_REF"))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `createAccount should return 400 when sent a malformed body`() {
      webTestClient.post()
        .uri("/account")
        .headers(setAuthorisation(scopes = listOf("write")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(jsonMapper { "wrong_key" to "wrong_value" })
        .exchange()
        .expectStatus().isBadRequest
    }
  }

  @Nested
  inner class GetAccount {
    private fun seedDummyAccount(reference: String) {
      webTestClient.post()
        .uri("/account")
        .headers(setAuthorisation(scopes = listOf("write")))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(CreateAccountRequest(reference))
        .exchange()
        .expectStatus().isCreated
    }

    @Test
    fun `should return 200 OK and the correct account`() {
      seedDummyAccount("TEST_ACCOUNT_REF")
      webTestClient.get()
        .uri("/account/TEST_ACCOUNT_REF")
        .headers(setAuthorisation(scopes = listOf("read")))
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.reference").isEqualTo("TEST_ACCOUNT_REF")
        .jsonPath("$.createdBy").isEqualTo("AUTH_ADM")
        .jsonPath("$.createdAt").value { it: String -> LocalDateTime.parse(it) }
        .jsonPath("$.uuid").value { it: String -> UUID.fromString(it) }
    }
  }
}
