package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Incident} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates Incident entity behavior, lifecycle transitions, and metrics
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Incident Domain Model Tests")
class IncidentTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String TITLE = "Unauthorized Access Detected";
    private static final String SEVERITY = "HIGH";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates incident with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);

            // THEN
            assertThat(incident.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(incident.getTitle()).isEqualTo(TITLE);
            assertThat(incident.getSeverity()).isEqualTo(SEVERITY);
            assertThat(incident.getStatus()).isEqualTo("OPEN");
            assertThat(incident.getPriority()).isEqualTo(3); // Default priority
            assertThat(incident.getDetectedAt()).isNotNull();
            assertThat(incident.getCreatedAt()).isNotNull();
            assertThat(incident.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null")
        void ofThrowsWhenWorkspaceIdNull() {
            assertThatThrownBy(() -> Incident.of(null, TITLE, SEVERITY))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when title is null")
        void ofThrowsWhenTitleNull() {
            assertThatThrownBy(() -> Incident.of(WORKSPACE_ID, null, SEVERITY))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("title must not be null");
        }

        @Test
        @DisplayName("of() throws NullPointerException when severity is null")
        void ofThrowsWhenSeverityNull() {
            assertThatThrownBy(() -> Incident.of(WORKSPACE_ID, TITLE, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("severity must not be null");
        }
    }

    @Nested
    @DisplayName("Lifecycle Transition Tests")
    class LifecycleTransitionTests {

        @Test
        @DisplayName("assignTo() transitions to ASSIGNED and sets assignee")
        void assignToTransitionsToAssigned() {
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            UUID assigneeId = UUID.randomUUID();

            // WHEN
            Incident result = incident.assignTo(assigneeId);

            // THEN
            assertThat(result).isSameAs(incident);
            assertThat(incident.getStatus()).isEqualTo("ASSIGNED");
            assertThat(incident.getAssigneeId()).isEqualTo(assigneeId);
        }

        @Test
        @DisplayName("startInvestigation() transitions to INVESTIGATING")
        void startInvestigationTransitions() {
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            incident.assignTo(UUID.randomUUID());
            Instant beforeInvestigation = Instant.now();

            // WHEN
            Incident result = incident.startInvestigation();

            // THEN
            assertThat(result).isSameAs(incident);
            assertThat(incident.getStatus()).isEqualTo("INVESTIGATING");
            assertThat(incident.getInvestigationStartedAt()).isAfterOrEqualTo(beforeInvestigation);
        }

        @Test
        @DisplayName("resolve() transitions to RESOLVED with resolution notes")
        void resolveTransitionsWithNotes() {
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            incident.assignTo(UUID.randomUUID()).startInvestigation();
            String resolution = "Identified compromised credentials and rotated. No data exfiltration detected.";

            // WHEN
            Incident result = incident.resolve(resolution);

            // THEN
            assertThat(result).isSameAs(incident);
            assertThat(incident.getStatus()).isEqualTo("RESOLVED");
            assertThat(incident.getResolution()).isEqualTo(resolution);
            assertThat(incident.getResolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("close() transitions to CLOSED")
        void closeTransitions() {
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            incident.assignTo(UUID.randomUUID())
                    .startInvestigation()
                    .resolve("Issue remediated");

            // WHEN
            Incident result = incident.close();

            // THEN
            assertThat(result).isSameAs(incident);
            assertThat(incident.getStatus()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("full lifecycle from OPEN to CLOSED")
        void fullLifecycle() {
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            UUID assigneeId = UUID.randomUUID();

            // WHEN
            incident.assignTo(assigneeId)
                    .startInvestigation()
                    .resolve("Remediated")
                    .close();

            // THEN
            assertThat(incident.getStatus()).isEqualTo("CLOSED");
            assertThat(incident.getAssigneeId()).isEqualTo(assigneeId);
            assertThat(incident.getInvestigationStartedAt()).isNotNull();
            assertThat(incident.getResolvedAt()).isNotNull();
            assertThat(incident.getResolution()).isEqualTo("Remediated");
        }
    }

    @Nested
    @DisplayName("isOpen() Tests")
    class IsOpenTests {

        @Test
        @DisplayName("isOpen() returns true for OPEN status")
        void isOpenReturnsTrueForOpen() {
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            assertThat(incident.isOpen()).isTrue();
        }

        @Test
        @DisplayName("isOpen() returns true for ASSIGNED status")
        void isOpenReturnsTrueForAssigned() {
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            incident.assignTo(UUID.randomUUID());
            assertThat(incident.isOpen()).isTrue();
        }

        @Test
        @DisplayName("isOpen() returns false for INVESTIGATING status")
        void isOpenReturnsFalseForInvestigating() {
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            incident.startInvestigation();
            assertThat(incident.isOpen()).isFalse();
        }

        @Test
        @DisplayName("isOpen() returns false for RESOLVED status")
        void isOpenReturnsFalseForResolved() {
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            incident.resolve("Fixed");
            assertThat(incident.isOpen()).isFalse();
        }

        @Test
        @DisplayName("isOpen() returns false for CLOSED status")
        void isOpenReturnsFalseForClosed() {
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            incident.close();
            assertThat(incident.isOpen()).isFalse();
        }
    }

    @Nested
    @DisplayName("Time To Resolution Tests")
    class TimeToResolutionTests {

        @Test
        @DisplayName("getTimeToResolutionMs() returns -1 before resolution")
        void getTimeToResolutionMsReturnsNegativeBeforeResolution() {
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            assertThat(incident.getTimeToResolutionMs()).isEqualTo(-1);
        }

        @Test
        @DisplayName("getTimeToResolutionMs() returns positive value after resolution")
        void getTimeToResolutionMsReturnsPositiveAfterResolution() throws InterruptedException {
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY);
            Thread.sleep(10); // Ensure measurable time
            incident.resolve("Fixed");

            // THEN
            assertThat(incident.getTimeToResolutionMs()).isGreaterThan(0);
        }

        @Test
        @DisplayName("getTimeToResolutionMs() returns -1 if detectedAt is null")
        void getTimeToResolutionMsReturnsNegativeIfDetectedAtNull() {
            Incident incident = Incident.builder()
                    .workspaceId(WORKSPACE_ID)
                    .title(TITLE)
                    .severity(SEVERITY)
                    .resolvedAt(Instant.now())
                    .build();

            assertThat(incident.getTimeToResolutionMs()).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates incident with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            UUID assigneeId = UUID.randomUUID();
            UUID reporterId = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            Incident incident = Incident.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .title(TITLE)
                    .description("Detailed description of the incident")
                    .severity(SEVERITY)
                    .status("INVESTIGATING")
                    .priority(1)
                    .assigneeId(assigneeId)
                    .reporterId(reporterId)
                    .category("UNAUTHORIZED_ACCESS")
                    .rootCause("Compromised credentials")
                    .resolution("Credentials rotated")
                    .detectedAt(now.minusSeconds(3600))
                    .investigationStartedAt(now.minusSeconds(1800))
                    .resolvedAt(now)
                    .createdAt(now.minusSeconds(3600))
                    .updatedAt(now)
                    .version(3)
                    .build();

            // THEN
            assertThat(incident.getId()).isEqualTo(id);
            assertThat(incident.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(incident.getTitle()).isEqualTo(TITLE);
            assertThat(incident.getDescription()).isEqualTo("Detailed description of the incident");
            assertThat(incident.getSeverity()).isEqualTo(SEVERITY);
            assertThat(incident.getStatus()).isEqualTo("INVESTIGATING");
            assertThat(incident.getPriority()).isEqualTo(1);
            assertThat(incident.getAssigneeId()).isEqualTo(assigneeId);
            assertThat(incident.getReporterId()).isEqualTo(reporterId);
            assertThat(incident.getCategory()).isEqualTo("UNAUTHORIZED_ACCESS");
            assertThat(incident.getRootCause()).isEqualTo("Compromised credentials");
            assertThat(incident.getResolution()).isEqualTo("Credentials rotated");
            assertThat(incident.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("builder defaults status to OPEN")
        void builderDefaultsStatusToOpen() {
            Incident incident = Incident.builder()
                    .workspaceId(WORKSPACE_ID)
                    .title(TITLE)
                    .severity(SEVERITY)
                    .build();

            assertThat(incident.getStatus()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("builder defaults priority to 3")
        void builderDefaultsPriorityTo3() {
            Incident incident = Incident.builder()
                    .workspaceId(WORKSPACE_ID)
                    .title(TITLE)
                    .severity(SEVERITY)
                    .build();

            assertThat(incident.getPriority()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            Incident incident1 = Incident.builder().id(id).severity("HIGH").build();
            Incident incident2 = Incident.builder().id(id).severity("LOW").build();

            assertThat(incident1).isEqualTo(incident2);
            assertThat(incident1.hashCode()).isEqualTo(incident2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            Incident incident1 = Incident.builder().id(UUID.randomUUID()).build();
            Incident incident2 = Incident.builder().id(UUID.randomUUID()).build();

            assertThat(incident1).isNotEqualTo(incident2);
        }
    }

    @Nested
    @DisplayName("Severity Tests")
    class SeverityTests {

        @Test
        @DisplayName("can create incident with various severities")
        void canCreateWithVariousSeverities() {
            String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"};

            for (String severity : severities) {
                Incident incident = Incident.of(WORKSPACE_ID, TITLE, severity);
                assertThat(incident.getSeverity()).isEqualTo(severity);
            }
        }
    }

    @Nested
    @DisplayName("Category Tests")
    class CategoryTests {

        @Test
        @DisplayName("can set various incident categories")
        void canSetVariousCategories() {
            String[] categories = {
                    "DATA_BREACH",
                    "MALWARE",
                    "UNAUTHORIZED_ACCESS",
                    "PHISHING",
                    "DOS_ATTACK",
                    "INSIDER_THREAT"
            };

            for (String category : categories) {
                Incident incident = Incident.builder()
                        .workspaceId(WORKSPACE_ID)
                        .title(TITLE)
                        .severity(SEVERITY)
                        .category(category)
                        .build();

                assertThat(incident.getCategory()).isEqualTo(category);
            }
        }
    }
}
