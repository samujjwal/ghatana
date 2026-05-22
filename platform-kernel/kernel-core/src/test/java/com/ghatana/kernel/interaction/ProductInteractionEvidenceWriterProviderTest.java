package com.ghatana.kernel.interaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ProductInteractionEvidenceWriterProvider.
 *
 * @doc.type class
 * @doc.purpose Test evidence writer provider factory functionality
 * @doc.layer kernel
 */
@DisplayName("ProductInteractionEvidenceWriterProvider Tests")
class ProductInteractionEvidenceWriterProviderTest {

    @Test
    @DisplayName("Creates file-backed writer for bootstrap mode")
    void createsFileBackedWriterForBootstrapMode() {
        Path evidenceRoot = Path.of("/tmp/evidence");
        Executor executor = Executors.newSingleThreadExecutor();
        
        ProductInteractionEvidenceWriterProvider.BootstrapConfig config = 
            ProductInteractionEvidenceWriterProvider.BootstrapConfig.builder()
                .evidenceRoot(evidenceRoot)
                .executor(executor)
                .build();
        
        ProductInteractionEvidenceWriter writer = 
            ProductInteractionEvidenceWriterProvider.create(
                ProductInteractionEvidenceWriterProvider.ProviderMode.BOOTSTRAP,
                config,
                null
            );
        
        assertNotNull(writer);
        assertTrue(writer instanceof FileProductInteractionEvidenceWriter);
    }

    @Test
    @DisplayName("Creates Data Cloud-backed writer for platform mode")
    void createsDataCloudBackedWriterForPlatformMode() {
        DataCloudEvidenceClient mockClient = mock(DataCloudEvidenceClient.class);
        Executor executor = Executors.newSingleThreadExecutor();
        
        ProductInteractionEvidenceWriterProvider.PlatformConfig config = 
            ProductInteractionEvidenceWriterProvider.PlatformConfig.builder()
                .dataCloudClient(mockClient)
                .executor(executor)
                .build();
        
        ProductInteractionEvidenceWriter writer = 
            ProductInteractionEvidenceWriterProvider.create(
                ProductInteractionEvidenceWriterProvider.ProviderMode.PLATFORM,
                null,
                config
            );
        
        assertNotNull(writer);
        assertTrue(writer instanceof DataCloudProductInteractionEvidenceWriter);
    }

    @Test
    @DisplayName("Null provider mode throws exception")
    void nullProviderModeThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            ProductInteractionEvidenceWriterProvider.create(null, null, null);
        });
    }

    @Test
    @DisplayName("Null bootstrap config throws exception for bootstrap mode")
    void nullBootstrapConfigThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            ProductInteractionEvidenceWriterProvider.create(
                ProductInteractionEvidenceWriterProvider.ProviderMode.BOOTSTRAP,
                null,
                null
            );
        });
    }

    @Test
    @DisplayName("Null platform config throws exception for platform mode")
    void nullPlatformConfigThrowsException() {
        assertThrows(NullPointerException.class, () -> {
            ProductInteractionEvidenceWriterProvider.create(
                ProductInteractionEvidenceWriterProvider.ProviderMode.PLATFORM,
                null,
                null
            );
        });
    }

    @Test
    @DisplayName("Null evidence root throws exception in bootstrap config builder")
    void nullEvidenceRootThrowsException() {
        Executor executor = Executors.newSingleThreadExecutor();
        
        assertThrows(NullPointerException.class, () -> {
            ProductInteractionEvidenceWriterProvider.BootstrapConfig.builder()
                .evidenceRoot(null)
                .executor(executor)
                .build();
        });
    }

    @Test
    @DisplayName("Null executor throws exception in bootstrap config builder")
    void nullExecutorThrowsException() {
        Path evidenceRoot = Path.of("/tmp/evidence");
        
        assertThrows(NullPointerException.class, () -> {
            ProductInteractionEvidenceWriterProvider.BootstrapConfig.builder()
                .evidenceRoot(evidenceRoot)
                .executor(null)
                .build();
        });
    }

    @Test
    @DisplayName("Null data cloud client throws exception in platform config builder")
    void nullDataCloudClientThrowsException() {
        Executor executor = Executors.newSingleThreadExecutor();
        
        assertThrows(NullPointerException.class, () -> {
            ProductInteractionEvidenceWriterProvider.PlatformConfig.builder()
                .dataCloudClient(null)
                .executor(executor)
                .build();
        });
    }

    @Test
    @DisplayName("Null executor throws exception in platform config builder")
    void nullExecutorThrowsExceptionInPlatformConfig() {
        DataCloudEvidenceClient mockClient = mock(DataCloudEvidenceClient.class);
        
        assertThrows(NullPointerException.class, () -> {
            ProductInteractionEvidenceWriterProvider.PlatformConfig.builder()
                .dataCloudClient(mockClient)
                .executor(null)
                .build();
        });
    }

    @Test
    @DisplayName("Bootstrap config builder returns configured values")
    void bootstrapConfigBuilderReturnsConfiguredValues() {
        Path evidenceRoot = Path.of("/tmp/evidence");
        Executor executor = Executors.newSingleThreadExecutor();
        
        ProductInteractionEvidenceWriterProvider.BootstrapConfig config = 
            ProductInteractionEvidenceWriterProvider.BootstrapConfig.builder()
                .evidenceRoot(evidenceRoot)
                .executor(executor)
                .build();
        
        assertEquals(evidenceRoot, config.evidenceRoot());
        assertEquals(executor, config.executor());
    }

    @Test
    @DisplayName("Platform config builder returns configured values")
    void platformConfigBuilderReturnsConfiguredValues() {
        DataCloudEvidenceClient mockClient = mock(DataCloudEvidenceClient.class);
        Executor executor = Executors.newSingleThreadExecutor();
        
        ProductInteractionEvidenceWriterProvider.PlatformConfig config = 
            ProductInteractionEvidenceWriterProvider.PlatformConfig.builder()
                .dataCloudClient(mockClient)
                .executor(executor)
                .build();
        
        assertEquals(mockClient, config.dataCloudClient());
        assertEquals(executor, config.executor());
    }
}
