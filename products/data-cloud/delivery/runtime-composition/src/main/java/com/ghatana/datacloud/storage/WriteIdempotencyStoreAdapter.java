package com.ghatana.datacloud.storage;

import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import com.ghatana.platform.observability.idempotency.IdempotencyEntry;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that wraps a {@link WriteIdempotencyStore} to implement the platform {@link IdempotencyStore} interface.
 *
 * <p>This adapter allows the Data Cloud SPI {@code WriteIdempotencyStore} to be used with handlers
 * that expect the platform {@code IdempotencyStore} interface for idempotency checks via {@code IdempotencyHelper}.
 *
 * <p>The adapter translates between the two interfaces:
 * <ul>
 *   <li>Synchronous SPI methods → Async platform Promise methods</li>
 *   <li>Simple key-based lookup → Composite key (tenantId:scope:key:principalId)</li>
 *   <li>Map-based response → Object response with payload hash</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Adapter to bridge WriteIdempotencyStore to IdempotencyStore
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class WriteIdempotencyStoreAdapter implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(WriteIdempotencyStoreAdapter.class);
    private static final IdempotencyEntry CACHE_MISS = null;

    private final WriteIdempotencyStore delegate;

    /**
     * Creates an adapter wrapping the given SPI idempotency store.
     *
     * @param delegate the SPI idempotency store to wrap
     */
    public WriteIdempotencyStoreAdapter(WriteIdempotencyStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public Promise<IdempotencyEntry> get(String tenantId, String scope, String idempotencyKey, String principalId) {
        // SPI interface doesn't use principalId in the key, so we ignore it for compatibility
        Optional<Map<String, Object>> response = delegate.get(tenantId, scope, idempotencyKey);
        if (response.isEmpty()) {
            return Promise.of(CACHE_MISS);
        }
        // Create an entry with empty payload hash since SPI doesn't track it
        IdempotencyEntry entry = new IdempotencyEntry(idempotencyKey, "", response.get());
        return Promise.of(entry);
    }

    @Override
    public Promise<Void> put(String tenantId, String scope, String idempotencyKey, String principalId,
                             String payloadHash, Object response) {
        // SPI interface expects Map<String, Object>, so we cast if possible
        if (response instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = (Map<String, Object>) response;
            delegate.put(tenantId, scope, idempotencyKey, responseMap);
        } else {
            log.warn("[IdempotencyAdapter] Response is not a Map, cannot store in WriteIdempotencyStore: {}", response.getClass());
        }
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> hasConflict(String tenantId, String scope, String idempotencyKey,
                                        String principalId, String payloadHash) {
        // SPI interface doesn't support conflict detection, so we always return false
        // This means conflict detection is not available when using WriteIdempotencyStore
        return Promise.of(false);
    }
}
