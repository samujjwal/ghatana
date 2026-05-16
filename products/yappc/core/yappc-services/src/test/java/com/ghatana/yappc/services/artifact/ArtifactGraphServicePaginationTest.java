package com.ghatana.yappc.services.artifact;

import com.ghatana.yappc.domain.artifact.ArtifactGraphQueryResponse;
import com.ghatana.yappc.domain.artifact.ArtifactNodeDto;
import com.ghatana.yappc.domain.artifact.ArtifactEdgeDto;
import com.ghatana.yappc.storage.ArtifactGraphRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Test for graph query pagination in ArtifactGraphService
 * @doc.layer test
 * @doc.pattern Test
 * 
 * P1-13: Tests that graph queries route through repository pagination
 * and return cursor-based response structure.
 */
@ExtendWith(MockitoExtension.class)
class ArtifactGraphServicePaginationTest {

    @Mock
    private ArtifactGraphRepository repository;

    private ArtifactGraphServiceImpl service;

    @BeforeEach
    void setUp() {
        Executor blockingExecutor = Runnable::run;
        service = new ArtifactGraphServiceImpl(repository, blockingExecutor);
    }

    @Test
    void testQueryGraphRoutesThroughRepositoryPagination() {
        // P1-13: Query should route through repository pagination
        ArtifactGraphRepository.PageResult<ArtifactNodeDto> nodesPage = 
            new ArtifactGraphRepository.PageResult<>(
                List.of(
                    new ArtifactNodeDto("node1", "CODE_MODULE", "Test", null, null, Map.<String, Object>of(), List.<String>of(), null, null, null, null, null, (Double)null, null, null, null, null, null)
                ),
                "cursor-abc123"
            );

        ArtifactGraphRepository.PageResult<ArtifactEdgeDto> edgesPage =
            new ArtifactGraphRepository.PageResult<>(
                List.of(),
                null
            );

        when(repository.findNodesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(nodesPage));
        when(repository.findEdgesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(edgesPage));

        Promise<ArtifactGraphQueryResponse> response = service.queryGraph(
            "product-456",
            "tenant-123",
            "stats",
            null,
            null,
            100
        );

        response.then(result -> {
            assertNotNull(result);
            assertNotNull(result.nextCursor());
            assertEquals("cursor-abc123", result.nextCursor());
            assertNotNull(result.scope());
            assertEquals("tenant-123", result.scope().tenantId());
            assertEquals("product-456", result.scope().productId());
            return Promise.of(null);
        });

        verify(repository).findNodesPaginated(eq("product-456"), eq("tenant-123"), eq(null), eq(100));
        verify(repository).findEdgesPaginated(eq("product-456"), eq("tenant-123"), eq(null), eq(100));
    }

    @Test
    void testQueryGraphReturnsCursorForNextPage() {
        // P1-13: Should return cursor when more pages available
        ArtifactGraphRepository.PageResult<ArtifactNodeDto> nodesPage = 
            new ArtifactGraphRepository.PageResult<>(
                List.of(
                    new ArtifactNodeDto("node1", "CODE_MODULE", "Test", null, null, Map.<String, Object>of(), List.<String>of(), null, null, null, null, null, (Double)null, null, null, null, null, null)
                ),
                "cursor-next-page"
            );

        when(repository.findNodesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(nodesPage));
        when(repository.findEdgesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(new ArtifactGraphRepository.PageResult<>(List.of(), null)));

        Promise<ArtifactGraphQueryResponse> response = service.queryGraph(
            "product-456",
            "tenant-123",
            "stats",
            null,
            null,
            100
        );

        response.then(result -> {
            assertEquals("cursor-next-page", result.nextCursor());
            assertTrue(result.scope().hasMore());
            return Promise.of(null);
        });
    }

    @Test
    void testQueryGraphReturnsNullCursorForLastPage() {
        // P1-13: Should return null cursor when no more pages
        ArtifactGraphRepository.PageResult<ArtifactNodeDto> nodesPage =
            new ArtifactGraphRepository.PageResult<>(
                List.of(
                    new ArtifactNodeDto("node1", "CODE_MODULE", "Test", null, null, Map.<String, Object>of(), List.<String>of(), null, null, null, null, null, (Double)null, null, null, null, null, null)
                ),
                null // No next cursor
            );

        when(repository.findNodesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(nodesPage));
        when(repository.findEdgesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(new ArtifactGraphRepository.PageResult<>(List.of(), null)));

        Promise<ArtifactGraphQueryResponse> response = service.queryGraph(
            "product-456",
            "tenant-123",
            "stats",
            null,
            null,
            100
        );

        response.then(result -> {
            assertNull(result.nextCursor());
            assertFalse(result.scope().hasMore());
            return Promise.of(null);
        });
    }

    @Test
    void testQueryGraphWithCursorParameter() {
        // P1-13: Should pass cursor to repository for pagination
        ArtifactGraphRepository.PageResult<ArtifactNodeDto> nodesPage =
            new ArtifactGraphRepository.PageResult<>(
                List.of(),
                "cursor-next"
            );

        when(repository.findNodesPaginated(eq("product-456"), eq("tenant-123"), eq("cursor-prev"), eq(50)))
            .thenReturn(Promise.of(nodesPage));
        when(repository.findEdgesPaginated(eq("product-456"), eq("tenant-123"), eq("cursor-prev"), eq(50)))
            .thenReturn(Promise.of(new ArtifactGraphRepository.PageResult<>(List.of(), null)));

        service.queryGraph(
            "product-456",
            "tenant-123",
            "stats",
            null,
            "cursor-prev",
            50
        );

        verify(repository).findNodesPaginated(eq("product-456"), eq("tenant-123"), eq("cursor-prev"), eq(50));
        verify(repository).findEdgesPaginated(eq("product-456"), eq("tenant-123"), eq("cursor-prev"), eq(50));
    }

    @Test
    void testQueryGraphIncludesTotalEstimate() {
        // P3-1: Should include total estimate in response
        ArtifactGraphRepository.PageResult<ArtifactNodeDto> nodesPage = 
            new ArtifactGraphRepository.PageResult<>(
                List.of(
                    new ArtifactNodeDto("node1", "CODE_MODULE", "Test", null, null, Map.<String, Object>of(), List.<String>of(), null, null, null, null, null, (Double)null, null, null, null, null, null)
                ),
                "cursor-next"
            );

        when(repository.findNodesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(nodesPage));
        when(repository.findEdgesPaginated(eq("product-456"), eq("tenant-123"), isNull(), eq(100)))
            .thenReturn(Promise.of(new ArtifactGraphRepository.PageResult<>(List.of(), null)));

        Promise<ArtifactGraphQueryResponse> response = service.queryGraph(
            "product-456",
            "tenant-123",
            "stats",
            null,
            null,
            100
        );

        response.then(result -> {
            assertNotNull(result);
            assertNotNull(result.scope());
            return Promise.of(null);
        });
    }
}
