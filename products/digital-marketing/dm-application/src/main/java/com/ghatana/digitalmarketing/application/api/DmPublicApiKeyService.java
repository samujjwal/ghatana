package com.ghatana.digitalmarketing.application.api;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.api.DmPublicApiKey;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for managing public API platform credentials.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for public API key management (DMOS-F5-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmPublicApiKeyService {

    Promise<DmPublicApiKey> issue(DmOperationContext ctx, IssuePublicApiKeyCommand cmd);

    Promise<DmPublicApiKey> revoke(DmOperationContext ctx, String keyId);

    Promise<DmPublicApiKey> recordUsage(DmOperationContext ctx, String keyId);

    Promise<Optional<DmPublicApiKey>> findById(DmOperationContext ctx, String keyId);

    Promise<List<DmPublicApiKey>> listActiveByTenant(DmOperationContext ctx);

    record IssuePublicApiKeyCommand(
            String displayName,
            String keyHash,
            List<String> scopes,
            Instant expiresAt
    ) {
        public IssuePublicApiKeyCommand {
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (keyHash == null || keyHash.isBlank()) throw new IllegalArgumentException("keyHash must not be blank");
            if (scopes == null) throw new IllegalArgumentException("scopes must not be null");
        }
    }
}
