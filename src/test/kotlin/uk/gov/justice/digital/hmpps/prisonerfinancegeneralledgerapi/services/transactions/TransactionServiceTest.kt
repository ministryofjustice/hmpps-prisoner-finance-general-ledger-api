package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.transactions

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.description
import org.mockito.kotlin.whenever
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

  lateinit var transaction: TransactionEntity

  val transactionUUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
  val date = LocalDateTime.of(2025, 1, 1, 0, 0, 0)
  val timeStamp = LocalDateTime.of(2025, 12, 24, 0, 0, 0)
  val transactionDescription = "TX"
  val transactionAmount = BigInteger.ONE
  val postings: List<PostingRequest> = listOf(PostingRequest(subAccountId = UUID.fromString("00000000-0000-0000-0000-000000000001"), type = PostingType.CR, amount = BigInteger.ONE), PostingRequest(subAccountId = UUID.fromString("00000000-0000-0000-0000-000000000002"), type = PostingType.DR, amount = BigInteger.ONE))

  @BeforeEach
  fun setupTransaction() {
    transaction = TransactionEntity(
      id = transactionUUID,
      createdBy = TEST_USERNAME,
      createdAt = date,
      reference = TEST_TREF,
      description = transactionDescription,
      timestamp = timeStamp,
      amount = transactionAmount,
    )
  }

  @Nested
  inner class CreateTransaction {
    @Test
    fun `Save transaction and create postings with the created transaction ID and return it`() {
      whenever(transactionDataRepository.save(any())).thenReturn(transaction)
      val createdTransaction: TransactionEntity =
        transactionService.createTransaction(TEST_TREF, TEST_USERNAME, description = transactionDescription, amount = transactionAmount)
    }
  }
}
