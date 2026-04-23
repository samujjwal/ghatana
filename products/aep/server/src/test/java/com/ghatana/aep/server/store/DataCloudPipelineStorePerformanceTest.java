package com.ghatana.aep.server.store;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("DataCloudPipelineStore Performance")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudPipelineStorePerformanceTest extends EventloopTestBase {

    private static final TenantId TENANT = TenantId.of("tenant-beta");

    @Mock
    private DataCloudClient client;

    private DataCloudPipelineStore store;

    @BeforeEach
    void setUp() { // GH-90000
        store = new DataCloudPipelineStore(client); // GH-90000
    }

    @Test
    @DisplayName("save completes within the latency budget")
    void saveCompletesWithinLatencyBudget() { // GH-90000
        when(client.save(eq("tenant-beta"), eq(DataCloudPipelineStore.COLLECTION), any()))
            .thenAnswer(invocation -> { // GH-90000
                @SuppressWarnings("unchecked")
                Map<String, Object> data = invocation.getArgument(2, Map.class); // GH-90000
                return Promise.of(new DataCloudClient.Entity( // GH-90000
                    data.get("id").toString(),
                    DataCloudPipelineStore.COLLECTION,
                    data,
                    Instant.parse(data.get("createdAt").toString()),
                    Instant.parse(data.get("updatedAt").toString()),
                    1L
                ));
            });

        Pipeline persisted = runPromise(() -> store.save(pipeline("pipeline-0", "Pipeline 0"))); // GH-90000
        long medianMillis = medianMillis(() -> runPromise(() -> store.save(pipeline("pipeline-0", "Pipeline 0"))), 5); // GH-90000

        assertThat(persisted.getId()).isEqualTo("pipeline-0");
        assertThat(medianMillis).isLessThan(50L); // GH-90000
    }

    @Test
    @DisplayName("findAll returns 100 pipelines within the latency budget")
    void findAllReturnsHundredPipelinesWithinLatencyBudget() { // GH-90000
        List<DataCloudClient.Entity> entities = IntStream.range(0, 100) // GH-90000
            .mapToObj(index -> entityFrom(pipeline("pipeline-" + index, "Pipeline " + index))) // GH-90000
            .toList(); // GH-90000
        when(client.query(eq("tenant-beta"), eq(DataCloudPipelineStore.COLLECTION), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(entities)); // GH-90000

        Page<Pipeline> page = runPromise(() -> store.findAll(TENANT, null, null, 1, 100)); // GH-90000
        long medianMillis = medianMillis(() -> runPromise(() -> store.findAll(TENANT, null, null, 1, 100)), 5); // GH-90000

        assertThat(page.content()).hasSize(100); // GH-90000
        assertThat(medianMillis).isLessThan(100L); // GH-90000
    }

    private long medianMillis(Supplier<?> operation, int iterations) { // GH-90000
        operation.get(); // GH-90000
        long[] timings = new long[iterations];
        for (int index = 0; index < iterations; index++) { // GH-90000
            long startedAt = System.nanoTime(); // GH-90000
            operation.get(); // GH-90000
            timings[index] = (System.nanoTime() - startedAt) / 1_000_000L; // GH-90000
        }
        java.util.Arrays.sort(timings); // GH-90000
        return timings[iterations / 2];
    }

    private Pipeline pipeline(String id, String name) { // GH-90000
        Pipeline pipeline = new Pipeline(); // GH-90000
        pipeline.setId(id); // GH-90000
        pipeline.setTenantId(TENANT); // GH-90000
        pipeline.setName(name); // GH-90000
        pipeline.setDescription("Performance regression pipeline");
        pipeline.setVersion(1); // GH-90000
        pipeline.setActive(true); // GH-90000
        pipeline.setConfig("{}");
        pipeline.setCreatedAt(Instant.parse("2026-04-17T00:00:00Z"));
        pipeline.setUpdatedAt(Instant.parse("2026-04-17T00:00:00Z"));
        pipeline.setCreatedBy("perf-test");
        pipeline.setUpdatedBy("perf-test");
        return pipeline;
    }

    private DataCloudClient.Entity entityFrom(Pipeline pipeline) { // GH-90000
        Map<String, Object> data = new LinkedHashMap<>(); // GH-90000
        data.put("id", pipeline.getId()); // GH-90000
        data.put("tenantId", pipeline.getTenantId().value()); // GH-90000
        data.put("name", pipeline.getName()); // GH-90000
        data.put("description", pipeline.getDescription()); // GH-90000
        data.put("version", pipeline.getVersion()); // GH-90000
        data.put("active", pipeline.isActive()); // GH-90000
        data.put("config", pipeline.getConfig()); // GH-90000
        data.put("createdAt", pipeline.getCreatedAt().toString()); // GH-90000
        data.put("updatedAt", pipeline.getUpdatedAt().toString()); // GH-90000
        data.put("createdBy", pipeline.getCreatedBy()); // GH-90000
        data.put("updatedBy", pipeline.getUpdatedBy()); // GH-90000
        return new DataCloudClient.Entity( // GH-90000
            pipeline.getId(), // GH-90000
            DataCloudPipelineStore.COLLECTION,
            data,
            pipeline.getCreatedAt(), // GH-90000
            pipeline.getUpdatedAt(), // GH-90000
            1L
        );
    }
}