package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.accounts

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.Account
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.AccountRepository
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.services.AccountService
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

  @Mock
  lateinit var accountRepositoryMock: AccountRepository

  @InjectMocks
  lateinit var accountService: AccountService

  lateinit var dummyAccount: Account

  @BeforeEach
  fun setupDummyAccount() {
    val testAccountReference = "01234567890"
    val testPrisonStaffID = "567"
    dummyAccount = Account(reference = testAccountReference, createdBy = testPrisonStaffID, uuid = UUID.randomUUID(), createdAt = LocalDateTime.now())
  }

  @Nested
  inner class CreateAccount {

    @Test
    fun `Should call the repository to save the account and return it`() {
      `when`(accountRepositoryMock.save(any())).thenAnswer { invocation ->
        val account = invocation.getArgument<Account>(0)
        account.copy(uuid = dummyAccount.uuid, createdAt = dummyAccount.createdAt)
      }

      val createdAccount: Account = accountService.createAccount(reference = "prisoner001", createdBy = "gary.f")

      assertThat(createdAccount.reference).isEqualTo("prisoner001")
      assertThat(createdAccount.createdBy).isEqualTo("gary.f")
      assertThat(createdAccount.uuid).isEqualTo(dummyAccount.uuid)
      assertThat(createdAccount.createdAt).isEqualTo(dummyAccount.createdAt)
    }
  }
}
