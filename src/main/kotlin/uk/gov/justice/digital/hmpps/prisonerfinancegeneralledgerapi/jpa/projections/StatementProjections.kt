package uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.projections

import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.AccountType
import uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.entities.enums.PostingType
import java.time.Instant
import java.util.UUID

/**
 * Flat DTO projections used by [PostingsDataRepository][uk.gov.justice.digital.hmpps.prisonerfinancegeneralledgerapi.jpa.repositories.PostingsDataRepository].
 *
 * With `spring.jpa.open-in-view=false` the persistence context is closed before the
 * controller serialises its response, so mapping from managed entities outside a
 * transaction throws LazyInitializationException. Selecting straight into these plain
 * DTOs via JPQL constructor expressions (`SELECT new ...`) side-steps that entirely:
 * the query fetches exactly the columns we need, and the result carries no lazy
 * associations. This is the pattern to replicate for other read paths.
 */

/** The sub-account (with its parent account) columns shared by the projections below. */
interface SubAccountWithParentProjection {
  val subAccountId: UUID
  val subAccountReference: String
  val subAccountCreatedBy: String
  val subAccountCreatedAt: Instant
  val parentAccountId: UUID
  val parentAccountReference: String
  val parentAccountCreatedBy: String
  val parentAccountCreatedAt: Instant
  val parentAccountType: AccountType
}

/** One row of a paged account statement (everything except the opposite postings). */
data class StatementEntryProjection(
  val transactionId: UUID,
  val postingCreatedAt: Instant,
  val transactionTimestamp: Instant,
  val description: String,
  val amount: Long,
  val postingType: PostingType,
  override val subAccountId: UUID,
  override val subAccountReference: String,
  override val subAccountCreatedBy: String,
  override val subAccountCreatedAt: Instant,
  override val parentAccountId: UUID,
  override val parentAccountReference: String,
  override val parentAccountCreatedBy: String,
  override val parentAccountCreatedAt: Instant,
  override val parentAccountType: AccountType,
  // Nullable: a posting has no posting_balance row until balances are calculated (LEFT JOIN).
  val subAccountBalance: Long?,
  val accountBalance: Long?,
) : SubAccountWithParentProjection

/** A posting belonging to one of the statement's transactions, used to build the "other side". */
data class OppositePostingProjection(
  val transactionId: UUID,
  val postingId: UUID,
  val createdBy: String,
  val createdAt: Instant,
  val type: PostingType,
  val amount: Long,
  override val subAccountId: UUID,
  override val subAccountReference: String,
  override val subAccountCreatedBy: String,
  override val subAccountCreatedAt: Instant,
  override val parentAccountId: UUID,
  override val parentAccountReference: String,
  override val parentAccountCreatedBy: String,
  override val parentAccountCreatedAt: Instant,
  override val parentAccountType: AccountType,
) : SubAccountWithParentProjection
