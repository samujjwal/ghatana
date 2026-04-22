package com.ghatana.datacloud.launcher.http.handlers;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for TierMigrationHandler tenant enforcement
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TierMigrationHandler [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        handler = new TierMigrationHandler(http, tierMigrationScheduler, archiveMigrationScheduler); // GH-90000
        when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("migration rejects missing tenant before scheduler access [GH-90000]")
    void migrationRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleMigrateCollection(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(tierMigrationScheduler, never()).triggerMigration("default", "default"); // GH-90000
        verify(archiveMigrationScheduler, never()).runMigrationCycle(); // GH-90000
    }
}