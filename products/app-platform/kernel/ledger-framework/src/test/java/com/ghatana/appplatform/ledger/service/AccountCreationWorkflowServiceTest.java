/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Account;
import com.ghatana.appplatform.ledger.domain.AccountType;
import com.ghatana.appplatform.ledger.service.AccountCreationWorkflowService.AccountDraft;
import com.ghatana.appplatform.ledger.service.AccountCreationWorkflowService.DraftStatus;
import com.ghatana.appplatform.ledger.service.AccountCreationWorkflowService.DuplicateAccountCodeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AccountCreationWorkflowService} (STORY-K16-017).
 *
 * <p>Tests cover the full maker-checker lifecycle: PENDING → APPROVED, PENDING → REJECTED,
 * duplicate code rejection (HTTP 409), and AccountCreated event emission.
 */
@DisplayName("AccountCreationWorkflowService — K16-017 maker-checker account creation")
class AccountCreationWorkflowServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private AccountCreationWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new AccountCreationWorkflowService();
    }

    // Helper to create a standard draft
    private AccountDraft submitStandardDraft(String code) {
        return service.createAccount(
                code, "Test Account", AccountType.ASSET,
                "NPR", TENANT_ID, "NPL", null, "maker-user-1");
    }

    // ── AC1: create in PENDING status ─────────────────────────────────────────

    @Test
    @DisplayName("account_create_pending — createAccount returns draft in PENDING status")
    void account_create_pending() {
        AccountDraft draft = submitStandardDraft("CASH-001");

        assertThat(draft.draftId()).isNotNull();
        assertThat(draft.status()).isEqualTo(DraftStatus.PENDING);
        assertThat(draft.code()).isEqualTo("CASH-001");
        assertThat(draft.name()).isEqualTo("Test Account");
        assertThat(draft.type()).isEqualTo(AccountType.ASSET);
        assertThat(draft.currency()).isEqualTo("NPR");
        assertThat(draft.tenantId()).isEqualTo(TENANT_ID);
        assertThat(draft.requesterId()).isEqualTo("maker-user-1");
        assertThat(draft.submittedAt()).isNotNull();
        assertThat(draft.approverId()).isNull();
        assertThat(draft.decidedAt()).isNull();
        assertThat(draft.rejectionReason()).isNull();
    }

    // ── AC2: approve → ACTIVE ─────────────────────────────────────────────────

    @Test
    @DisplayName("account_approve_active — approveAccount returns Account with ACTIVE status")
    void account_approve_active() {
        AccountDraft draft = submitStandardDraft("CASH-002");

        Account account = service.approveAccount(draft.draftId(), "checker-user-1");

        assertThat(account.status()).isEqualTo(Account.AccountStatus.ACTIVE);
        assertThat(account.code()).isEqualTo("CASH-002");
        assertThat(account.name()).isEqualTo("Test Account");
        assertThat(account.type()).isEqualTo(AccountType.ASSET);
        assertThat(account.currency()).isEqualTo("NPR");
        assertThat(account.tenantId()).isEqualTo(TENANT_ID);
        assertThat(account.accountId()).isNotNull();
        assertThat(account.createdAt()).isNotNull();

        // Draft should now be APPROVED
        AccountDraft updatedDraft = service.getDraft(draft.draftId());
        assertThat(updatedDraft.status()).isEqualTo(DraftStatus.APPROVED);
        assertThat(updatedDraft.approverId()).isEqualTo("checker-user-1");
        assertThat(updatedDraft.decidedAt()).isNotNull();
    }

    // ── AC3: reject ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("account_reject — rejectAccount marks draft REJECTED with reason")
    void account_reject() {
        AccountDraft draft = submitStandardDraft("CASH-003");

        AccountDraft rejected = service.rejectAccount(
                draft.draftId(), "checker-user-2", "Invalid account code format");

        assertThat(rejected.status()).isEqualTo(DraftStatus.REJECTED);
        assertThat(rejected.approverId()).isEqualTo("checker-user-2");
        assertThat(rejected.rejectionReason()).isEqualTo("Invalid account code format");
        assertThat(rejected.decidedAt()).isNotNull();
        assertThat(rejected.draftId()).isEqualTo(draft.draftId());
    }

    @Test
    @DisplayName("account_reject_releasesCode — rejected code can be resubmitted")
    void account_reject_releasesCode() {
        AccountDraft draft = submitStandardDraft("REUSABLE-001");
        service.rejectAccount(draft.draftId(), "checker-user-1", "Wrong details");

        // Should succeed — code was released by rejection
        AccountDraft second = submitStandardDraft("REUSABLE-001");
        assertThat(second.status()).isEqualTo(DraftStatus.PENDING);
    }

    // ── AC4: duplicate code → 409 ─────────────────────────────────────────────

    @Test
    @DisplayName("account_duplicateCode_409 — creating same code twice throws DuplicateAccountCodeException")
    void account_duplicateCode_409() {
        submitStandardDraft("DUP-CODE-001");

        assertThatThrownBy(() -> submitStandardDraft("DUP-CODE-001"))
                .isInstanceOf(DuplicateAccountCodeException.class)
                .hasMessageContaining("DUP-CODE-001");
    }

    @Test
    @DisplayName("account_duplicateCode_differentTenant — same code for different tenant is allowed")
    void account_duplicateCode_differentTenant() {
        UUID otherTenant = UUID.randomUUID();

        AccountDraft draft1 = service.createAccount(
                "ASSET-001", "Account A", AccountType.ASSET,
                "NPR", TENANT_ID, null, null, "maker-1");
        AccountDraft draft2 = service.createAccount(
                "ASSET-001", "Account B", AccountType.ASSET,
                "NPR", otherTenant, null, null, "maker-2");

        assertThat(draft1.status()).isEqualTo(DraftStatus.PENDING);
        assertThat(draft2.status()).isEqualTo(DraftStatus.PENDING);
    }

    // ── AC5: AccountCreated event emitted ─────────────────────────────────────

    @Test
    @DisplayName("account_event_emitted — approveAccount fires AccountCreatedListener")
    void account_event_emitted() {
        List<Account> captured = new ArrayList<>();
        service.addAccountCreatedListener(captured::add);

        AccountDraft draft = submitStandardDraft("EV-001");
        Account approved = service.approveAccount(draft.draftId(), "checker-1");

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).isSameAs(approved);
        assertThat(captured.get(0).code()).isEqualTo("EV-001");
    }

    @Test
    @DisplayName("account_event_multipleListeners — all registered listeners fired")
    void account_event_multipleListeners() {
        List<Account> listener1 = new ArrayList<>();
        List<Account> listener2 = new ArrayList<>();
        service.addAccountCreatedListener(listener1::add);
        service.addAccountCreatedListener(listener2::add);

        AccountDraft draft = submitStandardDraft("EV-002");
        service.approveAccount(draft.draftId(), "checker-1");

        assertThat(listener1).hasSize(1);
        assertThat(listener2).hasSize(1);
    }

    // ── Guard: invalid transitions ────────────────────────────────────────────

    @Test
    @DisplayName("account_approve_alreadyApproved_throws — cannot approve twice")
    void account_approve_alreadyApproved_throws() {
        AccountDraft draft = submitStandardDraft("IDM-001");
        service.approveAccount(draft.draftId(), "checker-1");

        assertThatThrownBy(() -> service.approveAccount(draft.draftId(), "checker-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not PENDING");
    }

    @Test
    @DisplayName("account_reject_afterApproval_throws — cannot reject an approved draft")
    void account_reject_afterApproval_throws() {
        AccountDraft draft = submitStandardDraft("IDM-002");
        service.approveAccount(draft.draftId(), "checker-1");

        assertThatThrownBy(() -> service.rejectAccount(draft.draftId(), "checker-2", "Late rejection"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not PENDING");
    }

    @Test
    @DisplayName("account_isPostable_afterApproval — approved account passes isPostable check")
    void account_isPostable_afterApproval() {
        AccountDraft draft = submitStandardDraft("POSTABLE-001");
        Account account = service.approveAccount(draft.draftId(), "checker-1");

        assertThat(account.isPostable()).isTrue();
    }
}
