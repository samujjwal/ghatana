package com.ghatana.digitalmarketing.application.idempotency;

import com.ghatana.digitalmarketing.application.idempotency.IdempotencyService.IdempotentResponse;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.persistence.idempotency.IdempotencyTokenRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of IdempotencyService.
 *
 * @doc.type class
 * @doc.purpose Production idempotency service for duplicate request prevention
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class IdempotencyServiceImpl implements IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyServiceImpl.class);
    private static final int DEFAULT_EXPIRATION_HOURS = 24;

    private final IdempotencyTokenRepository repository;
    private final int expirationHours;

    public IdempotencyServiceImpl(IdempotencyTokenRepository repository) {
        this(repository, DEFAULT_EXPIRATION_HOURS);
    }

    public IdempotencyServiceImpl(IdempotencyTokenRepository repository, int expirationHours) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.expirationHours = expirationHours;
    }

    @Override
    public Promise<IdempotentResponse> getCachedResponse(DmOperationContext ctx, String idempotencyKey) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");

        return repository.findByKey(ctx.getWorkspaceId(), idempotencyKey)
            .then(response -> {
                if (response != null) {
                    LOG.info("[DMOS] Idempotency cache hit: key={} workspace={}",
                        idempotencyKey, ctx.getWorkspaceId().getValue());
                }
                return Promise.of(response);
            });
    }

    @Override
    public Promise<Void> storeResponse(DmOperationContext ctx, String idempotencyKey, IdempotentResponse response) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(response, "response must not be null");

        Instant expiresAt = Instant.now().plus(expirationHours, ChronoUnit.HOURS);
        return repository.store(ctx.getWorkspaceId(), idempotencyKey, response, expiresAt)
            .then(__ -> {
                LOG.info("[DMOS] Idempotency response stored: key={} workspace={} expiresAt={}",
                    idempotencyKey, ctx.getWorkspaceId().getValue(), expiresAt);
                return Promise.of((Void) null);
            });
    }

    @Override
    public String generateIdempotencyKey() {
        return UUID.randomUUID().toString();
    }
}
