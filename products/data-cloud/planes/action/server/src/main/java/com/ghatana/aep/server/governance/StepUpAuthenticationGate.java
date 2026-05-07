/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.governance;

import io.activej.promise.Promise;

/**
 * Strategy interface for step-up authentication (MFA verification before sensitive operations).
 *
 * <p>F-018: Kill-switch activation and other governance operations require step-up verification
 * to ensure that only authorized actors with additional verification can perform critical actions.
 *
 * <p>Implementation strategies:
 * <ul>
 *   <li>TOTP verification (MFA service)
 *   <li>Backup code validation
 *   <li>Hardware security key verification (future)
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Strategy for multi-factor verification before critical governance operations
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface StepUpAuthenticationGate {
    /**
     * Verifies step-up credentials (MFA code or backup code).
     *
     * <p>Called before sensitive operations such as kill-switch activation.
     * Returns true if and only if the credentials are valid and the actor
     * is authorized to perform the operation.
     *
     * @param userId the authenticated user ID
     * @param tenantId the tenant context
     * @param mfaCode either a 6-digit TOTP code or a backup code
     * @return Promise&lt;Boolean&gt; — true if verification succeeded, false otherwise
     */
    Promise<Boolean> verify(String userId, String tenantId, String mfaCode);

    /**
     * Checks if step-up verification is available for the given user.
     *
     * @param userId the user ID
     * @return Promise&lt;Boolean&gt; — true if MFA is enabled for this user
     */
    Promise<Boolean> isMfaEnabled(String userId);
}

