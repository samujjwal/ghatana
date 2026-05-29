/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.api.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IdempotencyManager.
 * 
 * P7.2: Verify idempotency management for safe retry of mutating operations.
 * 
 * @doc.type test
 * @doc.purpose Verify idempotency manager behavior
 * @doc.layer product
 */
@DisplayName("IdempotencyManager Tests")
class IdempotencyManagerTest {

    private IdempotencyManager manager;

    @BeforeEach
    void setUp() {
        manager = new IdempotencyManager(Duration.ofHours(1));
    }

    @Test
    @DisplayName("getCachedResponse returns null for non-existent key")
    void getCachedResponseReturnsNullForNonExistent() {
        assertNull(manager.getCachedResponse("non-existent-key"));
    }

    @Test
    @DisplayName("getCachedResponse returns cached response for existing key")
    void getCachedResponseReturnsCached() {
        IdempotencyManager.CachedResponse response = new IdempotencyManager.CachedResponse(
            200, "{\"result\":\"ok\"}", Map.of("Content-Type", "application/json")
        );
        manager.storeResponse("test-key", response);

        IdempotencyManager.CachedResponse cached = manager.getCachedResponse("test-key");
        assertNotNull(cached);
        assertEquals(200, cached.statusCode());
        assertEquals("{\"result\":\"ok\"}", cached.body());
    }

    @Test
    @DisplayName("getCachedResponse returns null for expired response")
    void getCachedResponseReturnsNullForExpired() {
        IdempotencyManager managerShortTTL = new IdempotencyManager(Duration.ofMillis(1));
        IdempotencyManager.CachedResponse response = new IdempotencyManager.CachedResponse(
            200, "{\"result\":\"ok\"}", Map.of()
        );
        managerShortTTL.storeResponse("test-key", response);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertNull(managerShortTTL.getCachedResponse("test-key"));
    }

    @Test
    @DisplayName("storeResponse stores response correctly")
    void storeResponseStoresCorrectly() {
        IdempotencyManager.CachedResponse response = new IdempotencyManager.CachedResponse(
            201, "{\"id\":\"123\"}", Map.of("Location", "/resource/123")
        );
        manager.storeResponse("create-key", response);

        assertEquals(1, manager.size());
    }

    @Test
    @DisplayName("clear removes all cached responses")
    void clearRemovesAll() {
        manager.storeResponse("key1", new IdempotencyManager.CachedResponse(200, "{}", Map.of()));
        manager.storeResponse("key2", new IdempotencyManager.CachedResponse(200, "{}", Map.of()));
        assertEquals(2, manager.size());

        manager.clear();
        assertEquals(0, manager.size());
    }

    @Test
    @DisplayName("cleanupExpired removes only expired entries")
    void cleanupExpiredRemovesOnlyExpired() {
        IdempotencyManager managerShortTTL = new IdempotencyManager(Duration.ofMillis(1));
        
        managerShortTTL.storeResponse("fresh", new IdempotencyManager.CachedResponse(200, "{}", Map.of()));
        managerShortTTL.storeResponse("expired", new IdempotencyManager.CachedResponse(200, "{}", Map.of()));

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        managerShortTTL.storeResponse("new", new IdempotencyManager.CachedResponse(200, "{}", Map.of()));
        managerShortTTL.cleanupExpired();

        assertNull(managerShortTTL.getCachedResponse("expired"));
        assertNull(managerShortTTL.getCachedResponse("fresh"));
        assertNotNull(managerShortTTL.getCachedResponse("new"));
    }

    @Test
    @DisplayName("CachedResponse isExpired returns correct value")
    void cachedResponseIsExpiredCorrect() {
        IdempotencyManager.CachedResponse response = new IdempotencyManager.CachedResponse(
            200, "{}", Map.of()
        );
        
        assertFalse(response.isExpired(Duration.ofHours(1)));
        
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertTrue(response.isExpired(Duration.ofMillis(1)));
    }
}
