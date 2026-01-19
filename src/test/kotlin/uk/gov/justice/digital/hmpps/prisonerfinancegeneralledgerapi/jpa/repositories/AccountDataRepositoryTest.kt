package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import java.util.UUID

@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
class AccountDataRepositoryTest @Autowired constructor(
  val entityManager: TestEntityManager,
  val accountDataRepository: AccountDataRepository,
) {

  private lateinit var testAccountEntity: AccountEntity

  @BeforeEach
  fun setup() {
    testAccountEntity = AccountEntity(
      reference = "TEST_ACCOUNT_REF",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    )
    entityManager.persist(testAccountEntity)
  }

  @Nested
  inner class FindAccountById {

    @Test
    fun `Should return an account entity matching the provided ID`() {
      val retrievedAccount =
        accountDataRepository.findAccountById(UUID.fromString("00000000-0000-0000-0000-000000000000"))
      assertThat(retrievedAccount).isEqualTo(testAccountEntity)
    }

    @Test
    fun `Should return null if no account entity matches the provided ID`() {
      val retrievedAccount =
        accountDataRepository.findAccountById(UUID.fromString("00000000-0000-0000-0000-000000000001"))
      assertThat(retrievedAccount).isNull()
    }
  }

  @Nested
  inner class FindAccountEntityByReference {

    @Test
    fun `Should return a list containing an account entity matching the provided reference`() {
      val listOfAccounts = accountDataRepository.findAccountsByReference("TEST_ACCOUNT_REF")
      assertThat(listOfAccounts.first()).isEqualTo(testAccountEntity)
    }

    @Test
    fun `Should return an empty list if no account entity matches the provided reference`() {
      val listOfAccounts = accountDataRepository.findAccountsByReference("INVALID_ACCOUNT_REF")
      assertThat(listOfAccounts).isEmpty()
    }
  }
}
