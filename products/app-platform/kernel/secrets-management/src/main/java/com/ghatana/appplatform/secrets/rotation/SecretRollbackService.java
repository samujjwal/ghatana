/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.rotation;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Rolls back a secret to a specific historical version by writing that version's
 * plain-text value as a new current version (K14-014).
 *
 * <p>The rollback is non-destructive: it reads the desired historical version
 * via {@link SecretProvider#getSecretVersion} and stores the plain text back via
 * {@link SecretProvider#putSecret}, which assigns the next monotonic version number.
 * Historical versions remain available in providers that support full version history
 * (e.g., Vault KV v2).
 *
 * <p>Security note: the retrieved plain-text value is zeroed after being written
 * via {@link SecretValue#destroy()}.
 *
 * @doc.type class
 * @doc.purpose Secret rollback to a prior version by re-writing its value (K14-014)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SecretRollbackService {

    private static final Logger log = LoggerFactory.getLogger(SecretRollbackService.class);

    private final SecretProvider provider;

    public SecretRollbackService(SecretProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Rolls back a secret to the specified version.
     *
     * <p>Steps:
     * <ol>
     *   <li>Retrieves the historical version via {@code provider.getSecretVersion(path, version)}.</li>
     *   <li>Writes the plain-text value back via {@code provider.putSecret}, yielding a new
     *       version number that is current+1.</li>
     *   <li>Zeros the char[] in the fetched {@link SecretValue} via {@code destroy()}.</li>
     * </ol>
     *
     * @param path           secret path
     * @param targetVersion  historical version to restore (must be ≥ 1)
     * @param reason         mandatory rollback reason for audit trail
     * @return the newly written secret value (current+1 version, same plain text)
     * @throws IllegalArgumentException if targetVersion &lt; 1 or reason is blank
     */
    public Promise<SecretValue> rollback(String path, int targetVersion, String reason) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reason, "reason");
        if (targetVersion < 1) throw new IllegalArgumentException("targetVersion must be >= 1");
        if (reason.isBlank())  throw new IllegalArgumentException("rollback reason must not be blank");

        log.warn("SecretRollback initiated: path={} targetVersion={} reason='{}'",
                 path, targetVersion, reason);

        return provider.getSecretVersion(path, targetVersion)
            .then(historical -> {
                SecretMetadata meta = new SecretMetadata(
                    "rollback to v" + targetVersion + ": " + reason,
                    null,
                    false,
                    "rollback"
                );
                return provider.putSecret(path, historical.value(), meta)
                    .then(written -> {
                        historical.destroy();   // zero the char[] from the historical version
                        log.info("SecretRollback complete: path={} restoredVersion={} newVersion={}",
                                 path, targetVersion, written.version());
                        return Promise.of(written);
                    });
            });
    }
}
