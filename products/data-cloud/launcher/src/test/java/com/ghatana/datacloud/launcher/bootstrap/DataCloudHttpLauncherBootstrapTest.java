package com.ghatana.datacloud.launcher.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.launcher.DataCloudTransportStartupException;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * @doc.type class
 * @doc.purpose Verifies standalone HTTP bootstrap startup behavior and typed failure wrapping
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudHttpLauncherBootstrap")
class DataCloudHttpLauncherBootstrapTest {

    @Test
    @DisplayName("wires database health subsystem and shutdown hook on successful startup")
    void wiresDatabaseHealthSubsystemAndShutdownHook() throws Exception {
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class);
        DataSource dataSource = mock(DataSource.class);
        Logger log = mock(Logger.class);
        AtomicReference<Thread> registeredHook = new AtomicReference<>();

        when(httpServer.withHealthSubsystem(eq("database"), any())).thenReturn(httpServer);

        DataCloudHttpLauncherBootstrap.startTransport(
                httpServer,
                8082,
                true,
                dataSource,
                log,
                registeredHook::set);

        verify(httpServer).withHealthSubsystem(eq("database"), any());
        verify(httpServer).start();
        assertThat(registeredHook.get()).isNotNull();
    }

    @Test
    @DisplayName("does not wire database health subsystem when database is disabled")
    void skipsDatabaseHealthSubsystemWhenDatabaseDisabled() throws Exception {
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class);
        Logger log = mock(Logger.class);

        DataCloudHttpLauncherBootstrap.startTransport(
                httpServer,
                8082,
                false,
                null,
                log,
                hook -> {});

        verify(httpServer, never()).withHealthSubsystem(eq("database"), any());
        verify(httpServer).start();
    }

    @Test
    @DisplayName("wraps server startup failures in typed transport exception")
    void wrapsServerStartupFailuresInTypedTransportException() throws Exception {
        DataCloudHttpServer httpServer = mock(DataCloudHttpServer.class);
        Logger log = mock(Logger.class);
        doThrow(new IllegalStateException("boom")).when(httpServer).start();

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startTransport(
                httpServer,
                8082,
                false,
                null,
                log,
                hook -> {}))
                .isInstanceOf(DataCloudTransportStartupException.class)
                .hasMessage("Failed to start HTTP server on port 8082")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(log).error(eq("Failed to start HTTP server on port {}"), eq(8082), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("fails fast when required database datasource startup fails")
    void failsFastWhenRequiredDatabaseDatasourceStartupFails() {
        Logger log = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startRequiredDatabaseDataSource(
                log,
                () -> {
                    throw new IllegalStateException("db unavailable");
                }))
                .isInstanceOf(DataCloudTransportStartupException.class)
                .hasMessage("Failed to create standalone database DataSource for enabled DB-backed features")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(log).error(eq("Failed to create standalone database DataSource for enabled DB-backed features"), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("fails fast when required AI services startup fails")
    void failsFastWhenRequiredAiServicesStartupFails() {
        Logger log = mock(Logger.class);

        assertThatThrownBy(() -> DataCloudHttpLauncherBootstrap.startRequiredAiServices(
                log,
                () -> {
                    throw new IllegalStateException("ai unavailable");
                }))
                .isInstanceOf(DataCloudTransportStartupException.class)
                .hasMessage("Failed to start AI services while DATACLOUD_AI_ENABLED=true")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(log).error(eq("Failed to start AI services while DATACLOUD_AI_ENABLED=true"), any(IllegalStateException.class));
    }

    @Test
    @DisplayName("starts brain services and registers a shutdown hook when brain is enabled")
    void startsBrainServicesAndRegistersShutdownHook() {
        DataCloudBrain brain = mock(DataCloudBrain.class);
        DataCloudLearningBridge learningBridge = mock(DataCloudLearningBridge.class);
        Logger log = mock(Logger.class);
        AtomicReference<Thread> registeredHook = new AtomicReference<>();

        DataCloudHttpLauncherBootstrap.BrainServices services = DataCloudHttpLauncherBootstrap.startBrainServices(
                log,
                () -> brain,
                ignoredBrain -> learningBridge,
                registeredHook::set);

        assertThat(services.brain()).isSameAs(brain);
        assertThat(services.learningBridge()).isSameAs(learningBridge);
        verify(learningBridge).start();
        assertThat(registeredHook.get()).isNotNull();
    }

    @Test
    @DisplayName("disables brain services when learning bridge startup fails")
    void disablesBrainServicesWhenStartupFails() {
        DataCloudBrain brain = mock(DataCloudBrain.class);
        DataCloudLearningBridge learningBridge = mock(DataCloudLearningBridge.class);
        Logger log = mock(Logger.class);
        doThrow(new IllegalStateException("bridge failed")).when(learningBridge).start();

        DataCloudHttpLauncherBootstrap.BrainServices services = DataCloudHttpLauncherBootstrap.startBrainServices(
                log,
                () -> brain,
                ignoredBrain -> learningBridge,
                hook -> {});

        assertThat(services.brain()).isNull();
        assertThat(services.learningBridge()).isNull();
        verify(log).warn(
                eq("Failed to start brain/learning bridge, continuing without: {}"),
                eq("bridge failed"),
                any(IllegalStateException.class));
    }

    @Test
    @DisplayName("starts analytics and report services when analytics is enabled")
    void startsAnalyticsAndReportServices() {
        AnalyticsQueryEngine analyticsEngine = mock(AnalyticsQueryEngine.class);
        ReportService reportService = mock(ReportService.class);
        Logger log = mock(Logger.class);

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices(
                log,
                () -> analyticsEngine,
                ignored -> reportService);

        assertThat(services.analyticsEngine()).isSameAs(analyticsEngine);
        assertThat(services.reportService()).isSameAs(reportService);
    }

    @Test
    @DisplayName("keeps analytics engine when report service startup fails")
    void keepsAnalyticsEngineWhenReportStartupFails() {
        AnalyticsQueryEngine analyticsEngine = mock(AnalyticsQueryEngine.class);
        Logger log = mock(Logger.class);

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices(
                log,
                () -> analyticsEngine,
                ignored -> {
                    throw new IllegalStateException("report failed");
                });

        assertThat(services.analyticsEngine()).isSameAs(analyticsEngine);
        assertThat(services.reportService()).isNull();
        verify(log).warn(
                eq("Failed to start report service, continuing without: {}"),
                eq("report failed"),
                any(IllegalStateException.class));
    }

    @Test
    @DisplayName("disables analytics services when analytics engine startup fails")
    void disablesAnalyticsServicesWhenAnalyticsStartupFails() {
        Logger log = mock(Logger.class);

        DataCloudHttpLauncherBootstrap.AnalyticsServices services = DataCloudHttpLauncherBootstrap.startAnalyticsServices(
                log,
                () -> {
                    throw new IllegalStateException("analytics failed");
                },
                ReportService::new);

        assertThat(services.analyticsEngine()).isNull();
        assertThat(services.reportService()).isNull();
        verify(log).warn(
                eq("Failed to start analytics engine, continuing without: {}"),
                eq("analytics failed"),
                any(IllegalStateException.class));
    }
}
