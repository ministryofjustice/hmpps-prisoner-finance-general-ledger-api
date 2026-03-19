package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.postings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.PostingService
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PostingServiceTest {

  @Mock
  lateinit var postingsDataRepository: PostingsDataRepository

  @InjectMocks
  lateinit var postingService: PostingService

  private val serviceTestHelpers = ServiceTestHelpers()

  @Nested
  inner class GetPostings {

    @Test
    fun `should return empty list when no postings for prisoner`() {
      val prisonerId = UUID.randomUUID()
      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId) }.thenReturn(emptyList())

      val postings = postingService.listPostingsForPrisoner(prisonerId)

      assertThat(postings).isEmpty()
    }

    @Test
    fun `should return list of postings for prisoner`() {
      val prisonerId = UUID.randomUUID()

      val accountEntity = serviceTestHelpers.createAccount("ABC123XX", AccountType.PRISONER)
      val subAccountCashEntity = serviceTestHelpers.createSubAccount("CASH", accountEntity)
      val subAccountSpendsEntity = serviceTestHelpers.createSubAccount("SPENDS", accountEntity)

      val transactionEntity = serviceTestHelpers.createOneToOneTransaction(1L, Instant.now(), subAccountCashEntity, subAccountSpendsEntity)

      val posting1 = serviceTestHelpers.createPostingEntity(subAccountCashEntity, transactionEntity)
      val posting2 = serviceTestHelpers.createPostingEntity(subAccountSpendsEntity, transactionEntity)

      whenever { postingsDataRepository.getPostingsByAccountId(prisonerId) }
        .thenReturn(
          listOf(
            posting1,
            posting2,
          ),
        )

      val postings = postingService.listPostingsForPrisoner(prisonerId)

      assertThat(postings).hasSize(2)
    }

    @Test
    fun `should return list of postings for a transaction`() {

      val prisonerId = UUID.randomUUID()

      val prisonerPostingResponseList = postingService.transformTransactionIntoPostingsForPrisoner(prisonerId)

      assertThat(prisonerPostingResponseList).hasSize(1)
    }
  }
}
