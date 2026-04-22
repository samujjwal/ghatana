package com.ghatana.yappc.storage;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.PhaseType;
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
    void setUp() { // GH-90000
        store = new InMemoryArtifactStore(); // GH-90000
        repository = new YappcArtifactRepository(store); // GH-90000
    }

    @Test
    void shouldStoreAndRetrieveArtifact() { // GH-90000
        // GIVEN
        String productId = "product-123";
        PhaseType phase = PhaseType.INTENT;
        byte[] content = "test content".getBytes(); // GH-90000

        // WHEN
        String version = runPromise(() -> repository.storeArtifact(productId, phase, content)); // GH-90000
        byte[] retrieved = runPromise(() -> repository.getArtifact(productId, phase, version)); // GH-90000

        // THEN
        assertNotNull(version); // GH-90000
        assertArrayEquals(content, retrieved); // GH-90000
    }

    @Test
    void shouldListVersions() { // GH-90000
        // GIVEN
        String productId = "product-123";
        PhaseType phase = PhaseType.SHAPE;

        runPromise(() -> repository.storeArtifact(productId, phase, "v1".getBytes())); // GH-90000
        runPromise(() -> repository.storeArtifact(productId, phase, "v2".getBytes())); // GH-90000

        // WHEN
        List<String> versions = runPromise(() -> repository.listVersions(productId, phase)); // GH-90000

        // THEN
        assertNotNull(versions); // GH-90000
        assertEquals(2, versions.size()); // GH-90000
    }

    @Test
    void shouldStoreAndRetrieveMetadata() { // GH-90000
        // GIVEN
        String productId = "product-123";
        PhaseType phase = PhaseType.GENERATE;
        String version = "v1";
        Map<String, String> metadata = Map.of("author", "test", "timestamp", "2025-01-07"); // GH-90000

        // WHEN
        runPromise(() -> repository.storeMetadata(productId, phase, version, metadata)); // GH-90000

        // THEN - metadata stored successfully and can be retrieved
        Map<String, String> retrieved = runPromise(() -> store.getMetadata( // GH-90000
            String.format("products/%s/phases/%s/%s/metadata", productId, phase.name().toLowerCase(), version))); // GH-90000
        assertEquals("test", retrieved.get("author [GH-90000]"));
        assertEquals("2025-01-07", retrieved.get("timestamp [GH-90000]"));
    }

    @Test
    void shouldHandleNonExistentArtifact() { // GH-90000
        // GIVEN
        String productId = "nonexistent";
        PhaseType phase = PhaseType.INTENT;
        String version = "v1";

        // WHEN/THEN
        Exception e = assertThrows(Exception.class, () -> // GH-90000
                runPromise(() -> repository.getArtifact(productId, phase, version))); // GH-90000
        assertTrue(e.getMessage().contains("not found [GH-90000]"));
    }
}
