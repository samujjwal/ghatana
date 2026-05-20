/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime hardening validation for fallback implementations.
 *
 * <p>DC-P1-02: Ensures that fallback implementations are only used in appropriate
 * runtime profiles. Prevents accidental use of in-memory storage in production.
 *
 * @doc.type class
 * @doc.purpose Runtime profile validation for storage implementations
 * @doc.layer product
 * @doc.pattern Validator
 */
public final class RuntimeProfileValidator {

    private static final Logger log = LoggerFactory.getLogger(RuntimeProfileValidator.class);

    private RuntimeProfileValidator() {
        // Utility class
    }

    /**
     * Validate that the provided storage implementation is appropriate for the runtime profile.
     *
     * <p>DC-P1-02: Raises IllegalStateException if an in-memory implementation is used
     * in production profiles.
     *
     * @param store the storage implementation to validate
     * @param profile the active runtime profile
     * @param context human-readable context (e.g., "ContextLayerHandler initialization")
     * @throws IllegalStateException if validation fails
     */
    public static void validateStorageImplementation(Object store, RuntimeProfile profile, String context) {
        if (isInMemoryImplementation(store)) {
            if (profile.requiresDurableStorage()) {
                String errorMsg = String.format(
                        "[DC-P1-02] %s is not permitted in %s profile. " +
                        "Context: %s. " +
                        "Use a durable implementation instead.",
                        implementationName(store), profile, context);
                log.error(errorMsg);
                throw new IllegalStateException(errorMsg);
            } else {
                log.warn("[DC-P1-02] Using {} in {} profile. " +
                        "This is only acceptable for development/testing. Context: {}",
                        implementationName(store), profile, context);
            }
        }
    }

    private static boolean isInMemoryImplementation(Object store) {
        return store != null && implementationName(store).startsWith("InMemory");
    }

    private static String implementationName(Object store) {
        return store == null ? "unknown-storage" : store.getClass().getSimpleName();
    }

    /**
     * Validate that a fallback operation is permitted in the current profile.
     *
     * <p>DC-P1-02: Centralized validation for all fallback code paths.
     *
     * @param fallbackName Name of the fallback operation (e.g., "use default timeout")
     * @param profile active runtime profile
     * @param defaultBehavior the default value being used as fallback
     * @throws IllegalStateException if fallback is not permitted
     */
    public static void validateFallbackPermitted(String fallbackName, RuntimeProfile profile, Object defaultBehavior) {
        if (profile.requiresDurableStorage()) {
            String errorMsg = String.format(
                    "[DC-P1-02] Fallback not permitted in %s profile: %s (default=%s). " +
                    "Fallbacks are only allowed in LOCAL and TEST profiles.",
                    profile, fallbackName, defaultBehavior);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        } else {
            log.warn("[DC-P1-02] Using fallback in {} profile: {} (default={})",
                    profile, fallbackName, defaultBehavior);
        }
    }

    /**
     * Log that a fallback is being used (with context).
     *
     * @param fallbackName Name of the fallback
     * @param profile active runtime profile
     * @param reason Why the fallback is being used
     */
    public static void logFallbackUsage(String fallbackName, RuntimeProfile profile, String reason) {
        if (profile.isDebugAllowed()) {
            log.debug("[DC-P1-02] Fallback in {} profile: {} (reason: {})",
                    profile, fallbackName, reason);
        } else {
            log.info("[DC-P1-02] Fallback in {} profile: {} (reason: {})",
                    profile, fallbackName, reason);
        }
    }
}
