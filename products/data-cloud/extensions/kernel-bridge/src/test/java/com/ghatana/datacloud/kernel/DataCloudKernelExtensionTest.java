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
@ExtendWith(MockitoExtension.class) 
@DisplayName("DataCloudKernelExtension")
class DataCloudKernelExtensionTest extends EventloopTestBase {

    @Mock
    private KernelContext context;

    @Mock
    private KernelModule hostModule;

    private DataCloudKernelAdapterImpl.DataCloudClient stubClient;
    private DataCloudKernelExtension extension;

    @BeforeEach
    void setUp() { 
        stubClient = new StubDataCloudClient(); 
        extension = new DataCloudKernelExtension(stubClient); 
    }

    // ==================== Identity ====================

    @Test
    @DisplayName("extension ID is 'data-cloud-kernel-bridge'")
    void extensionIdIsCorrect() { 
        assertThat(extension.getExtensionId()).isEqualTo("data-cloud-kernel-bridge");
    }

    @Test
    @DisplayName("extension name is human-readable")
    void extensionNameIsHumanReadable() { 
        assertThat(extension.getName()).isEqualTo("Data-Cloud Kernel Bridge");
    }

    @Test
    @DisplayName("extension version follows semver")
    void extensionVersionIsSemver() { 
        assertThat(extension.getVersion()).matches("\\d+\\.\\d+\\.\\d+.*");
    }

    // ==================== Descriptor ====================

    @Test
    @DisplayName("descriptor type is EXTENSION")
    void descriptorTypeIsExtension() { 
        KernelDescriptor descriptor = extension.getDescriptor(); 
        assertThat(descriptor.getType()).isEqualTo(KernelDescriptor.DescriptorType.EXTENSION); 
    }

    @Test
    @DisplayName("descriptor ID matches extension ID")
    void descriptorIdMatchesExtensionId() { 
        assertThat(extension.getDescriptor().getDescriptorId()).isEqualTo(extension.getExtensionId()); 
    }

    // ==================== Capabilities ====================

    @Test
    @DisplayName("contributes three Data-Cloud capabilities")
    void contributesThreeCapabilities() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps).hasSize(3); 
    }

    @Test
    @DisplayName("contributes data-cloud.storage capability")
    void contributesStorageCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps) 
            .anyMatch(c -> c.getCapabilityId().equals("data-cloud.storage"));
    }

    @Test
    @DisplayName("contributes data-cloud.transactions capability")
    void contributesTransactionCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps) 
            .anyMatch(c -> c.getCapabilityId().equals("data-cloud.transactions"));
    }

    @Test
    @DisplayName("contributes data-cloud.streaming capability")
    void contributesStreamingCapability() { 
        Set<KernelCapability> caps = extension.getContributedCapabilities(); 
        assertThat(caps) 
            .anyMatch(c -> c.getCapabilityId().equals("data-cloud.streaming"));
    }

    // ==================== Compatibility ====================

    @Test
    @DisplayName("is compatible with any non-null module")
    void isCompatibleWithAnyModule() { 
        assertThat(extension.isCompatible(hostModule)).isTrue(); 
    }

    @Test
    @DisplayName("is not compatible with null module")
    void isNotCompatibleWithNullModule() { 
        assertThat(extension.isCompatible(null)).isFalse(); 
    }

    // ==================== Lifecycle ====================

    @Test
    @DisplayName("onModuleInitialized registers DataCloudKernelAdapter into context")
    void onModuleInitializedRegistersAdapter() { 
        extension.onModuleInitialized(context); 

        verify(context).registerService(eq(DataCloudKernelAdapter.class), any(DataCloudKernelAdapter.class)); 
    }

    @Test
    @DisplayName("onModuleInitialized is idempotent — second call is no-op")
    void onModuleInitializedIsIdempotent() { 
        extension.onModuleInitialized(context); 
        extension.onModuleInitialized(context);  // second call must be silent no-op 

        // registerService called exactly once (AbstractKernelExtension CAS guard) 
        verify(context).registerService(eq(DataCloudKernelAdapter.class), any(DataCloudKernelAdapter.class)); 
    }

    @Test
    @DisplayName("full lifecycle runs without error")
    void fullLifecycleRunsWithoutError() { 
        extension.onModuleInitialized(context); 
        extension.onModuleStarted(context); 
        extension.onModuleStopped(context); 
    }

    // ==================== Construction guard ====================

    @Test
    @DisplayName("null client is rejected at construction")
    void nullClientIsRejected() { 
        assertThatThrownBy(() -> new DataCloudKernelExtension(null)) 
            .isInstanceOf(NullPointerException.class); 
    }

    // ==================== Helpers ====================

    /** Minimal stub that satisfies all CompletableFuture-returning methods. */
    private static class StubDataCloudClient implements DataCloudKernelAdapterImpl.DataCloudClient {

        @Override
        public CompletableFuture<DataResult> read(String datasetId, String recordId, Map<String, String> options) { 
            return CompletableFuture.completedFuture( 
                new DataResult(recordId, new byte[0], Map.of(), Instant.now().toEpochMilli())); 
        }

        @Override
        public CompletableFuture<Void> write(String datasetId, String recordId, byte[] data, Map<String, String> metadata) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<Void> delete(String datasetId, String recordId) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<List<DataResult>> query(String datasetId, String query, Map<String, Object> params, int limit, int offset) { 
            return CompletableFuture.completedFuture(List.of()); 
        }

        @Override
        public CompletableFuture<Void> createDataset(String datasetId, Map<String, String> schema, Map<String, String> options) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<SchemaInfo> getSchema(String datasetId) { 
            return CompletableFuture.completedFuture( 
                new SchemaInfo(datasetId, Map.of(), Instant.now().toEpochMilli(), Instant.now().toEpochMilli())); 
        }

        @Override
        public CompletableFuture<List<DatasetInfo>> listDatasets() { 
            return CompletableFuture.completedFuture(List.of()); 
        }

        @Override
        public CompletableFuture<Object> beginTransaction() { 
            return CompletableFuture.completedFuture("tx-stub");
        }

        @Override
        public CompletableFuture<Void> commitTransaction(Object transaction) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<Void> rollbackTransaction(Object transaction) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<Object> openReadStream(String datasetId, Map<String, String> options) { 
            return CompletableFuture.completedFuture("read-stream-stub");
        }

        @Override
        public CompletableFuture<Object> openWriteStream(String datasetId, Map<String, String> options) { 
            return CompletableFuture.completedFuture("write-stream-stub");
        }

        @Override
        public CompletableFuture<byte[]> readStreamChunk(Object stream) { 
            return CompletableFuture.completedFuture(new byte[0]); 
        }

        @Override
        public CompletableFuture<Void> writeStreamChunk(Object stream, byte[] data) { 
            return CompletableFuture.completedFuture(null); 
        }

        @Override
        public CompletableFuture<Void> closeStream(Object stream) { 
            return CompletableFuture.completedFuture(null); 
        }
    }
}
