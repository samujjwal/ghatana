/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * Enforces declared learning contract boundaries.
 */
public final class LearningPermissionChecker {
    private LearningPermissionChecker() {}

    public static void requireAllowed(LearningContract contract, LearningTarget target) {
        if (contract == null || !contract.permits(target)) {
            throw new IllegalArgumentException("Learning target " + target + " is not permitted by contract");
        }
        if (contract.level().isOfflineOnly() && target == LearningTarget.MODEL_ADAPTER) {
            throw new IllegalStateException("MODEL_ADAPTER learning is offline-only and cannot self-activate");
        }
    }
}
