package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.transactions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.PostingEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.SubAccountEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.TransactionEntity
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.TransactionDataRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.PostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.TransactionService
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.UUID

private const val TEST_TREF = "TEST_ACCOUNT_REF"
private const val TEST_USERNAME = "TEST_USERNAME"

@ExtendWith(MockitoExtension::class)
class TransactionServiceTest {

  @Mock
  lateinit var transactionDataRepository: TransactionDataRepository

  @Mock
  lateinit var postingsDataRepository: PostingsDataRepository

  @InjectMocks
  lateinit var transactionService: TransactionService

  lateinit var transactionEntity: TransactionEntity
  lateinit var postingEntities: List<PostingEntity>

  val transactionUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
  val date = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
  val timeStamp = LocalDateTime.of(2025, 12, 24, 0, 0, 0)
  val transactionDescription = "TX"
  val transactionAmount = BigInteger.ONE
  val postingRequests: List<PostingRequest> = listOf(PostingRequest(subAccountId = UUID.fromString("00000000-0000-0000-0000-000000000001"), type = PostingType.CR, amount = BigInteger.ONE), PostingRequest(subAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002"), type = PostingType.DR, amount = BigInteger.ONE))

  @BeforeEach
  fun setupTransaction() {
    transactionEntity = TransactionEntity(
      id = transactionUUID,
      createdBy = TEST_USERNAME,
      createdAt = date,
      reference = TEST_TREF,
      description = transactionDescription,
      timestamp = timeStamp,
      amount = transactionAmount,
    )
    postingEntities = postingRequests.map { PostingEntity(createdBy = TEST_USERNAME, createdAt = date, type = it.type, amount = it.amount, subAccountEntity = SubAccountEntity(), transactionEntity = transactionEntity) }.toMutableList()
  }

  @Nested
  inner class CreateTransaction {
    @Test
    fun `Save transaction and create postings with the created transaction ID and return it`() {
      whenever(transactionDataRepository.save(any())).thenReturn(transactionEntity)
      whenever(postingsDataRepository.saveAll(postingEntities)).thenReturn(postingEntities)
      val createdTransaction: TransactionEntity =
        transactionService.createTransaction(TEST_TREF, TEST_USERNAME, description = transactionDescription, amount = transactionAmount, timestamp = timeStamp, postings = postingRequests)
      val transactionCaptor = argumentCaptor<TransactionEntity>()
      verify(transactionDataRepository, times(1)).save(transactionCaptor.capture())
      val transactionToSave = transactionCaptor.firstValue
      assertThat(transactionToSave.reference).isEqualTo(TEST_TREF)
      assertThat(transactionToSave.createdBy).isEqualTo(TEST_USERNAME)
      assertThat(transactionToSave.description).isEqualTo(transactionDescription)
      assertThat(transactionToSave.amount).isEqualTo(transactionAmount)
      assertThat(transactionToSave.timestamp).isEqualTo(timeStamp)

      val postingsCaptor = argumentCaptor<List<PostingEntity>>()
      verify(postingsDataRepository, times(1)).saveAll(postingsCaptor.capture())
    }
  }
}
