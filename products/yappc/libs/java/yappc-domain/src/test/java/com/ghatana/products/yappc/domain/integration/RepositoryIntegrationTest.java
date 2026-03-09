package com.ghatana.products.yappc.domain.integration;

import com.ghatana.products.yappc.domain.model.Dashboard;
import com.ghatana.products.yappc.domain.model.ScanJob;
import com.ghatana.products.yappc.domain.model.Incident;
import com.ghatana.products.yappc.domain.model.CloudAccount;
import com.ghatana.products.yappc.domain.model.SecurityAlert;
import com.ghatana.products.yappc.domain.enums.CloudProvider;
import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.ghatana.platform.testing.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for yappc-domain repository operations.
 *
 * <p>These tests verify that domain entities can be persisted and retrieved
 * correctly using in-memory repositories that simulate database behavior.</p>
 *
 * @doc.type class
 * @doc.purpose Tests repository operations with in-memory implementations
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@IntegrationTest
@DisplayName("YAPPC Domain Repository Integration Tests")
class RepositoryIntegrationTest {

    /**
     * In-memory repository for Dashboard entities.
     */
    private InMemoryDashboardRepository dashboardRepository;

    /**
     * In-memory repository for ScanJob entities.
     */
    private InMemoryScanJobRepository scanJobRepository;

    /**
     * In-memory repository for Incident entities.
     */
    private InMemoryIncidentRepository incidentRepository;

    /**
     * In-memory repository for CloudAccount entities.
     */
    private InMemoryCloudAccountRepository cloudAccountRepository;

    /**
     * In-memory repository for SecurityAlert entities.
     */
    private InMemorySecurityAlertRepository securityAlertRepository;

    @BeforeEach
    void setUp() {
        dashboardRepository = new InMemoryDashboardRepository();
        scanJobRepository = new InMemoryScanJobRepository();
        incidentRepository = new InMemoryIncidentRepository();
        cloudAccountRepository = new InMemoryCloudAccountRepository();
        securityAlertRepository = new InMemorySecurityAlertRepository();
    }

    @Nested
    @DisplayName("Dashboard Repository Tests")
    class DashboardRepositoryTests {

        @Test
        @DisplayName("should save and retrieve dashboard")
        void shouldSaveAndRetrieveDashboard() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            Dashboard dashboard = Dashboard.of(workspaceId, "My Dashboard");

            // When
            Dashboard saved = dashboardRepository.save(dashboard);
            Optional<Dashboard> retrieved = dashboardRepository.findById(saved.getId());

