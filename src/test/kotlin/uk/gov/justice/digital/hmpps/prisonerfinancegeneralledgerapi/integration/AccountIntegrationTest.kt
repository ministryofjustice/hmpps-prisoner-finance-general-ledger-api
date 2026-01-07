package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.integration

import com.fasterxml.jackson.module.kotlin.jsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.CreateAccountRequest
import java.time.LocalDateTime
import java.util.UUID

class AccountIntegrationTest : IntegrationTestBase() {

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

//  @RepositoryTest
  @Nested
  inner class GetAccount {
//  @Autowired constructor(
//    val entityManager: TestEntityManager,
//    val accountRepository: AccountRepository
//  )

    lateinit var dummyAccount: Account

    @BeforeEach
    fun seedDummyAccount() {
      val dummyUUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
      val dummyDate = LocalDateTime.of(2025, 12, 25, 0, 0, 0)
      dummyAccount = Account(uuid = dummyUUID, createdAt = dummyDate, reference = "TEST_ACCOUNT_REF", createdBy = "TEST_USERNAME")
//      accountRepository.save(dummyAccount)
    }

    @Test
    fun `should return 200 OK and the correct account`() {
      val response = webTestClient.get()
        .uri("/account/TEST_ACCOUNT_REF")
        .headers(setAuthorisation(scopes = listOf("read")))
        .exchange()
        .expectStatus().isOk
        .returnResult<ResponseEntity<Account>>()

      assertThat(response.responseBody).isNotNull()
      println("****************")
      println(response.responseBody)
//      assertThat(response.responseBody).is - need to prove the shape of the account
    }
  }
}
