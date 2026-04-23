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
    void setUp() { // GH-90000
        dashboardRepository = new InMemoryDashboardRepository(); // GH-90000
        scanJobRepository = new InMemoryScanJobRepository(); // GH-90000
        incidentRepository = new InMemoryIncidentRepository(); // GH-90000
        cloudAccountRepository = new InMemoryCloudAccountRepository(); // GH-90000
        securityAlertRepository = new InMemorySecurityAlertRepository(); // GH-90000
    }

    @Nested
    @DisplayName("Dashboard Repository Tests")
    class DashboardRepositoryTests {

        @Test
        @DisplayName("should save and retrieve dashboard")
        void shouldSaveAndRetrieveDashboard() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            Dashboard dashboard = Dashboard.of(workspaceId, "My Dashboard"); // GH-90000

            // When
            Dashboard saved = dashboardRepository.save(dashboard); // GH-90000
            Optional<Dashboard> retrieved = dashboardRepository.findById(saved.getId()); // GH-90000

            // Then
            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getName()).isEqualTo("My Dashboard");
            assertThat(retrieved.get().getWorkspaceId()).isEqualTo(workspaceId); // GH-90000
        }

        @Test
        @DisplayName("should find dashboards by workspace id")
        void shouldFindDashboardsByWorkspaceId() { // GH-90000
            // Given
            UUID workspaceId1 = UUID.randomUUID(); // GH-90000
            UUID workspaceId2 = UUID.randomUUID(); // GH-90000

            dashboardRepository.save(Dashboard.of(workspaceId1, "Dashboard 1")); // GH-90000
            dashboardRepository.save(Dashboard.of(workspaceId1, "Dashboard 2")); // GH-90000
            dashboardRepository.save(Dashboard.of(workspaceId2, "Dashboard 3")); // GH-90000

            // When
            List<Dashboard> workspace1Dashboards = dashboardRepository.findByWorkspaceId(workspaceId1); // GH-90000

            // Then
            assertThat(workspace1Dashboards).hasSize(2); // GH-90000
            assertThat(workspace1Dashboards).extracting(Dashboard::getName) // GH-90000
                    .containsExactlyInAnyOrder("Dashboard 1", "Dashboard 2"); // GH-90000
        }

        @Test
        @DisplayName("should find dashboard by workspace and name")
        void shouldFindDashboardByWorkspaceAndName() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            Dashboard dashboard = Dashboard.of(workspaceId, "Unique Dashboard"); // GH-90000
            dashboardRepository.save(dashboard); // GH-90000

            // When
            Optional<Dashboard> found = dashboardRepository.findByWorkspaceIdAndName(workspaceId, "Unique Dashboard"); // GH-90000
            Optional<Dashboard> notFound = dashboardRepository.findByWorkspaceIdAndName(workspaceId, "Non-existent"); // GH-90000

            // Then
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getName()).isEqualTo("Unique Dashboard");
            assertThat(notFound).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should delete dashboard")
        void shouldDeleteDashboard() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            Dashboard dashboard = Dashboard.of(workspaceId, "To Be Deleted"); // GH-90000
            Dashboard saved = dashboardRepository.save(dashboard); // GH-90000

            // When
            dashboardRepository.deleteById(saved.getId()); // GH-90000

            // Then
            assertThat(dashboardRepository.findById(saved.getId())).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("ScanJob Repository Tests")
    class ScanJobRepositoryTests {

        @Test
        @DisplayName("should save and retrieve scan job")
        void shouldSaveAndRetrieveScanJob() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000
            ScanJob scanJob = ScanJob.pending(workspaceId, projectId, ScanType.SAST); // GH-90000

            // When
            ScanJob saved = scanJobRepository.save(scanJob); // GH-90000
            Optional<ScanJob> retrieved = scanJobRepository.findById(saved.getId()); // GH-90000

            // Then
            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getStatus()).isEqualTo(ScanStatus.PENDING); // GH-90000
            assertThat(retrieved.get().getScanType()).isEqualTo(ScanType.SAST); // GH-90000
        }

        @Test
        @DisplayName("should find scan jobs by status")
        void shouldFindScanJobsByStatus() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000

            ScanJob pending = ScanJob.pending(workspaceId, projectId, ScanType.SAST); // GH-90000
            ScanJob running = ScanJob.pending(workspaceId, projectId, ScanType.DAST); // GH-90000
            running.start(); // GH-90000
            ScanJob completed = ScanJob.pending(workspaceId, projectId, ScanType.SCA); // GH-90000
            completed.start(); // GH-90000
            completed.complete(); // GH-90000

            scanJobRepository.save(pending); // GH-90000
            scanJobRepository.save(running); // GH-90000
            scanJobRepository.save(completed); // GH-90000

            // When
            List<ScanJob> pendingJobs = scanJobRepository.findByStatus(ScanStatus.PENDING); // GH-90000
            List<ScanJob> runningJobs = scanJobRepository.findByStatus(ScanStatus.RUNNING); // GH-90000

            // Then
            assertThat(pendingJobs).hasSize(1); // GH-90000
            assertThat(runningJobs).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should find scan jobs by project id")
        void shouldFindScanJobsByProjectId() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId1 = UUID.randomUUID(); // GH-90000
            UUID projectId2 = UUID.randomUUID(); // GH-90000

            scanJobRepository.save(ScanJob.pending(workspaceId, projectId1, ScanType.SAST)); // GH-90000
            scanJobRepository.save(ScanJob.pending(workspaceId, projectId1, ScanType.DAST)); // GH-90000
            scanJobRepository.save(ScanJob.pending(workspaceId, projectId2, ScanType.SCA)); // GH-90000

            // When
            List<ScanJob> project1Jobs = scanJobRepository.findByProjectId(projectId1); // GH-90000

            // Then
            assertThat(project1Jobs).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("should update scan job status")
        void shouldUpdateScanJobStatus() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            UUID projectId = UUID.randomUUID(); // GH-90000
            ScanJob scanJob = ScanJob.pending(workspaceId, projectId, ScanType.SAST); // GH-90000
            ScanJob saved = scanJobRepository.save(scanJob); // GH-90000

            // When
            saved.start(); // GH-90000
            scanJobRepository.save(saved); // GH-90000
            Optional<ScanJob> updated = scanJobRepository.findById(saved.getId()); // GH-90000

            // Then
            assertThat(updated).isPresent(); // GH-90000
            assertThat(updated.get().getStatus()).isEqualTo(ScanStatus.RUNNING); // GH-90000
            assertThat(updated.get().getStartedAt()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Incident Repository Tests")
    class IncidentRepositoryTests {

        @Test
        @DisplayName("should save and retrieve incident")
        void shouldSaveAndRetrieveIncident() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            Incident incident = Incident.of(workspaceId, "Security Breach", "CRITICAL"); // GH-90000

            // When
            Incident saved = incidentRepository.save(incident); // GH-90000
            Optional<Incident> retrieved = incidentRepository.findById(saved.getId()); // GH-90000

            // Then
            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getTitle()).isEqualTo("Security Breach");
            assertThat(retrieved.get().getSeverity()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("should find open incidents by workspace")
        void shouldFindOpenIncidentsByWorkspace() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            Incident open1 = Incident.of(workspaceId, "Open Incident 1", "HIGH"); // GH-90000
            Incident open2 = Incident.of(workspaceId, "Open Incident 2", "MEDIUM"); // GH-90000
            Incident resolved = Incident.of(workspaceId, "Resolved Incident", "LOW"); // GH-90000
            resolved.resolve("Fixed");

            incidentRepository.save(open1); // GH-90000
            incidentRepository.save(open2); // GH-90000
            incidentRepository.save(resolved); // GH-90000

            // When
            List<Incident> openIncidents = incidentRepository.findOpenByWorkspaceId(workspaceId); // GH-90000

            // Then
            assertThat(openIncidents).hasSize(2); // GH-90000
            assertThat(openIncidents).allMatch(Incident::isOpen); // GH-90000
        }

        @Test
        @DisplayName("should find incidents by severity")
        void shouldFindIncidentsBySeverity() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            incidentRepository.save(Incident.of(workspaceId, "Critical 1", "CRITICAL")); // GH-90000
            incidentRepository.save(Incident.of(workspaceId, "Critical 2", "CRITICAL")); // GH-90000
            incidentRepository.save(Incident.of(workspaceId, "High", "HIGH")); // GH-90000
            incidentRepository.save(Incident.of(workspaceId, "Medium", "MEDIUM")); // GH-90000

            // When
            List<Incident> criticalIncidents = incidentRepository.findBySeverity("CRITICAL");

            // Then
            assertThat(criticalIncidents).hasSize(2); // GH-90000
        }
    }

    @Nested
    @DisplayName("CloudAccount Repository Tests")
    class CloudAccountRepositoryTests {

        @Test
        @DisplayName("should save and retrieve cloud account")
        void shouldSaveAndRetrieveCloudAccount() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            CloudAccount account = CloudAccount.of(workspaceId, CloudProvider.AWS, "123456789012", "Production AWS"); // GH-90000

            // When
            CloudAccount saved = cloudAccountRepository.save(account); // GH-90000
            Optional<CloudAccount> retrieved = cloudAccountRepository.findById(saved.getId()); // GH-90000

            // Then
            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getName()).isEqualTo("Production AWS");
            assertThat(retrieved.get().getProvider()).isEqualTo(CloudProvider.AWS); // GH-90000
        }

        @Test
        @DisplayName("should find cloud accounts by provider")
        void shouldFindCloudAccountsByProvider() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.AWS, "111111111111", "AWS 1")); // GH-90000
            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.AWS, "222222222222", "AWS 2")); // GH-90000
            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.GCP, "gcp-project-1", "GCP 1")); // GH-90000
            cloudAccountRepository.save(CloudAccount.of(workspaceId, CloudProvider.AZURE, "azure-sub-1", "Azure 1")); // GH-90000

            // When
            List<CloudAccount> awsAccounts = cloudAccountRepository.findByProvider(CloudProvider.AWS); // GH-90000

            // Then
            assertThat(awsAccounts).hasSize(2); // GH-90000
            assertThat(awsAccounts).allMatch(a -> a.getProvider() == CloudProvider.AWS); // GH-90000
        }

        @Test
        @DisplayName("should find enabled cloud accounts")
        void shouldFindEnabledCloudAccounts() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            CloudAccount enabled = CloudAccount.of(workspaceId, CloudProvider.AWS, "111111111111", "Enabled"); // GH-90000
            CloudAccount disabled = CloudAccount.of(workspaceId, CloudProvider.AWS, "222222222222", "Disabled"); // GH-90000
            disabled.disable(); // GH-90000

            cloudAccountRepository.save(enabled); // GH-90000
            cloudAccountRepository.save(disabled); // GH-90000

            // When
            List<CloudAccount> enabledAccounts = cloudAccountRepository.findEnabled(workspaceId); // GH-90000

            // Then
            assertThat(enabledAccounts).hasSize(1); // GH-90000
            assertThat(enabledAccounts.get(0).getName()).isEqualTo("Enabled");
        }
    }

    @Nested
    @DisplayName("SecurityAlert Repository Tests")
    class SecurityAlertRepositoryTests {

        @Test
        @DisplayName("should save and retrieve security alert")
        void shouldSaveAndRetrieveSecurityAlert() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000
            // Signature: of(workspaceId, alertType, severity, title) // GH-90000
            SecurityAlert alert = SecurityAlert.of(workspaceId, "IDS", "HIGH", "Suspicious Activity"); // GH-90000

            // When
            SecurityAlert saved = securityAlertRepository.save(alert); // GH-90000
            Optional<SecurityAlert> retrieved = securityAlertRepository.findById(saved.getId()); // GH-90000

            // Then
            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getTitle()).isEqualTo("Suspicious Activity");
            assertThat(retrieved.get().getSeverity()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("should find unacknowledged alerts")
        void shouldFindUnacknowledgedAlerts() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            // Signature: of(workspaceId, alertType, severity, title) // GH-90000
            SecurityAlert open1 = SecurityAlert.of(workspaceId, "IDS", "HIGH", "Open 1"); // GH-90000
            SecurityAlert open2 = SecurityAlert.of(workspaceId, "WAF", "MEDIUM", "Open 2"); // GH-90000
            SecurityAlert acked = SecurityAlert.of(workspaceId, "SIEM", "LOW", "Acknowledged"); // GH-90000
            acked.acknowledge(UUID.randomUUID()); // GH-90000

            securityAlertRepository.save(open1); // GH-90000
            securityAlertRepository.save(open2); // GH-90000
            securityAlertRepository.save(acked); // GH-90000

            // When
            List<SecurityAlert> openAlerts = securityAlertRepository.findOpenByWorkspaceId(workspaceId); // GH-90000

            // Then
            assertThat(openAlerts).hasSize(2); // GH-90000
            assertThat(openAlerts).allMatch(SecurityAlert::isOpen); // GH-90000
        }

        @Test
        @DisplayName("should count alerts by severity")
        void shouldCountAlertsBySeverity() { // GH-90000
            // Given
            UUID workspaceId = UUID.randomUUID(); // GH-90000

            // Signature: of(workspaceId, alertType, severity, title) // GH-90000
            securityAlertRepository.save(SecurityAlert.of(workspaceId, "IDS", "CRITICAL", "Critical 1")); // GH-90000
            securityAlertRepository.save(SecurityAlert.of(workspaceId, "WAF", "CRITICAL", "Critical 2")); // GH-90000
            securityAlertRepository.save(SecurityAlert.of(workspaceId, "SIEM", "HIGH", "High")); // GH-90000

            // When
            long criticalCount = securityAlertRepository.countBySeverity(workspaceId, "CRITICAL"); // GH-90000
            long highCount = securityAlertRepository.countBySeverity(workspaceId, "HIGH"); // GH-90000

            // Then
            assertThat(criticalCount).isEqualTo(2); // GH-90000
            assertThat(highCount).isEqualTo(1); // GH-90000
        }
    }

    // =======================
    // In-Memory Repository Implementations
    // =======================

    /**
     * In-memory implementation of Dashboard repository for testing.
     */
    static class InMemoryDashboardRepository {
        private final Map<UUID, Dashboard> store = new ConcurrentHashMap<>(); // GH-90000

        public Dashboard save(Dashboard dashboard) { // GH-90000
            if (dashboard.getId() == null) { // GH-90000
                // Simulate ID generation
                Dashboard withId = dashboard.toBuilder() // GH-90000
                        .id(UUID.randomUUID()) // GH-90000
                        .build(); // GH-90000
                store.put(withId.getId(), withId); // GH-90000
                return withId;
            }
            store.put(dashboard.getId(), dashboard); // GH-90000
            return dashboard;
        }

        public Optional<Dashboard> findById(UUID id) { // GH-90000
            return Optional.ofNullable(store.get(id)); // GH-90000
        }

        public List<Dashboard> findByWorkspaceId(UUID workspaceId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(d -> d.getWorkspaceId().equals(workspaceId)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        public Optional<Dashboard> findByWorkspaceIdAndName(UUID workspaceId, String name) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(d -> d.getWorkspaceId().equals(workspaceId) && d.getName().equals(name)) // GH-90000
                    .findFirst(); // GH-90000
        }

        public void deleteById(UUID id) { // GH-90000
            store.remove(id); // GH-90000
        }
    }

    /**
     * In-memory implementation of ScanJob repository for testing.
     */
    static class InMemoryScanJobRepository {
        private final Map<UUID, ScanJob> store = new ConcurrentHashMap<>(); // GH-90000

        public ScanJob save(ScanJob scanJob) { // GH-90000
            if (scanJob.getId() == null) { // GH-90000
                ScanJob withId = scanJob.toBuilder() // GH-90000
                        .id(UUID.randomUUID()) // GH-90000
                        .build(); // GH-90000
                store.put(withId.getId(), withId); // GH-90000
                return withId;
            }
            store.put(scanJob.getId(), scanJob); // GH-90000
            return scanJob;
        }

        public Optional<ScanJob> findById(UUID id) { // GH-90000
            return Optional.ofNullable(store.get(id)); // GH-90000
        }

        public List<ScanJob> findByStatus(ScanStatus status) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(j -> j.getStatus() == status) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        public List<ScanJob> findByProjectId(UUID projectId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(j -> j.getProjectId().equals(projectId)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }
    }

    /**
     * In-memory implementation of Incident repository for testing.
     */
    static class InMemoryIncidentRepository {
        private final Map<UUID, Incident> store = new ConcurrentHashMap<>(); // GH-90000

        public Incident save(Incident incident) { // GH-90000
            if (incident.getId() == null) { // GH-90000
                Incident withId = incident.toBuilder() // GH-90000
                        .id(UUID.randomUUID()) // GH-90000
                        .build(); // GH-90000
                store.put(withId.getId(), withId); // GH-90000
                return withId;
            }
            store.put(incident.getId(), incident); // GH-90000
            return incident;
        }

        public Optional<Incident> findById(UUID id) { // GH-90000
            return Optional.ofNullable(store.get(id)); // GH-90000
        }

        public List<Incident> findOpenByWorkspaceId(UUID workspaceId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(i -> i.getWorkspaceId().equals(workspaceId) && i.isOpen()) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        public List<Incident> findBySeverity(String severity) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(i -> i.getSeverity().equals(severity)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }
    }

    /**
     * In-memory implementation of CloudAccount repository for testing.
     */
    static class InMemoryCloudAccountRepository {
        private final Map<UUID, CloudAccount> store = new ConcurrentHashMap<>(); // GH-90000

        public CloudAccount save(CloudAccount account) { // GH-90000
            if (account.getId() == null) { // GH-90000
                CloudAccount withId = account.toBuilder() // GH-90000
                        .id(UUID.randomUUID()) // GH-90000
                        .build(); // GH-90000
                store.put(withId.getId(), withId); // GH-90000
                return withId;
            }
            store.put(account.getId(), account); // GH-90000
            return account;
        }

        public Optional<CloudAccount> findById(UUID id) { // GH-90000
            return Optional.ofNullable(store.get(id)); // GH-90000
        }

        public List<CloudAccount> findByProvider(CloudProvider provider) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(a -> a.getProvider() == provider) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        public List<CloudAccount> findEnabled(UUID workspaceId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(a -> a.getWorkspaceId().equals(workspaceId) && a.isEnabled()) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }
    }

    /**
     * In-memory implementation of SecurityAlert repository for testing.
     */
    static class InMemorySecurityAlertRepository {
        private final Map<UUID, SecurityAlert> store = new ConcurrentHashMap<>(); // GH-90000

        public SecurityAlert save(SecurityAlert alert) { // GH-90000
            if (alert.getId() == null) { // GH-90000
                SecurityAlert withId = alert.toBuilder() // GH-90000
                        .id(UUID.randomUUID()) // GH-90000
                        .build(); // GH-90000
                store.put(withId.getId(), withId); // GH-90000
                return withId;
            }
            store.put(alert.getId(), alert); // GH-90000
            return alert;
        }

        public Optional<SecurityAlert> findById(UUID id) { // GH-90000
            return Optional.ofNullable(store.get(id)); // GH-90000
        }

        public List<SecurityAlert> findOpenByWorkspaceId(UUID workspaceId) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(a -> a.getWorkspaceId().equals(workspaceId) && a.isOpen()) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        public long countBySeverity(UUID workspaceId, String severity) { // GH-90000
            return store.values().stream() // GH-90000
                    .filter(a -> a.getWorkspaceId().equals(workspaceId) && a.getSeverity().equals(severity)) // GH-90000
                    .count(); // GH-90000
        }
    }
}