            // Then
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getName()).isEqualTo("My Dashboard");
            assertThat(retrieved.get().getWorkspaceId()).isEqualTo(workspaceId);
        }

        @Test
        @DisplayName("should find dashboards by workspace id")
        void shouldFindDashboardsByWorkspaceId() {
            // Given
            UUID workspaceId1 = UUID.randomUUID();
            UUID workspaceId2 = UUID.randomUUID();

            dashboardRepository.save(Dashboard.of(workspaceId1, "Dashboard 1"));
            dashboardRepository.save(Dashboard.of(workspaceId1, "Dashboard 2"));
            dashboardRepository.save(Dashboard.of(workspaceId2, "Dashboard 3"));

            // When
            List<Dashboard> workspace1Dashboards = dashboardRepository.findByWorkspaceId(workspaceId1);

            // Then
            assertThat(workspace1Dashboards).hasSize(2);
            assertThat(workspace1Dashboards).extracting(Dashboard::getName)
                    .containsExactlyInAnyOrder("Dashboard 1", "Dashboard 2");
        }

        @Test
        @DisplayName("should find dashboard by workspace and name")
        void shouldFindDashboardByWorkspaceAndName() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            Dashboard dashboard = Dashboard.of(workspaceId, "Unique Dashboard");
            dashboardRepository.save(dashboard);

            // When
            Optional<Dashboard> found = dashboardRepository.findByWorkspaceIdAndName(workspaceId, "Unique Dashboard");
            Optional<Dashboard> notFound = dashboardRepository.findByWorkspaceIdAndName(workspaceId, "Non-existent");

            // Then
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Unique Dashboard");
            assertThat(notFound).isEmpty();
        }

        @Test
        @DisplayName("should delete dashboard")
        void shouldDeleteDashboard() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            Dashboard dashboard = Dashboard.of(workspaceId, "To Be Deleted");
            Dashboard saved = dashboardRepository.save(dashboard);

            // When
            dashboardRepository.deleteById(saved.getId());

            // Then
            assertThat(dashboardRepository.findById(saved.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("ScanJob Repository Tests")
    class ScanJobRepositoryTests {

        @Test
        @DisplayName("should save and retrieve scan job")
        void shouldSaveAndRetrieveScanJob() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            ScanJob scanJob = ScanJob.pending(workspaceId, projectId, ScanType.SAST);

            // When
            ScanJob saved = scanJobRepository.save(scanJob);
            Optional<ScanJob> retrieved = scanJobRepository.findById(saved.getId());

            // Then
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getStatus()).isEqualTo(ScanStatus.PENDING);
            assertThat(retrieved.get().getScanType()).isEqualTo(ScanType.SAST);
        }

        @Test
        @DisplayName("should find scan jobs by status")
        void shouldFindScanJobsByStatus() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();

            ScanJob pending = ScanJob.pending(workspaceId, projectId, ScanType.SAST);
            ScanJob running = ScanJob.pending(workspaceId, projectId, ScanType.DAST);
            running.start();
            ScanJob completed = ScanJob.pending(workspaceId, projectId, ScanType.SCA);
            completed.start();
            completed.complete();

            scanJobRepository.save(pending);
            scanJobRepository.save(running);
            scanJobRepository.save(completed);

            // When
            List<ScanJob> pendingJobs = scanJobRepository.findByStatus(ScanStatus.PENDING);
            List<ScanJob> runningJobs = scanJobRepository.findByStatus(ScanStatus.RUNNING);

            // Then
            assertThat(pendingJobs).hasSize(1);
            assertThat(runningJobs).hasSize(1);
        }

        @Test
        @DisplayName("should find scan jobs by project id")
        void shouldFindScanJobsByProjectId() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            UUID projectId1 = UUID.randomUUID();
            UUID projectId2 = UUID.randomUUID();

            scanJobRepository.save(ScanJob.pending(workspaceId, projectId1, ScanType.SAST));
            scanJobRepository.save(ScanJob.pending(workspaceId, projectId1, ScanType.DAST));
            scanJobRepository.save(ScanJob.pending(workspaceId, projectId2, ScanType.SCA));

            // When
            List<ScanJob> project1Jobs = scanJobRepository.findByProjectId(projectId1);

            // Then
            assertThat(project1Jobs).hasSize(2);
        }

        @Test
        @DisplayName("should update scan job status")
        void shouldUpdateScanJobStatus() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            ScanJob scanJob = ScanJob.pending(workspaceId, projectId, ScanType.SAST);
            ScanJob saved = scanJobRepository.save(scanJob);

            // When
            saved.start();
            scanJobRepository.save(saved);
            Optional<ScanJob> updated = scanJobRepository.findById(saved.getId());

            // Then
            assertThat(updated).isPresent();
            assertThat(updated.get().getStatus()).isEqualTo(ScanStatus.RUNNING);
            assertThat(updated.get().getStartedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Incident Repository Tests")
    class IncidentRepositoryTests {

        @Test
        @DisplayName("should save and retrieve incident")
        void shouldSaveAndRetrieveIncident() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            Incident incident = Incident.of(workspaceId, "Security Breach", "CRITICAL");

            // When
            Incident saved = incidentRepository.save(incident);
            Optional<Incident> retrieved = incidentRepository.findById(saved.getId());

            // Then
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getTitle()).isEqualTo("Security Breach");
            assertThat(retrieved.get().getSeverity()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("should find open incidents by workspace")
        void shouldFindOpenIncidentsByWorkspace() {
            // Given
            UUID workspaceId = UUID.randomUUID();

            Incident open1 = Incident.of(workspaceId, "Open Incident 1", "HIGH");
            Incident open2 = Incident.of(workspaceId, "Open Incident 2", "MEDIUM");
            Incident resolved = Incident.of(workspaceId, "Resolved Incident", "LOW");
            resolved.resolve("Fixed");

            incidentRepository.save(open1);
            incidentRepository.save(open2);
            incidentRepository.save(resolved);

            // When
            List<Incident> openIncidents = incidentRepository.findOpenByWorkspaceId(workspaceId);

            // Then
            assertThat(openIncidents).hasSize(2);
            assertThat(openIncidents).allMatch(Incident::isOpen);
        }

        @Test
        @DisplayName("should find incidents by severity")
        void shouldFindIncidentsBySeverity() {
            // Given
            UUID workspaceId = UUID.randomUUID();

            incidentRepository.save(Incident.of(workspaceId, "Critical 1", "CRITICAL"));
            incidentRepository.save(Incident.of(workspaceId, "Critical 2", "CRITICAL"));
            incidentRepository.save(Incident.of(workspaceId, "High", "HIGH"));
            incidentRepository.save(Incident.of(workspaceId, "Medium", "MEDIUM"));

            // When
            List<Incident> criticalIncidents = incidentRepository.findBySeverity("CRITICAL");

            // Then
            assertThat(criticalIncidents).hasSize(2);
        }
    }

    @Nested
    @DisplayName("CloudAccount Repository Tests")
    class CloudAccountRepositoryTests {

        @Test
        @DisplayName("should save and retrieve cloud account")
        void shouldSaveAndRetrieveCloudAccount() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            CloudAccount account = CloudAccount.of(workspaceId, CloudProvider.AWS, "123456789012", "Production AWS");

            // When
            CloudAccount saved = cloudAccountRepository.save(account);
            Optional<CloudAccount> retrieved = cloudAccountRepository.findById(saved.getId());

            // Then
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getName()).isEqualTo("Production AWS");
            assertThat(retrieved.get().getProvider()).isEqualTo(CloudProvider.AWS);
        }

        @Test
        @DisplayName("should find cloud accounts by provider")
        void shouldFindCloudAccountsByProvider() {
            // Given
            UUID workspaceId = UUID.randomUUID();

            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.AWS, "111111111111", "AWS 1"));
            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.AWS, "222222222222", "AWS 2"));
            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.GCP, "gcp-project-1", "GCP 1"));
            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.AZURE, "azure-sub-1", "Azure 1"));

            // When
            List<CloudAccount> awsAccounts = cloudAccountRepository.findByProvider(CloudProvider.AWS);

            // Then
            assertThat(awsAccounts).hasSize(2);
            assertThat(awsAccounts).allMatch(a -> a.getProvider() == CloudProvider.AWS);
        }

        @Test
        @DisplayName("should find enabled cloud accounts")
        void shouldFindEnabledCloudAccounts() {
            // Given
            UUID workspaceId = UUID.randomUUID();

            CloudAccount enabled = CloudAccount.of(workspaceId, CloudProvider.AWS, "111111111111", "Enabled");
            CloudAccount disabled = CloudAccount.of(workspaceId, CloudProvider.AWS, "222222222222", "Disabled");
            disabled.disable();

            cloudAccountRepository.save(enabled);
            cloudAccountRepository.save(disabled);

            // When
            List<CloudAccount> enabledAccounts = cloudAccountRepository.findEnabled(workspaceId);

            // Then
            assertThat(enabledAccounts).hasSize(1);
            assertThat(enabledAccounts.get(0).getName()).isEqualTo("Enabled");
        }
    }

    @Nested
    @DisplayName("SecurityAlert Repository Tests")
    class SecurityAlertRepositoryTests {

        @Test
        @DisplayName("should save and retrieve security alert")
        void shouldSaveAndRetrieveSecurityAlert() {
            // Given
            UUID workspaceId = UUID.randomUUID();
            // Signature: of(workspaceId, alertType, severity, title)
            SecurityAlert alert = SecurityAlert.of(workspaceId, "IDS", "HIGH", "Suspicious Activity");

            // When
            SecurityAlert saved = securityAlertRepository.save(alert);
            Optional<SecurityAlert> retrieved = securityAlertRepository.findById(saved.getId());

            // Then
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getTitle()).isEqualTo("Suspicious Activity");
            assertThat(retrieved.get().getSeverity()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("should find unacknowledged alerts")
        void shouldFindUnacknowledgedAlerts() {
            // Given
            UUID workspaceId = UUID.randomUUID();

            // Signature: of(workspaceId, alertType, severity, title)
            SecurityAlert open1 = SecurityAlert.of(workspaceId, "IDS", "HIGH", "Open 1");
            SecurityAlert open2 = SecurityAlert.of(workspaceId, "WAF", "MEDIUM", "Open 2");
            SecurityAlert acked = SecurityAlert.of(workspaceId, "SIEM", "LOW", "Acknowledged");
            acked.acknowledge(UUID.randomUUID());

            securityAlertRepository.save(open1);
            securityAlertRepository.save(open2);
            securityAlertRepository.save(acked);

            // When
            List<SecurityAlert> openAlerts = securityAlertRepository.findOpenByWorkspaceId(workspaceId);

            // Then
            assertThat(openAlerts).hasSize(2);
            assertThat(openAlerts).allMatch(SecurityAlert::isOpen);
        }

        @Test
        @DisplayName("should count alerts by severity")
        void shouldCountAlertsBySeverity() {
            // Given
            UUID workspaceId = UUID.randomUUID();

            // Signature: of(workspaceId, alertType, severity, title)
            securityAlertRepository.save(SecurityAlert.of(workspaceId, "IDS", "CRITICAL", "Critical 1"));
            securityAlertRepository.save(SecurityAlert.of(workspaceId, "WAF", "CRITICAL", "Critical 2"));
            securityAlertRepository.save(SecurityAlert.of(workspaceId, "SIEM", "HIGH", "High"));

            // When
            long criticalCount = securityAlertRepository.countBySeverity(workspaceId, "CRITICAL");
            long highCount = securityAlertRepository.countBySeverity(workspaceId, "HIGH");

            // Then
            assertThat(criticalCount).isEqualTo(2);
            assertThat(highCount).isEqualTo(1);
        }
    }

    // =======================
    // In-Memory Repository Implementations
    // =======================

    /**
     * In-memory implementation of Dashboard repository for testing.
     */
    static class InMemoryDashboardRepository {
        private final Map<UUID, Dashboard> store = new ConcurrentHashMap<>();

        public Dashboard save(Dashboard dashboard) {
            if (dashboard.getId() == null) {
                // Simulate ID generation
                Dashboard withId = dashboard.toBuilder()
                        .id(UUID.randomUUID())
                        .build();
                store.put(withId.getId(), withId);
                return withId;
            }
            store.put(dashboard.getId(), dashboard);
            return dashboard;
        }

        public Optional<Dashboard> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        public List<Dashboard> findByWorkspaceId(UUID workspaceId) {
            return store.values().stream()
                    .filter(d -> d.getWorkspaceId().equals(workspaceId))
                    .collect(Collectors.toList());
        }

        public Optional<Dashboard> findByWorkspaceIdAndName(UUID workspaceId, String name) {
            return store.values().stream()
                    .filter(d -> d.getWorkspaceId().equals(workspaceId) && d.getName().equals(name))
                    .findFirst();
        }

        public void deleteById(UUID id) {
            store.remove(id);
        }
    }

    /**
     * In-memory implementation of ScanJob repository for testing.
     */
    static class InMemoryScanJobRepository {
        private final Map<UUID, ScanJob> store = new ConcurrentHashMap<>();

        public ScanJob save(ScanJob scanJob) {
            if (scanJob.getId() == null) {
                ScanJob withId = scanJob.toBuilder()
                        .id(UUID.randomUUID())
                        .build();
                store.put(withId.getId(), withId);
                return withId;
            }
            store.put(scanJob.getId(), scanJob);
            return scanJob;
        }

        public Optional<ScanJob> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        public List<ScanJob> findByStatus(ScanStatus status) {
            return store.values().stream()
                    .filter(j -> j.getStatus() == status)
                    .collect(Collectors.toList());
        }

        public List<ScanJob> findByProjectId(UUID projectId) {
            return store.values().stream()
                    .filter(j -> j.getProjectId().equals(projectId))
                    .collect(Collectors.toList());
        }
    }

    /**
     * In-memory implementation of Incident repository for testing.
     */
    static class InMemoryIncidentRepository {
        private final Map<UUID, Incident> store = new ConcurrentHashMap<>();

        public Incident save(Incident incident) {
            if (incident.getId() == null) {
                Incident withId = incident.toBuilder()
                        .id(UUID.randomUUID())
                        .build();
                store.put(withId.getId(), withId);
                return withId;
            }
            store.put(incident.getId(), incident);
            return incident;
        }

        public Optional<Incident> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        public List<Incident> findOpenByWorkspaceId(UUID workspaceId) {
            return store.values().stream()
                    .filter(i -> i.getWorkspaceId().equals(workspaceId) && i.isOpen())
                    .collect(Collectors.toList());
        }

        public List<Incident> findBySeverity(String severity) {
            return store.values().stream()
                    .filter(i -> i.getSeverity().equals(severity))
                    .collect(Collectors.toList());
        }
    }

    /**
     * In-memory implementation of CloudAccount repository for testing.
     */
    static class InMemoryCloudAccountRepository {
        private final Map<UUID, CloudAccount> store = new ConcurrentHashMap<>();

        public CloudAccount save(CloudAccount account) {
            if (account.getId() == null) {
                CloudAccount withId = account.toBuilder()
                        .id(UUID.randomUUID())
                        .build();
                store.put(withId.getId(), withId);
                return withId;
            }
            store.put(account.getId(), account);
            return account;
        }

        public Optional<CloudAccount> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        public List<CloudAccount> findByProvider(CloudProvider provider) {
            return store.values().stream()
                    .filter(a -> a.getProvider() == provider)
                    .collect(Collectors.toList());
        }

        public List<CloudAccount> findEnabled(UUID workspaceId) {
            return store.values().stream()
                    .filter(a -> a.getWorkspaceId().equals(workspaceId) && a.isEnabled())
                    .collect(Collectors.toList());
        }
    }

    /**
     * In-memory implementation of SecurityAlert repository for testing.
     */
    static class InMemorySecurityAlertRepository {
        private final Map<UUID, SecurityAlert> store = new ConcurrentHashMap<>();

        public SecurityAlert save(SecurityAlert alert) {
            if (alert.getId() == null) {
                SecurityAlert withId = alert.toBuilder()
                        .id(UUID.randomUUID())
                        .build();
                store.put(withId.getId(), withId);
                return withId;
            }
            store.put(alert.getId(), alert);
            return alert;
        }

        public Optional<SecurityAlert> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        public List<SecurityAlert> findOpenByWorkspaceId(UUID workspaceId) {
            return store.values().stream()
                    .filter(a -> a.getWorkspaceId().equals(workspaceId) && a.isOpen())
                    .collect(Collectors.toList());
        }

        public long countBySeverity(UUID workspaceId, String severity) {
            return store.values().stream()
                    .filter(a -> a.getWorkspaceId().equals(workspaceId) && a.getSeverity().equals(severity))
                    .count();
        }
    }
}
