/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.finance.ledger.service;

import com.ghatana.finance.ledger.domain.Account;
import com.ghatana.finance.ledger.domain.Account.AccountStatus;
import com.ghatana.finance.ledger.domain.AccountType;
import com.ghatana.finance.ledger.service.AccountCreationWorkflowService.AccountDraft;
import com.ghatana.finance.ledger.service.AccountCreationWorkflowService.DraftStatus;
import com.ghatana.finance.ledger.service.AccountCreationWorkflowService.DuplicateAccountCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AccountCreationWorkflowService} covering:
 * <ul>
 *   <li>Maker-checker happy path (create → approve / reject)</li>
 *   <li>Code uniqueness enforcement</li>
 *   <li>Draft state transitions</li>
 *   <li>Listener notification on approval</li>
 *   <li>Input validation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tests for maker-checker account creation workflow (K16-017)
 * @doc.layer finance
 * @doc.pattern Test
 */
@DisplayName("AccountCreationWorkflowService")
class AccountCreationWorkflowServiceTest {

    private AccountCreationWorkflowService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String CURRENCY = "NPR";
    private static final String MAKER = "maker-001";
    private static final String CHECKER = "checker-001";

    @BeforeEach
    void setUp() {
        service = new AccountCreationWorkflowService();
    }

    // ── Create draft ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Create account draft")
    class CreateDraft {

        @Test
        @DisplayName("Should create a PENDING draft")
        void shouldCreatePendingDraft() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash Account", AccountType.ASSET, CURRENCY,
                    TENANT, "NPL", null, MAKER);

