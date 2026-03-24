package com.ghatana.yappc.storage;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.PhaseType;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for YappcArtifactRepository
 * @doc.layer test
 * @doc.pattern Test
 */
class YappcArtifactRepositoryTest extends EventloopTestBase {
    
    private YappcArtifactRepository repository;
    private InMemoryArtifactStore store;
    
    @BeforeEach
    void setUp() {
        store = new InMemoryArtifactStore();
        repository = new YappcArtifactRepository(store);
    }
    
    @Test
    void shouldStoreAndRetrieveArtifact() {
        // GIVEN
        String productId = "product-123";
        PhaseType phase = PhaseType.INTENT;
        byte[] content = "test content".getBytes();
        
        // WHEN
        String version = runPromise(() -> repository.storeArtifact(productId, phase, content));
        byte[] retrieved = runPromise(() -> repository.getArtifact(productId, phase, version));
        
        // THEN
        assertNotNull(version);
        assertArrayEquals(content, retrieved);
    }
    
    @Test
    void shouldListVersions() {
        // GIVEN
        String productId = "product-123";
        PhaseType phase = PhaseType.SHAPE;
        
        runPromise(() -> repository.storeArtifact(productId, phase, "v1".getBytes()));
        runPromise(() -> repository.storeArtifact(productId, phase, "v2".getBytes()));
        
        // WHEN
        List<String> versions = runPromise(() -> repository.listVersions(productId, phase));
        
        // THEN
        assertNotNull(versions);
        assertEquals(2, versions.size());
    }
    
    @Test
    void shouldStoreAndRetrieveMetadata() {
        // GIVEN
        String productId = "product-123";
        PhaseType phase = PhaseType.GENERATE;
        String version = "v1";
        Map<String, String> metadata = Map.of("author", "test", "timestamp", "2025-01-07");
        
        // WHEN
        runPromise(() -> repository.storeMetadata(productId, phase, version, metadata));
        
        // THEN - metadata stored successfully and can be retrieved
        Map<String, String> retrieved = runPromise(() -> store.getMetadata(
            String.format("products/%s/phases/%s/%s/metadata", productId, phase.name().toLowerCase(), version)));
        assertEquals("test", retrieved.get("author"));
        assertEquals("2025-01-07", retrieved.get("timestamp"));
    }
    
    @Test
    void shouldHandleNonExistentArtifact() {
        // GIVEN
        String productId = "nonexistent";
        PhaseType phase = PhaseType.INTENT;
        String version = "v1";
        
        // WHEN/THEN
        Exception e = assertThrows(Exception.class, () ->
                runPromise(() -> repository.getArtifact(productId, phase, version)));
        assertTrue(e.getMessage().contains("not found"));
    }
}
