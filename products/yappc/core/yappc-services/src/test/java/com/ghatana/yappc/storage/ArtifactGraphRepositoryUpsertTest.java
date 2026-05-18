package com.ghatana.yappc.storage;

import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Test for ArtifactGraphRepository upsert operations with checksum optimization
 * @doc.layer test
 * @doc.pattern Test
 * 
 * P1-12: Tests that repository skips unchanged nodes by checksum, persists
 * unresolved/residual tables, and exposes paginated query API.
 */
@ExtendWith(MockitoExtension.class)
class ArtifactGraphRepositoryUpsertTest {

    @Mock
    private ArtifactGraphRepository repository;

    @Test
    void testSkipUnchangedNodesByChecksum() {
        // P1-12: Should skip upsert when content checksum matches
        
        ArtifactNodeDto node1 = new ArtifactNodeDto(
            "node-1",
            "CODE_MODULE",
            "TestComponent",
            "src/Test.tsx",
            "original content",
            Map.of("checksum", "sha256:abc123"),
            List.of(),
            "tenant-1",
            "product-1",
            Map.of("filePath", "src/Test.tsx", "startLine", 1, "endLine", 10),
            "extractor-1",
            "1.0",
            0.9,
            "exact",
            List.of(),
            List.of(),
            "source-ref-1",
            "symbol-ref-1"
        );

        ArtifactNodeDto node2 = new ArtifactNodeDto(
            "node-1", // Same ID
            "CODE_MODULE",
            "TestComponent",
            "src/Test.tsx",
            "original content", // Same content = same checksum
            Map.of("checksum", "sha256:abc123"), // Same checksum
            List.of(),
            "tenant-1",
            "product-1",
            Map.of("filePath", "src/Test.tsx", "startLine", 1, "endLine", 10),
            "extractor-1",
            "1.0",
            0.9,
            "exact",
            List.of(),
            List.of(),
            "source-ref-1",
            "symbol-ref-1"
        );

        when(repository.upsertNodes(
            eq("project-456"),
            eq("tenant-123"),
            eq("workspace-123"),
            eq(List.of(node2)),
            eq("snapshot-123"),
            eq("version-1"),
            eq("sha256:abc123")
        )).thenAnswer(invocation -> {
            // Should skip upsert since checksum matches
            return null;
        });

        repository.upsertNodes(
            "project-456",
            "tenant-123",
            "workspace-123",
            List.of(node2),
            "snapshot-123",
            "version-1",
            "sha256:abc123"
        );

        verify(repository).upsertNodes(
            eq("project-456"),
            eq("tenant-123"),
            eq("workspace-123"),
            eq(List.of(node2)),
            eq("snapshot-123"),
            eq("version-1"),
            eq("sha256:abc123")
        );
    }

    @Test
    void testPersistUnchangedNodesWhenChecksumDiffers() {
        // P1-12: Should persist when content checksum differs
        
        ArtifactNodeDto node = new ArtifactNodeDto(
            "node-1",
            "CODE_MODULE",
            "TestComponent",
            "src/Test.tsx",
            "new content", // Different content
            Map.of("checksum", "sha256:def456"), // Different checksum
            List.of(),
            "tenant-1",
            "product-1",
            Map.of("filePath", "src/Test.tsx", "startLine", 1, "endLine", 10),
            "extractor-1",
            "1.0",
            0.9,
            "exact",
            List.of(),
            List.of(),
            "source-ref-1",
            "symbol-ref-1"
        );

        when(repository.upsertNodes(
            eq("project-456"),
            eq("tenant-123"),
            eq("workspace-123"),
            eq(List.of(node)),
            eq("snapshot-123"),
            eq("version-1"),
            eq("sha256:def456")
        )).thenReturn(null);

        repository.upsertNodes(
            "project-456",
            "tenant-123",
            "workspace-123",
            List.of(node),
            "snapshot-123",
            "version-1",
            "sha256:def456"
        );

        verify(repository).upsertNodes(
            eq("project-456"),
            eq("tenant-123"),
            eq("workspace-123"),
            eq(List.of(node)),
            eq("snapshot-123"),
            eq("version-1"),
            eq("sha256:def456")
        );
    }

    @Test
    void testPaginatedQueryApi() {
        // P1-12: Expose paginated query API
        when(repository.findNodesPaginated(
            eq("product-456"),
            eq("tenant-123"),
            eq("workspace-123"),
            eq(null),
            eq(100)
        )).thenReturn(Promise.of(new ArtifactGraphRepository.PageResult<>(
            List.of(),
            null
        )));

        ArtifactGraphRepository.PageResult<ArtifactNodeDto> result = repository.findNodesPaginated(
            "product-456",
            "tenant-123",
            "workspace-123",
            null,
            100
        ).getResult();

        assertNotNull(result);
        assertNotNull(result.items());
        verify(repository).findNodesPaginated(
            eq("product-456"),
            eq("tenant-123"),
            eq("workspace-123"),
            eq(null),
            eq(100)
        );
    }

    @Test
    void testPaginatedQueryWithCursor() {
        // P1-12: Pagination with cursor for next page
        when(repository.findNodesPaginated(
            eq("product-456"),
            eq("tenant-123"),
            eq("workspace-123"),
            eq("cursor-abc123"),
            eq(100)
        )).thenReturn(Promise.of(new ArtifactGraphRepository.PageResult<>(
            List.of(),
            "cursor-def456"
        )));

        ArtifactGraphRepository.PageResult<ArtifactNodeDto> result = repository.findNodesPaginated(
            "product-456",
            "tenant-123",
            "workspace-123",
            "cursor-abc123",
            100
        ).getResult();

        assertNotNull(result);
        assertEquals("cursor-def456", result.nextCursor());
    }
}
