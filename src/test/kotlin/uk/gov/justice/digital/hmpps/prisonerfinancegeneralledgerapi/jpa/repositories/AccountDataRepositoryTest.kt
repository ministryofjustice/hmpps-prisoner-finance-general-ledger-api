package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.util.UUID

// @SpringBootTest
@DataJpaTest
@Import(RepoTestHelpers::class)
class AccountDataRepositoryTest @Autowired constructor(
  val accountDataRepository: AccountDataRepository,
  private val repoTestHelpers: RepoTestHelpers,
) {

  private lateinit var testAccountEntity: AccountEntity

  @BeforeEach
  fun setup() {
    testAccountEntity = repoTestHelpers.createAccount("TEST_ACCOUNT_REF")
  }

  @Nested
  inner class FindAccountById {

    @Test
    fun `Should return an account entity matching the provided ID`() {
      val retrievedAccount =
        accountDataRepository.findAccountById(testAccountEntity.id)
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
      val retrievedEntity = accountDataRepository.findAccountByReference("TEST_ACCOUNT_REF")
      assertThat(retrievedEntity).isEqualTo(testAccountEntity)
    }

    @Test
    fun `Should return a null if no account entity matches the provided reference`() {
      val retrievedEntity = accountDataRepository.findAccountByReference("INVALID_ACCOUNT_REF")
      assertThat(retrievedEntity).isNull()
    }
  }
}
