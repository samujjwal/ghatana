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
@ExtendWith(MockitoExtension.class) 
class DataCloudPipelineStorePerformanceTest extends EventloopTestBase {

    private static final TenantId TENANT = TenantId.of("tenant-beta");

    @Mock
    private DataCloudClient client;

    private DataCloudPipelineStore store;

    @BeforeEach
    void setUp() { 
        store = new DataCloudPipelineStore(client); 
    }

    @Test
    @DisplayName("save completes within the latency budget")
    void saveCompletesWithinLatencyBudget() { 
        when(client.save(eq("tenant-beta"), eq(DataCloudPipelineStore.COLLECTION), any()))
            .thenAnswer(invocation -> { 
                @SuppressWarnings("unchecked")
                Map<String, Object> data = invocation.getArgument(2, Map.class); 
                return Promise.of(new DataCloudClient.Entity( 
                    data.get("id").toString(),
                    DataCloudPipelineStore.COLLECTION,
                    data,
                    Instant.parse(data.get("createdAt").toString()),
                    Instant.parse(data.get("updatedAt").toString()),
                    1L
                ));
            });

        Pipeline persisted = runPromise(() -> store.save(pipeline("pipeline-0", "Pipeline 0"))); 
        long medianMillis = medianMillis(() -> runPromise(() -> store.save(pipeline("pipeline-0", "Pipeline 0"))), 5); 

        assertThat(persisted.getId()).isEqualTo("pipeline-0");
        assertThat(medianMillis).isLessThan(50L); 
    }

    @Test
    @DisplayName("findAll returns 100 pipelines within the latency budget")
    void findAllReturnsHundredPipelinesWithinLatencyBudget() { 
        List<DataCloudClient.Entity> entities = IntStream.range(0, 100) 
            .mapToObj(index -> entityFrom(pipeline("pipeline-" + index, "Pipeline " + index))) 
            .toList(); 
        when(client.query(eq("tenant-beta"), eq(DataCloudPipelineStore.COLLECTION), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(entities)); 

        Page<Pipeline> page = runPromise(() -> store.findAll(TENANT, null, null, 1, 100)); 
        long medianMillis = medianMillis(() -> runPromise(() -> store.findAll(TENANT, null, null, 1, 100)), 5); 

        assertThat(page.content()).hasSize(100); 
        assertThat(medianMillis).isLessThan(100L); 
    }

    private long medianMillis(Supplier<?> operation, int iterations) { 
        operation.get(); 
        long[] timings = new long[iterations];
        for (int index = 0; index < iterations; index++) { 
            long startedAt = System.nanoTime(); 
            operation.get(); 
            timings[index] = (System.nanoTime() - startedAt) / 1_000_000L; 
        }
        java.util.Arrays.sort(timings); 
        return timings[iterations / 2];
    }

    private Pipeline pipeline(String id, String name) { 
        Pipeline pipeline = new Pipeline(); 
        pipeline.setId(id); 
        pipeline.setTenantId(TENANT); 
        pipeline.setName(name); 
        pipeline.setDescription("Performance regression pipeline");
        pipeline.setVersion(1); 
        pipeline.setActive(true); 
        pipeline.setConfig("{}");
        pipeline.setCreatedAt(Instant.parse("2026-04-17T00:00:00Z"));
        pipeline.setUpdatedAt(Instant.parse("2026-04-17T00:00:00Z"));
        pipeline.setCreatedBy("perf-test");
        pipeline.setUpdatedBy("perf-test");
        return pipeline;
    }

    private DataCloudClient.Entity entityFrom(Pipeline pipeline) { 
        Map<String, Object> data = new LinkedHashMap<>(); 
        data.put("id", pipeline.getId()); 
        data.put("tenantId", pipeline.getTenantId().value()); 
        data.put("name", pipeline.getName()); 
        data.put("description", pipeline.getDescription()); 
        data.put("version", pipeline.getVersion()); 
        data.put("active", pipeline.isActive()); 
        data.put("config", pipeline.getConfig()); 
        data.put("createdAt", pipeline.getCreatedAt().toString()); 
        data.put("updatedAt", pipeline.getUpdatedAt().toString()); 
        data.put("createdBy", pipeline.getCreatedBy()); 
        data.put("updatedBy", pipeline.getUpdatedBy()); 
        return new DataCloudClient.Entity( 
            pipeline.getId(), 
            DataCloudPipelineStore.COLLECTION,
            data,
            pipeline.getCreatedAt(), 
            pipeline.getUpdatedAt(), 
            1L
        );
    }
}