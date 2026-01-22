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
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@TestPropertySource(
  properties = [
    "spring.flyway.locations=classpath:/db/migrations/common,classpath:/db/migrations/h2",
  ],
)
class SubAccountDataRepositoryTest @Autowired constructor(
  val entityManager: TestEntityManager,
  val subAccountDataRepository: SubAccountDataRepository,
) {
  private lateinit var testAccountEntity: AccountEntity
  private lateinit var testSubAccountEntity: SubAccountEntity

  @BeforeEach
  fun setup() {
    testAccountEntity = AccountEntity(
      reference = "TEST_ACCOUNT_REF",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000000"),
    )
    entityManager.persist(testAccountEntity)
    testSubAccountEntity = SubAccountEntity(
      reference = "TEST_SUB_ACCOUNT_REF",
      createdBy = "TEST_USERNAME",
      id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
      parentAccountEntity = testAccountEntity,
      createdAt = LocalDateTime.of(2025, 12, 25, 0, 0, 0),
    )
    entityManager.persist(testSubAccountEntity)
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
  inner class FindSubAccountById {
    @Test
    fun `Should return a subaccount entity matching the provided ID`() {
      val retrievedSubAccountEntity = subAccountDataRepository.findById(testSubAccountEntity.id)
      assertThat(retrievedSubAccountEntity.get().parentAccountEntity.reference).isEqualTo("TEST_ACCOUNT_REF")
    }

    @Test
    fun `Should return a null if no subaccount entity matches the provided ID`() {
      val retrievedSubAccountEntity = subAccountDataRepository.findById(UUID.fromString("00000000-0000-0000-0000-000000000002"))
      assertThat(retrievedSubAccountEntity.isPresent).isFalse()
    }
  }
}
