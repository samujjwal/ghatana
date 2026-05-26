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
}
