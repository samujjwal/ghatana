/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.sandbox;

import com.ghatana.appplatform.rules.OpaEvaluationService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Restricted API surface available inside a T2 rule sandbox (STORY-K03-005).
 *
 * <p>T2 rules may only access the following capabilities through this surface:
 * <ul>
 *   <li>Read config values (K-02) — read-only, no writes</li>
 *   <li>Read reference data (D-11) — read-only</li>
 *   <li>Read calendar data (K-15) — business day checks, conversions</li>
 *   <li>Evaluate sub-policies via OPA — policy delegation</li>
 *   <li>Structured logging — context-aware, no raw I/O</li>
 * </ul>
 *
 * <p>All methods are instrumented to record API call counts via
 * {@link SandboxResourceAccountant}. Network access is structurally impossible
 * because this class has no socket or HTTP dependencies.
 *
 * @doc.type  class
 * @doc.purpose Restricted read-only API surface for T2 sandboxed rule execution (K03-005)
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class SandboxApiSurface {

    private static final Logger log = LoggerFactory.getLogger(SandboxApiSurface.class);

    private final String jurisdictionId;
    private final ConfigReader configReader;
    private final ReferenceDataReader referenceDataReader;
    private final CalendarReader calendarReader;
    private final OpaEvaluationService opaService;
    private final Executor executor;

    // ── Call tracking ─────────────────────────────────────────────────────────
    private volatile long apiCallCount = 0;
    private static final long MAX_API_CALLS_PER_EXECUTION = 100;

    public SandboxApiSurface(String jurisdictionId,
                             ConfigReader configReader,
                             ReferenceDataReader referenceDataReader,
                             CalendarReader calendarReader,
                             OpaEvaluationService opaService,
                             Executor executor) {
        this.jurisdictionId    = Objects.requireNonNull(jurisdictionId,      "jurisdictionId");
        this.configReader      = Objects.requireNonNull(configReader,        "configReader");
        this.referenceDataReader = Objects.requireNonNull(referenceDataReader, "referenceDataReader");
        this.calendarReader    = Objects.requireNonNull(calendarReader,      "calendarReader");
        this.opaService        = Objects.requireNonNull(opaService,          "opaService");
        this.executor          = Objects.requireNonNull(executor,            "executor");
    }

    public String jurisdictionId() { return jurisdictionId; }

    // ── Config read (K-02, read-only) ─────────────────────────────────────────

    /**
     * Read a configuration value for this jurisdiction.
     *
     * @param namespace config namespace
     * @param key       config key
     * @return value string, or {@code null} if absent
     */
    public String readConfig(String namespace, String key) {
        checkApiQuota("readConfig");
        return configReader.get(jurisdictionId, namespace, key);
    }

    // ── Reference data (D-11, read-only) ─────────────────────────────────────

    /**
     * Look up an instrument by symbol (read-only).
     *
     * @param symbol instrument symbol
     * @return instrument attributes map, or empty map if not found
     */
    public Map<String, Object> lookupInstrument(String symbol) {
        checkApiQuota("lookupInstrument");
        return referenceDataReader.getInstrument(symbol);
    }

    // ── Calendar (K-15) ───────────────────────────────────────────────────────

    /**
     * Checks if the given Gregorian date (YYYY-MM-DD) is a business day
     * for the current jurisdiction.
     *
     * @param gregorianDate ISO date string
     * @return true if the date is a business day
     */
    public boolean isBusinessDay(String gregorianDate) {
        checkApiQuota("isBusinessDay");
        return calendarReader.isBusinessDay(jurisdictionId, gregorianDate);
    }

    // ── Sub-policy evaluation (OPA delegation) ────────────────────────────────

    /**
     * Evaluate a sub-policy via OPA. Does NOT allow writing to any store.
     *
     * @param policyPath OPA policy path
     * @param input      input document
     * @return promise resolving to the OPA allow decision
     */
    public Promise<Boolean> evaluateSubPolicy(String policyPath, Map<String, Object> input) {
        checkApiQuota("evaluateSubPolicy");
        return opaService.evaluate(policyPath, input)
                .map(OpaEvaluationService.OpaResult::allow);
    }

    // ── Structured logging ────────────────────────────────────────────────────

    /**
     * Log a message from inside the sandbox. Messages are tagged with jurisdiction context.
     *
     * @param level   one of INFO, WARN, DEBUG
     * @param message log message
     */
    public void log(String level, String message) {
        // No quota increment — logging is always allowed
        String tag = "[T2:" + jurisdictionId + "] " + message;
        switch (level.toUpperCase()) {
            case "WARN"  -> log.warn(tag);
            case "DEBUG" -> log.debug(tag);
            default      -> log.info(tag);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void checkApiQuota(String method) {
        long count = ++apiCallCount;
        if (count > MAX_API_CALLS_PER_EXECUTION) {
            throw new SandboxApiQuotaExceededException(
                    "T2 rule exceeded max API calls (" + MAX_API_CALLS_PER_EXECUTION
                    + ") at method=" + method + " for jurisdiction=" + jurisdictionId);
        }
    }

    void resetApiCallCount() {
        this.apiCallCount = 0;
    }

    // ── Functional interfaces for the read-only facades ──────────────────────

    @FunctionalInterface
    public interface ConfigReader {
        String get(String jurisdictionId, String namespace, String key);
    }

    @FunctionalInterface
    public interface ReferenceDataReader {
        Map<String, Object> getInstrument(String symbol);
    }

    @FunctionalInterface
    public interface CalendarReader {
        boolean isBusinessDay(String jurisdictionId, String gregorianDate);
    }

    /** Thrown when a T2 rule exceeds the per-execution API call quota. */
    public static final class SandboxApiQuotaExceededException extends RuntimeException {
        public SandboxApiQuotaExceededException(String message) {
            super(message);
        }
    }
}
