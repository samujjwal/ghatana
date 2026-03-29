/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.error;

/**
 * Base exception for all AEP domain errors (AEP-003).
 *
 * <p>All AEP-specific exceptions extend this class, enabling callers to catch
 * the full AEP exception hierarchy with a single {@code catch (AepException e)}
 * clause, or catch specific subtypes for granular handling.
 *
 * <p>Error categories:
 * <ul>
 *   <li>{@link AepProcessingException} — event processing pipeline failures</li>
 *   <li>{@link AepConsentException} — consent evaluation and enforcement failures</li>
 *   <li>{@link AepTenantException} — tenant isolation violations</li>
 *   <li>{@link AepConfigException} — configuration errors detected at runtime</li>
 *   <li>{@link AepVersionException} — event schema version incompatibility</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Base for all categorised AEP domain exceptions
 * @doc.layer product
 * @doc.pattern Exception
 * @since 1.2.0
 */
public class AepException extends RuntimeException {

    private final String context;
    private final String operation;

    /**
     * @param context   human-readable context (e.g. tenant ID or event ID)
     * @param operation operation that failed (e.g. {@code "consent.eval"})
     * @param message   explanation of the failure
     */
    public AepException(String context, String operation, String message) {
        super("[" + operation + "][" + context + "] " + message);
        this.context   = context;
        this.operation = operation;
    }

    /**
     * @param context   human-readable context
     * @param operation operation that failed
     * @param message   explanation of the failure
     * @param cause     underlying cause
     */
    public AepException(String context, String operation, String message, Throwable cause) {
        super("[" + operation + "][" + context + "] " + message, cause);
        this.context   = context;
        this.operation = operation;
    }

    /** @return the context label supplied at construction time */
    public String context() {
        return context;
    }

    /** @return the operation label supplied at construction time */
    public String operation() {
        return operation;
    }
}
