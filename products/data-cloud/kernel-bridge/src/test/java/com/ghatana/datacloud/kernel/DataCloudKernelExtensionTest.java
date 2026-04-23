package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl;
import com.ghatana.kernel.adapter.datacloud.DataResult;
import com.ghatana.kernel.adapter.datacloud.DatasetInfo;
import com.ghatana.kernel.adapter.datacloud.SchemaInfo;
import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DataCloudKernelExtension}.
 *
 * @doc.type class
 * @doc.purpose Verify that the bridge extension registers the adapter and exposes correct metadata
 * @doc.layer adapter
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("DataCloudKernelExtension")
class DataCloudKernelExtensionTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    @Mock
    private KernelModule hostModule;

    private DataCloudKernelAdapterImpl.DataCloudClient stubClient;
    private DataCloudKernelExtension extension;

    @BeforeEach
    void setUp() { // GH-90000
        stubClient = new StubDataCloudClient(); // GH-90000
        extension = new DataCloudKernelExtension(stubClient); // GH-90000
    }

    // ==================== Identity ====================

    @Test
    @DisplayName("extension ID is 'data-cloud-kernel-bridge'")
    void extensionIdIsCorrect() { // GH-90000
        assertThat(extension.getExtensionId()).isEqualTo("data-cloud-kernel-bridge");
    }

    @Test
    @DisplayName("extension name is human-readable")
    void extensionNameIsHumanReadable() { // GH-90000
        assertThat(extension.getName()).isEqualTo("Data-Cloud Kernel Bridge");
    }

    @Test
    @DisplayName("extension version follows semver")
    void extensionVersionIsSemver() { // GH-90000
        assertThat(extension.getVersion()).matches("\\d+\\.\\d+\\.\\d+.*");
    }

    // ==================== Descriptor ====================

    @Test
    @DisplayName("descriptor type is EXTENSION")
    void descriptorTypeIsExtension() { // GH-90000
        KernelDescriptor descriptor = extension.getDescriptor(); // GH-90000
        assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.EXTENSION); // GH-90000
    }

    @Test
    @DisplayName("descriptor ID matches extension ID")
    void descriptorIdMatchesExtensionId() { // GH-90000
        assertThat(extension.getDescriptor().getDescriptorId()).isEqualTo(extension.getExtensionId()); // GH-90000
    }

    // ==================== Capabilities ====================

    @Test
    @DisplayName("contributes three Data-Cloud capabilities")
    void contributesThreeCapabilities() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps).hasSize(3); // GH-90000
    }

    @Test
    @DisplayName("contributes data-cloud.storage capability")
    void contributesStorageCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps) // GH-90000
            .anyMatch(c -> c.getCapabilityId().equals("data-cloud.storage"));
    }

    @Test
    @DisplayName("contributes data-cloud.transactions capability")
    void contributesTransactionCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps) // GH-90000
            .anyMatch(c -> c.getCapabilityId().equals("data-cloud.transactions"));
    }

    @Test
    @DisplayName("contributes data-cloud.streaming capability")
    void contributesStreamingCapability() { // GH-90000
        Set<KernelCapability> caps = extension.getContributedCapabilities(); // GH-90000
        assertThat(caps) // GH-90000
            .anyMatch(c -> c.getCapabilityId().equals("data-cloud.streaming"));
    }

    // ==================== Compatibility ====================

    @Test
    @DisplayName("is compatible with any non-null module")
    void isCompatibleWithAnyModule() { // GH-90000
        assertThat(extension.isCompatible(hostModule)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("is not compatible with null module")
    void isNotCompatibleWithNullModule() { // GH-90000
        assertThat(extension.isCompatible(null)).isFalse(); // GH-90000
    }

    // ==================== Lifecycle ====================

    @Test
    @DisplayName("onModuleInitialized registers DataCloudKernelAdapter into context")
    void onModuleInitializedRegistersAdapter() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000

        verify(context).registerService(eq(DataCloudKernelAdapter.class), any(DataCloudKernelAdapter.class)); // GH-90000
    }

    @Test
    @DisplayName("onModuleInitialized is idempotent — second call is no-op")
    void onModuleInitializedIsIdempotent() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000
        extension.onModuleInitialized(context);  // second call must be silent no-op // GH-90000

        // registerService called exactly once (AbstractKernelExtension CAS guard) // GH-90000
        verify(context).registerService(eq(DataCloudKernelAdapter.class), any(DataCloudKernelAdapter.class)); // GH-90000
    }

    @Test
    @DisplayName("full lifecycle runs without error")
    void fullLifecycleRunsWithoutError() { // GH-90000
        extension.onModuleInitialized(context); // GH-90000
        extension.onModuleStarted(context); // GH-90000
        extension.onModuleStopped(context); // GH-90000
    }

    // ==================== Construction guard ====================

    @Test
    @DisplayName("null client is rejected at construction")
    void nullClientIsRejected() { // GH-90000
        assertThatThrownBy(() -> new DataCloudKernelExtension(null)) // GH-90000
            .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ==================== Helpers ====================

    /** Minimal stub that satisfies all CompletableFuture-returning methods. */
    private static class StubDataCloudClient implements DataCloudKernelAdapterImpl.DataCloudClient {

        @Override
        public CompletableFuture<DataResult> read(String datasetId, String recordId, Map<String, String> options) { // GH-90000
            return CompletableFuture.completedFuture( // GH-90000
                new DataResult(recordId, new byte[0], Map.of(), Instant.now().toEpochMilli())); // GH-90000
        }

        @Override
        public CompletableFuture<Void> write(String datasetId, String recordId, byte[] data, Map<String, String> metadata) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<Void> delete(String datasetId, String recordId) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<List<DataResult>> query(String datasetId, String query, Map<String, Object> params, int limit, int offset) { // GH-90000
            return CompletableFuture.completedFuture(List.of()); // GH-90000
        }

        @Override
        public CompletableFuture<Void> createDataset(String datasetId, Map<String, String> schema, Map<String, String> options) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<SchemaInfo> getSchema(String datasetId) { // GH-90000
            return CompletableFuture.completedFuture( // GH-90000
                new SchemaInfo(datasetId, Map.of(), Instant.now().toEpochMilli(), Instant.now().toEpochMilli())); // GH-90000
        }

        @Override
        public CompletableFuture<List<DatasetInfo>> listDatasets() { // GH-90000
            return CompletableFuture.completedFuture(List.of()); // GH-90000
        }

        @Override
        public CompletableFuture<Object> beginTransaction() { // GH-90000
            return CompletableFuture.completedFuture("tx-stub");
        }

        @Override
        public CompletableFuture<Void> commitTransaction(Object transaction) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<Void> rollbackTransaction(Object transaction) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<Object> openReadStream(String datasetId, Map<String, String> options) { // GH-90000
            return CompletableFuture.completedFuture("read-stream-stub");
        }

        @Override
        public CompletableFuture<Object> openWriteStream(String datasetId, Map<String, String> options) { // GH-90000
            return CompletableFuture.completedFuture("write-stream-stub");
        }

        @Override
        public CompletableFuture<byte[]> readStreamChunk(Object stream) { // GH-90000
            return CompletableFuture.completedFuture(new byte[0]); // GH-90000
        }

        @Override
        public CompletableFuture<Void> writeStreamChunk(Object stream, byte[] data) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }

        @Override
        public CompletableFuture<Void> closeStream(Object stream) { // GH-90000
            return CompletableFuture.completedFuture(null); // GH-90000
        }
    }
}
