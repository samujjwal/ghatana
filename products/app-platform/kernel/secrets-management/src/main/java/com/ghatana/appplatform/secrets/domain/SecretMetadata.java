/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.domain;

import java.time.Duration;

/**
 * Metadata governing lifecycle and rotation for a secret (K14-006).
 *
 * @doc.type record
 * @doc.purpose Rotation and lifecycle metadata for a managed secret
 * @doc.layer core
 * @doc.pattern ValueObject
 */
public record SecretMetadata(
        String description,
        Duration maxAge,         // how long before the secret should be rotated
        boolean autoRotate,      // true = rotation scheduler will rotate this automatically
        String rotationProvider  // provider key for rotation: "local", "vault"
) {

    /** Default metadata: 90-day max age, no auto-rotation. */
    public static SecretMetadata defaults() {
        return new SecretMetadata("", Duration.ofDays(90), false, "local");
    }

    /** Creates metadata with auto-rotation enabled. */
    public static SecretMetadata autoRotating(Duration maxAge, String rotationProvider) {
        return new SecretMetadata("", maxAge, true, rotationProvider);
    }
}
