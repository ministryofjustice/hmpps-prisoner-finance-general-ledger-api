package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.AccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.helpers.RepoTestHelpers
import java.util.UUID

@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
@Import(RepoTestHelpers::class)
class SubAccountDataRepositoryTest @Autowired constructor(
  val subAccountDataRepository: SubAccountDataRepository,
  val repoTestHelpers: RepoTestHelpers,
) {
  private lateinit var testAccountEntity: AccountEntity
  private lateinit var testSubAccountEntity: SubAccountEntity

  @BeforeEach
  fun setup() {
    repoTestHelpers.clearDb()
    testAccountEntity = repoTestHelpers.createAccount("TEST_ACCOUNT_REF")
    testSubAccountEntity = repoTestHelpers.createSubAccount("TEST_SUB_ACCOUNT_REF", testAccountEntity)
  }

  @Nested
  inner class FindSubAccountByReference {
    @Test
    fun `Should return a list containing subaccount entity matching the provided account reference and subaccount reference`() {
      val retrievedSubAccountEntity =
        subAccountDataRepository.findByParentAccountEntityReferenceAndReference(
          "TEST_ACCOUNT_REF",
          "TEST_SUB_ACCOUNT_REF",
        )
      assertThat(retrievedSubAccountEntity?.parentAccountEntity?.reference).isEqualTo("TEST_ACCOUNT_REF")
    }

    @Test
    fun `Should return a null if no subaccount entity matches the provided account reference and subaccount reference`() {
      val retrievedSubAccountEntity = subAccountDataRepository.findByParentAccountEntityReferenceAndReference(
        "INVALID_ACCOUNT_REF",
        "INVALID_SUB_ACCOUNT_REF",
      )
      assertThat(retrievedSubAccountEntity).isNull()
    }

    @Test
    fun `Should return a null if no account entity matches the provided account reference even if a subaccount with the correct sub account reference exists elsewhere`() {
      val retrievedSubAccountEntity = subAccountDataRepository.findByParentAccountEntityReferenceAndReference(
        "INVALID_ACCOUNT_REF",
        "TEST_SUB_ACCOUNT_REF",
      )
      assertThat(retrievedSubAccountEntity).isNull()
    }
  }

  @Nested
  inner class GetSubAccountEntityById {
    @Test
    fun `Should return a subaccount entity matching the provided ID`() {
      val retrievedSubAccountEntity = subAccountDataRepository.getSubAccountEntityById(testSubAccountEntity.id)
      assertThat(retrievedSubAccountEntity?.parentAccountEntity?.reference).isEqualTo("TEST_ACCOUNT_REF")
    }

    @Test
    fun `Should return a null if no subaccount entity matches the provided ID`() {
      val retrievedSubAccountEntity = subAccountDataRepository.getSubAccountEntityById(UUID.fromString("00000000-0000-0000-0000-000000000002"))
      assertThat(retrievedSubAccountEntity).isNull()
    }
  }
}
