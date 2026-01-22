package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.validators

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.models.requests.CreateTransactionRequest
import java.time.LocalDateTime
import java.util.UUID

class TransactionValidatorTest {
  private val validator = TransactionValidator()

  @Test
  fun `should fail when transaction is null`() {
    val request = null
    val ok = validator.isValid(request, null)
    assertThat(ok).isFalse()
  }

  @Test
  fun `should fail when sum of posting credits differs from transaction amount`() {
    val createPostingRequests: List<CreatePostingRequest> = listOf(
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.CR, amount = 1L),
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.CR, amount = 2L),
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.DR, amount = 3L),
    )

    val request = CreateTransactionRequest(reference = "TX", description = "DESCRIPTION", amount = 100L, timestamp = LocalDateTime.now(), postings = createPostingRequests)

    val ok = validator.isValid(request, null)
    assertThat(ok).isFalse()
  }

  @Test
  fun `should fail when posting credits and debits do not balance`() {
    val createPostingRequests: List<CreatePostingRequest> = listOf(
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.CR, amount = 1L),
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.CR, amount = 2L),
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.DR, amount = 100L),
    )

    val request = CreateTransactionRequest(reference = "TX", description = "DESCRIPTION", amount = 3L, timestamp = LocalDateTime.now(), postings = createPostingRequests)

    val ok = validator.isValid(request, null)
    assertThat(ok).isFalse()
  }

  @Test
  fun `should pass when given a valid transaction`() {
    val createPostingRequests: List<CreatePostingRequest> = listOf(
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.CR, amount = 1L),
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.CR, amount = 2L),
      CreatePostingRequest(subAccountId = UUID.randomUUID(), type = PostingType.DR, amount = 3L),
    )

    val request = CreateTransactionRequest(reference = "TX", description = "DESCRIPTION", amount = 3L, timestamp = LocalDateTime.now(), postings = createPostingRequests)

    val ok = validator.isValid(request, null)
    assertThat(ok).isTrue()
  }
}
