package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

import com.ghatana.datacloud.plugins.iceberg.TierMigrationScheduler;
import com.ghatana.datacloud.plugins.s3archive.ArchiveMigrationScheduler;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * @doc.type class
 * @doc.purpose Regression tests for TierMigrationHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TierMigrationHandler")
@ExtendWith(MockitoExtension.class) 
class TierMigrationHandlerTest extends EventloopTestBase {

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private TierMigrationScheduler tierMigrationScheduler;

    @Mock
    private ArchiveMigrationScheduler archiveMigrationScheduler;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private TierMigrationHandler handler;

    @BeforeEach
    void setUp() { 
        handler = new TierMigrationHandler(http, tierMigrationScheduler, archiveMigrationScheduler); 
        when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("migration rejects missing tenant before scheduler access")
    void migrationRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleMigrateCollection(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(tierMigrationScheduler, never()).triggerMigration("default", "default"); 
        verify(archiveMigrationScheduler, never()).runMigrationCycle(); 
    }
}