/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.service;

import com.ghatana.appplatform.ledger.domain.Account;
import com.ghatana.appplatform.ledger.domain.Account.AccountStatus;
import com.ghatana.appplatform.ledger.domain.AccountType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maker-checker workflow for account creation in the chart of accounts (STORY-K16-017).
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Maker calls {@link #createAccount} → {@link AccountDraft} in {@code PENDING} state.</li>
 *   <li>Checker calls {@link #approveAccount} → {@link Account} activated ({@code ACTIVE}),
 *       {@link AccountCreatedListener} callbacks fired.</li>
 *   <li>Checker calls {@link #rejectAccount} → draft moves to {@code REJECTED}; the account code
 *       is released so a corrected submission is possible.</li>
 * </ol>
 *
 * <h2>Code uniqueness</h2>
 * <p>Account codes are unique within the {@code (tenantId, code)} namespace. Attempting to
 * create a second account with an existing code while a {@code PENDING} draft is already in
 * flight throws {@link DuplicateAccountCodeException}.  The code is released if the draft is
 * rejected, or permanently reserved once approved.
 *
 * <p>This service manages purely in-memory workflow state.  Persistence of approved
 * {@link Account} objects is delegated to the {@link com.ghatana.appplatform.ledger.port.AccountStore}
 * port (wired at the application layer, not here).
 *
 * @doc.type class
 * @doc.purpose Maker-checker workflow for ledger account creation with code uniqueness (K16-017)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AccountCreationWorkflowService {

    // ── In-memory state ───────────────────────────────────────────────────────

    /** Maps codeKey(tenantId, code) → draftId while a PENDING draft exists. */
    private final ConcurrentHashMap<String, UUID> codeRegistry = new ConcurrentHashMap<>();

    /** All drafts (PENDING, APPROVED, REJECTED). */
    private final ConcurrentHashMap<UUID, AccountDraft> drafts = new ConcurrentHashMap<>();

    /** Listeners notified synchronously when an account is approved. */
    private final List<AccountCreatedListener> listeners = new CopyOnWriteArrayList<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Maker step: submit a new account creation request.
     *
     * <p>The draft is immediately placed in {@link DraftStatus#PENDING} and requires a
     * checker to call {@link #approveAccount} or {@link #rejectAccount}.
     *
     * @param code         unique account code within the tenant namespace; must not be blank
     * @param name         human-readable account name; must not be blank
     * @param type         account classification (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE)
     * @param currency     ISO 4217 currency code; must not be blank
     * @param tenantId     tenant identifier, or {@code null} for platform-level accounts
     * @param jurisdiction jurisdiction tag (e.g. {@code "NPL"}), or {@code null}
     * @param parentId     parent account, or {@code null} for root accounts
     * @param requesterId  identifier of the maker; must not be blank
     * @return newly created {@link AccountDraft} in {@link DraftStatus#PENDING}
     * @throws DuplicateAccountCodeException if the code is already in use for this tenant
     * @throws IllegalArgumentException      if required parameters are null or blank
     */
    public AccountDraft createAccount(String code, String name, AccountType type,
                                      String currency, UUID tenantId, String jurisdiction,
                                      UUID parentId, String requesterId) {
        requireNonBlank(code, "code");
        requireNonBlank(name, "name");
        Objects.requireNonNull(type, "type");
        requireNonBlank(currency, "currency");
        requireNonBlank(requesterId, "requesterId");

        UUID draftId = UUID.randomUUID();
        String key = codeKey(tenantId, code);
        UUID existing = codeRegistry.putIfAbsent(key, draftId);
        if (existing != null) {
            throw new DuplicateAccountCodeException(
                    "Account code '" + code + "' already exists for tenant " + tenantId);
        }

        AccountDraft draft = new AccountDraft(draftId, code, name, type, currency,
                tenantId, jurisdiction, parentId, requesterId, Instant.now(),
                DraftStatus.PENDING, null, null, null);
        drafts.put(draftId, draft);
        return draft;
    }

    /**
     * Checker step: approve a pending account creation draft.
     *
     * <p>Transitions the draft to {@link DraftStatus#APPROVED}, constructs an
     * {@link Account} with status {@link AccountStatus#ACTIVE}, and fires all registered
     * {@link AccountCreatedListener} callbacks synchronously.
     *
     * @param draftId   identifier of the pending draft
     * @param approverId identifier of the checker/approver
     * @return the newly activated {@link Account}
     * @throws IllegalArgumentException if the draft does not exist
     * @throws IllegalStateException    if the draft is not in PENDING state
     */
    public Account approveAccount(UUID draftId, String approverId) {
        Objects.requireNonNull(draftId, "draftId");
        requireNonBlank(approverId, "approverId");

        AccountDraft draft = requirePendingDraft(draftId);

        Instant now = Instant.now();
        AccountDraft approved = new AccountDraft(
                draft.draftId(), draft.code(), draft.name(), draft.type(),
                draft.currency(), draft.tenantId(), draft.jurisdiction(), draft.parentId(),
                draft.requesterId(), draft.submittedAt(),
                DraftStatus.APPROVED, approverId, now, null);
        drafts.put(draftId, approved);
        // Code stays reserved permanently for approved accounts

        Account account = new Account(
                UUID.randomUUID(), draft.code(), draft.name(), draft.type(),
                draft.parentId(), draft.currency(), AccountStatus.ACTIVE,
                draft.jurisdiction(), draft.tenantId(), now);

        for (AccountCreatedListener listener : listeners) {
            listener.onAccountCreated(account);
        }
        return account;
    }

    /**
     * Checker step: reject a pending account creation draft.
     *
     * <p>Transitions the draft to {@link DraftStatus#REJECTED} and frees the account code
     * so a corrected submission can be made.
     *
     * @param draftId    identifier of the pending draft
     * @param approverId identifier of the checker/approver
     * @param reason     human-readable rejection reason; must not be blank
     * @return the updated {@link AccountDraft} in {@link DraftStatus#REJECTED} state
     * @throws IllegalArgumentException if the draft does not exist or reason is blank
     * @throws IllegalStateException    if the draft is not in PENDING state
     */
    public AccountDraft rejectAccount(UUID draftId, String approverId, String reason) {
        Objects.requireNonNull(draftId, "draftId");
        requireNonBlank(approverId, "approverId");
        requireNonBlank(reason, "reason");

        AccountDraft draft = requirePendingDraft(draftId);

        // Release the code reservation so a corrected draft can be submitted
        String key = codeKey(draft.tenantId(), draft.code());
        codeRegistry.remove(key);

        AccountDraft rejected = new AccountDraft(
                draft.draftId(), draft.code(), draft.name(), draft.type(),
                draft.currency(), draft.tenantId(), draft.jurisdiction(), draft.parentId(),
                draft.requesterId(), draft.submittedAt(),
                DraftStatus.REJECTED, approverId, Instant.now(), reason);
        drafts.put(draftId, rejected);
        return rejected;
    }

    /**
     * Returns the draft with the given ID.
     *
     * @param draftId draft identifier
     * @return the draft
     * @throws IllegalArgumentException if not found
     */
    public AccountDraft getDraft(UUID draftId) {
        Objects.requireNonNull(draftId, "draftId");
        AccountDraft d = drafts.get(draftId);
        if (d == null) {
            throw new IllegalArgumentException("Draft not found: " + draftId);
        }
        return d;
    }

    /**
     * Registers a listener that is invoked when an account is successfully approved.
     *
     * @param listener callback to register; not null
     */
    public void addAccountCreatedListener(AccountCreatedListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * Workflow status of an account creation draft.
     */
    public enum DraftStatus {
        /** Submitted by maker; awaiting checker decision. */
        PENDING,
        /** Approved by checker; Account is now ACTIVE. */
        APPROVED,
        /** Rejected by checker; code reservation released. */
        REJECTED
    }

    /**
     * Immutable snapshot of an account creation draft at a point in the workflow.
     *
     * @param draftId         unique identifier for this workflow instance
     * @param code            requested account code
     * @param name            requested account name
     * @param type            requested account type
     * @param currency        requested ISO 4217 currency code
     * @param tenantId        tenant scope (null = platform)
     * @param jurisdiction    jurisdiction tag (nullable)
     * @param parentId        parent account (null = root)
     * @param requesterId     identifier of the maker who submitted the draft
     * @param submittedAt     timestamp when the draft was submitted
     * @param status          current workflow status
     * @param approverId      identifier of the checker who made the decision (null if PENDING)
     * @param decidedAt       timestamp of the checker decision (null if PENDING)
     * @param rejectionReason explanation for rejection (null unless REJECTED)
     */
    public record AccountDraft(
            UUID draftId,
            String code,
            String name,
            AccountType type,
            String currency,
            UUID tenantId,
            String jurisdiction,
            UUID parentId,
            String requesterId,
            Instant submittedAt,
            DraftStatus status,
            String approverId,
            Instant decidedAt,
            String rejectionReason
    ) {}

    /**
     * Thrown when a duplicate account code is detected within the same tenant namespace.
     *
     * <p>Maps to HTTP 409 Conflict at the API layer.
     */
    public static final class DuplicateAccountCodeException extends IllegalStateException {
        public DuplicateAccountCodeException(String message) {
            super(message);
        }
    }

    /**
     * Listener invoked when an account creation is approved.
     *
     * @doc.gaa.lifecycle act
     */
    @FunctionalInterface
    public interface AccountCreatedListener {
        /**
         * Called after an account draft is approved and the {@link Account} is activated.
         *
         * @param account the newly activated account
         */
        void onAccountCreated(Account account);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AccountDraft requirePendingDraft(UUID draftId) {
        AccountDraft draft = drafts.get(draftId);
        if (draft == null) {
            throw new IllegalArgumentException("Draft not found: " + draftId);
        }
        if (draft.status() != DraftStatus.PENDING) {
            throw new IllegalStateException(
                    "Draft " + draftId + " is not PENDING (current: " + draft.status() + ")");
        }
        return draft;
    }

    private static String codeKey(UUID tenantId, String code) {
        return (tenantId != null ? tenantId.toString() : "PLATFORM") + "|" + code;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
