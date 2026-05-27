package com.ghatana.yappc.kernelvisibility;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataCloudKernelLifecycleTruthSource}.
 *
 * @doc.type class
 * @doc.purpose Verifies Data Cloud kernel_lifecycle_truth records are typed and fail explicitly when malformed
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DataCloudKernelLifecycleTruthSource")
class DataCloudKernelLifecycleTruthSourceTest extends EventloopTestBase {

    @Mock private DataCloudClient dataCloudClient;

    @Test
    @DisplayName("malformed truth record returns explicit degraded error state")
    void malformedTruthRecordReturnsDegradedErrorState() {
        when(dataCloudClient.findById(eq("tenant-1"), eq("kernel_lifecycle_truth"), eq("product-1")))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of(
                        "product-1",
                        "kernel_lifecycle_truth",
                        Map.of("status", 42)))));

        DataCloudKernelLifecycleTruthSource truthSource =
                new DataCloudKernelLifecycleTruthSource(dataCloudClient, "tenant-1");

        Map<String, Object> record = runPromise(() -> truthSource.getProductUnitLifecycleData("product-1"));

        assertThat(record.get("status")).isEqualTo("error");
        assertThat(record.get("degraded")).isEqualTo(true);
        assertThat(record.get("degradedReason")).isEqualTo("MALFORMED_KERNEL_LIFECYCLE_TRUTH");
        assertThat(record.get("truthSource")).isEqualTo("data-cloud");
    }

    @Test
    @DisplayName("typed truth record preserves known sections and extension metadata")
    void typedTruthRecordPreservesKnownSectionsAndExtensionMetadata() {
        DataCloudKernelLifecycleTruthSource.KernelLifecycleTruthRecord record =
                DataCloudKernelLifecycleTruthSource.KernelLifecycleTruthRecord.from(
                        "product-1",
                        Map.of(
                                "productUnitId", "product-1",
                                "status", "healthy",
                                "healthSnapshot", Map.of("status", "healthy", "lastChecked", "2026-05-27T10:15:30Z", "probe", "kernel"),
                                "gates", Map.of("failedCount", 1, "totalCount", 6, "lastGateId", "deploy"),
                                "artifacts", Map.of("status", "ready", "artifactCount", 3, "bundle", "phase-packet"),
                                "deployment", Map.of("status", "deployed", "environment", "production", "target", "cluster-a"),
                                "lifecycleResult", Map.of("currentPhase", "deploy"),
                                "sourceVersion", "v2"));

        assertThat(record.healthSnapshot()).isNotNull();
        assertThat(record.healthSnapshot().status()).isEqualTo("healthy");
        assertThat(record.gates()).isNotNull();
        assertThat(record.gates().failedCount()).isEqualTo(1);
        assertThat(record.artifacts()).isNotNull();
        assertThat(record.artifacts().artifactCount()).isEqualTo(3);
        assertThat(record.deployment()).isNotNull();
        assertThat(record.deployment().environment()).isEqualTo("production");
        assertThat(record.metadata()).containsEntry("sourceVersion", "v2");

        Map<String, Object> emitted = record.toMap();
        assertThat(emitted).containsEntry("productUnitId", "product-1");
        assertThat(emitted).containsEntry("truthSource", "data-cloud");
        assertThat(emitted).containsEntry("sourceVersion", "v2");
        assertThat(asStringObjectMap(emitted.get("healthSnapshot"))).containsEntry("status", "healthy");
        assertThat(asStringObjectMap(emitted.get("gates"))).containsEntry("failedCount", 1);
        assertThat(asStringObjectMap(emitted.get("artifacts"))).containsEntry("artifactCount", 3);
        assertThat(asStringObjectMap(emitted.get("deployment"))).containsEntry("environment", "production");
    }

    @Test
    @DisplayName("typed truth record rejects malformed known sections")
    void typedTruthRecordRejectsMalformedKnownSections() {
        assertThatThrownBy(() -> DataCloudKernelLifecycleTruthSource.KernelLifecycleTruthRecord.from(
                "product-1",
                Map.of(
                        "productUnitId", "product-1",
                        "status", "healthy",
                        "gates", Map.of("failedCount", "one"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failedCount must be numeric");
    }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asStringObjectMap(Object value) {
                assertThat(value).isInstanceOf(Map.class);
                return (Map<String, Object>) value;
        }
}