            assertNotNull(draft.draftId());
            assertEquals("1001", draft.code());
            assertEquals("Cash Account", draft.name());
            assertEquals(AccountType.ASSET, draft.type());
            assertEquals(CURRENCY, draft.currency());
            assertEquals(TENANT, draft.tenantId());
            assertEquals("NPL", draft.jurisdiction());
            assertNull(draft.parentId());
            assertEquals(MAKER, draft.requesterId());
            assertEquals(DraftStatus.PENDING, draft.status());
            assertNotNull(draft.submittedAt());
            assertNull(draft.approverId());
            assertNull(draft.decidedAt());
            assertNull(draft.rejectionReason());
        }

        @Test
        @DisplayName("Should allow null tenantId for platform-level accounts")
        void shouldAllowNullTenantIdForPlatformAccounts() {
            AccountDraft draft = service.createAccount(
                    "PLAT-001", "Platform Account", AccountType.EQUITY,
                    CURRENCY, null, null, null, MAKER);

            assertNull(draft.tenantId());
            assertEquals(DraftStatus.PENDING, draft.status());
        }

        @Test
        @DisplayName("Should allow null jurisdiction and parentId")
        void shouldAllowNullJurisdictionAndParentId() {
            AccountDraft draft = service.createAccount(
                    "2001", "Revenue", AccountType.REVENUE, CURRENCY,
                    TENANT, null, null, MAKER);

            assertNull(draft.jurisdiction());
            assertNull(draft.parentId());
        }
    }

    // ── Approve ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Approve account")
    class Approve {

        @Test
        @DisplayName("Should approve a PENDING draft and return an ACTIVE account")
        void shouldApproveAndReturnActiveAccount() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, "NPL", null, MAKER);

            Account account = service.approveAccount(draft.draftId(), CHECKER);

            assertNotNull(account.accountId());
            assertEquals("1001", account.code());
            assertEquals("Cash", account.name());
            assertEquals(AccountType.ASSET, account.type());
            assertEquals(CURRENCY, account.currency());
            assertEquals(AccountStatus.ACTIVE, account.status());
            assertEquals(TENANT, account.tenantId());
            assertEquals("NPL", account.jurisdiction());
        }

        @Test
        @DisplayName("Should update draft to APPROVED after approval")
        void shouldUpdateDraftToApproved() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);

            service.approveAccount(draft.draftId(), CHECKER);

            AccountDraft updated = service.getDraft(draft.draftId());
            assertEquals(DraftStatus.APPROVED, updated.status());
            assertEquals(CHECKER, updated.approverId());
            assertNotNull(updated.decidedAt());
        }

        @Test
        @DisplayName("Should invoke listeners on approval")
        void shouldInvokeListenersOnApproval() {
            List<Account> notifications = new ArrayList<>();
            service.addAccountCreatedListener(notifications::add);

            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);
            Account account = service.approveAccount(draft.draftId(), CHECKER);

            assertEquals(1, notifications.size());
            assertEquals(account.code(), notifications.get(0).code());
        }

        @Test
        @DisplayName("Should reject approving a non-PENDING draft")
        void shouldRejectApprovingNonPendingDraft() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);
            service.approveAccount(draft.draftId(), CHECKER);

            assertThrows(IllegalStateException.class,
                    () -> service.approveAccount(draft.draftId(), CHECKER));
        }

        @Test
        @DisplayName("Should throw for unknown draftId")
        void shouldThrowForUnknownDraftId() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.approveAccount(UUID.randomUUID(), CHECKER));
        }
    }

    // ── Reject ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Reject account")
    class Reject {

        @Test
        @DisplayName("Should reject a PENDING draft")
        void shouldRejectPendingDraft() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);

            AccountDraft rejected = service.rejectAccount(
                    draft.draftId(), CHECKER, "Duplicate entry");

            assertEquals(DraftStatus.REJECTED, rejected.status());
            assertEquals(CHECKER, rejected.approverId());
            assertEquals("Duplicate entry", rejected.rejectionReason());
            assertNotNull(rejected.decidedAt());
        }

        @Test
        @DisplayName("Should release account code after rejection to allow re-creation")
        void shouldReleaseCodeAfterRejection() {
            AccountDraft first = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);
            service.rejectAccount(first.draftId(), CHECKER, "Wrong type");

            // Re-creating with the same code should succeed
            AccountDraft second = service.createAccount(
                    "1001", "Cash Fixed", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);

            assertEquals(DraftStatus.PENDING, second.status());
            assertNotEquals(first.draftId(), second.draftId());
        }

        @Test
        @DisplayName("Should reject rejecting a non-PENDING draft")
        void shouldRejectRejectingNonPendingDraft() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);
            service.rejectAccount(draft.draftId(), CHECKER, "Bad");

            assertThrows(IllegalStateException.class,
                    () -> service.rejectAccount(draft.draftId(), CHECKER, "Again"));
        }

        @Test
        @DisplayName("Should throw for unknown draftId")
        void shouldThrowForUnknownDraftId() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.rejectAccount(UUID.randomUUID(), CHECKER, "Not found"));
        }
    }

    // ── Code uniqueness ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Code uniqueness")
    class CodeUniqueness {

        @Test
        @DisplayName("Should throw DuplicateAccountCodeException for duplicate code in same tenant")
        void shouldThrowForDuplicateCodeInSameTenant() {
            service.createAccount("1001", "Cash", AccountType.ASSET,
                    CURRENCY, TENANT, null, null, MAKER);

            assertThrows(DuplicateAccountCodeException.class, () ->
                    service.createAccount("1001", "Cash Alt", AccountType.ASSET,
                            CURRENCY, TENANT, null, null, MAKER));
        }

        @Test
        @DisplayName("Should allow same code in different tenants")
        void shouldAllowSameCodeInDifferentTenants() {
            UUID tenant2 = UUID.randomUUID();
            service.createAccount("1001", "Cash", AccountType.ASSET,
                    CURRENCY, TENANT, null, null, MAKER);

            assertDoesNotThrow(() ->
                    service.createAccount("1001", "Cash", AccountType.ASSET,
                            CURRENCY, tenant2, null, null, MAKER));
        }

        @Test
        @DisplayName("Should permanently reserve code after approval")
        void shouldPermanentlyReserveCodeAfterApproval() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);
            service.approveAccount(draft.draftId(), CHECKER);

            assertThrows(DuplicateAccountCodeException.class, () ->
                    service.createAccount("1001", "New Cash", AccountType.ASSET,
                            CURRENCY, TENANT, null, null, MAKER));
        }
    }

    // ── getDraft ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Get draft")
    class GetDraft {

        @Test
        @DisplayName("Should return existing draft")
        void shouldReturnExistingDraft() {
            AccountDraft created = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);

            AccountDraft retrieved = service.getDraft(created.draftId());

            assertEquals(created.draftId(), retrieved.draftId());
            assertEquals(created.code(), retrieved.code());
        }

        @Test
        @DisplayName("Should throw for non-existent draft")
        void shouldThrowForNonExistentDraft() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.getDraft(UUID.randomUUID()));
        }
    }

    // ── Input validation ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("Should reject null code")
        void shouldRejectNullCode() {
            assertThrows(IllegalArgumentException.class, () ->
                    service.createAccount(null, "Cash", AccountType.ASSET,
                            CURRENCY, TENANT, null, null, MAKER));
        }

        @Test
        @DisplayName("Should reject blank code")
        void shouldRejectBlankCode() {
            assertThrows(IllegalArgumentException.class, () ->
                    service.createAccount("  ", "Cash", AccountType.ASSET,
                            CURRENCY, TENANT, null, null, MAKER));
        }

        @Test
        @DisplayName("Should reject null name")
        void shouldRejectNullName() {
            assertThrows(IllegalArgumentException.class, () ->
                    service.createAccount("1001", null, AccountType.ASSET,
                            CURRENCY, TENANT, null, null, MAKER));
        }

        @Test
        @DisplayName("Should reject null type")
        void shouldRejectNullType() {
            assertThrows(NullPointerException.class, () ->
                    service.createAccount("1001", "Cash", null,
                            CURRENCY, TENANT, null, null, MAKER));
        }

        @Test
        @DisplayName("Should reject blank currency")
        void shouldRejectBlankCurrency() {
            assertThrows(IllegalArgumentException.class, () ->
                    service.createAccount("1001", "Cash", AccountType.ASSET,
                            "", TENANT, null, null, MAKER));
        }

        @Test
        @DisplayName("Should reject blank requesterId")
        void shouldRejectBlankRequesterId() {
            assertThrows(IllegalArgumentException.class, () ->
                    service.createAccount("1001", "Cash", AccountType.ASSET,
                            CURRENCY, TENANT, null, null, ""));
        }

        @Test
        @DisplayName("Should reject null draftId in approve")
        void shouldRejectNullDraftIdInApprove() {
            assertThrows(NullPointerException.class,
                    () -> service.approveAccount(null, CHECKER));
        }

        @Test
        @DisplayName("Should reject blank approverId in approve")
        void shouldRejectBlankApproverIdInApprove() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);

            assertThrows(IllegalArgumentException.class,
                    () -> service.approveAccount(draft.draftId(), "  "));
        }

        @Test
        @DisplayName("Should reject blank reason in reject")
        void shouldRejectBlankReasonInReject() {
            AccountDraft draft = service.createAccount(
                    "1001", "Cash", AccountType.ASSET, CURRENCY,
                    TENANT, null, null, MAKER);

            assertThrows(IllegalArgumentException.class,
                    () -> service.rejectAccount(draft.draftId(), CHECKER, ""));
        }
    }
}
