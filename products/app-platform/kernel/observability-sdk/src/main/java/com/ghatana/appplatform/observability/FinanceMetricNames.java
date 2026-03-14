package com.ghatana.appplatform.observability;

/**
 * Canonical metric name constants for the Finance/App-Platform kernel.
 *
 * <p>Centralises all metric names used across IAM, ledger, secrets, and calendar modules
 * to avoid typos, naming drift, and duplicate metric definitions.
 *
 * @doc.type class
 * @doc.purpose Single source of truth for finance kernel metric name strings
 * @doc.layer product
 * @doc.pattern ValueObject (constants carrier)
 */
public final class FinanceMetricNames {

    // --- Ledger ---
    public static final String LEDGER_JOURNALS_POSTED   = "finance.ledger.journals.posted";
    public static final String LEDGER_BALANCE_UPDATES   = "finance.ledger.balance.updates";
    public static final String LEDGER_HASH_CHAIN_ERRORS = "finance.ledger.hash_chain.errors";
    public static final String LEDGER_POSTING_DURATION  = "finance.ledger.posting.duration";

    // --- IAM ---
    public static final String IAM_TOKENS_ISSUED   = "finance.iam.tokens.issued";
    public static final String IAM_AUTH_FAILURES   = "finance.iam.auth.failures";
    public static final String IAM_TOKEN_DURATION  = "finance.iam.token.issuance.duration";

    // --- Secrets ---
    public static final String SECRETS_ACCESS        = "finance.secrets.access";
    public static final String SECRETS_ROTATION      = "finance.secrets.rotation";
    public static final String SECRETS_ACCESS_ERRORS = "finance.secrets.access.errors";

    // --- Calendar ---
    public static final String CALENDAR_QUERIES = "finance.calendar.queries";

    private FinanceMetricNames() {}
}
