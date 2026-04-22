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
@DisplayName("Incident Domain Model Tests [GH-90000]")
class IncidentTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final String TITLE = "Unauthorized Access Detected";
    private static final String SEVERITY = "HIGH";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates incident with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000

            // THEN
            assertThat(incident.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(incident.getTitle()).isEqualTo(TITLE); // GH-90000
            assertThat(incident.getSeverity()).isEqualTo(SEVERITY); // GH-90000
            assertThat(incident.getStatus()).isEqualTo("OPEN [GH-90000]");
            assertThat(incident.getPriority()).isEqualTo(3); // Default priority // GH-90000
            assertThat(incident.getDetectedAt()).isNotNull(); // GH-90000
            assertThat(incident.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(incident.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException when workspaceId is null [GH-90000]")
        void ofThrowsWhenWorkspaceIdNull() { // GH-90000
            assertThatThrownBy(() -> Incident.of(null, TITLE, SEVERITY)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when title is null [GH-90000]")
        void ofThrowsWhenTitleNull() { // GH-90000
            assertThatThrownBy(() -> Incident.of(WORKSPACE_ID, null, SEVERITY)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("title must not be null [GH-90000]");
        }

        @Test
        @DisplayName("of() throws NullPointerException when severity is null [GH-90000]")
        void ofThrowsWhenSeverityNull() { // GH-90000
            assertThatThrownBy(() -> Incident.of(WORKSPACE_ID, TITLE, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("severity must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Lifecycle Transition Tests [GH-90000]")
    class LifecycleTransitionTests {

        @Test
        @DisplayName("assignTo() transitions to ASSIGNED and sets assignee [GH-90000]")
        void assignToTransitionsToAssigned() { // GH-90000
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            UUID assigneeId = UUID.randomUUID(); // GH-90000

            // WHEN
            Incident result = incident.assignTo(assigneeId); // GH-90000

            // THEN
            assertThat(result).isSameAs(incident); // GH-90000
            assertThat(incident.getStatus()).isEqualTo("ASSIGNED [GH-90000]");
            assertThat(incident.getAssigneeId()).isEqualTo(assigneeId); // GH-90000
        }

        @Test
        @DisplayName("startInvestigation() transitions to INVESTIGATING [GH-90000]")
        void startInvestigationTransitions() { // GH-90000
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            incident.assignTo(UUID.randomUUID()); // GH-90000
            Instant beforeInvestigation = Instant.now(); // GH-90000

            // WHEN
            Incident result = incident.startInvestigation(); // GH-90000

            // THEN
            assertThat(result).isSameAs(incident); // GH-90000
            assertThat(incident.getStatus()).isEqualTo("INVESTIGATING [GH-90000]");
            assertThat(incident.getInvestigationStartedAt()).isAfterOrEqualTo(beforeInvestigation); // GH-90000
        }

        @Test
        @DisplayName("resolve() transitions to RESOLVED with resolution notes [GH-90000]")
        void resolveTransitionsWithNotes() { // GH-90000
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            incident.assignTo(UUID.randomUUID()).startInvestigation(); // GH-90000
            String resolution = "Identified compromised credentials and rotated. No data exfiltration detected.";

            // WHEN
            Incident result = incident.resolve(resolution); // GH-90000

            // THEN
            assertThat(result).isSameAs(incident); // GH-90000
            assertThat(incident.getStatus()).isEqualTo("RESOLVED [GH-90000]");
            assertThat(incident.getResolution()).isEqualTo(resolution); // GH-90000
            assertThat(incident.getResolvedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("close() transitions to CLOSED [GH-90000]")
        void closeTransitions() { // GH-90000
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            incident.assignTo(UUID.randomUUID()) // GH-90000
                    .startInvestigation() // GH-90000
                    .resolve("Issue remediated [GH-90000]");

            // WHEN
            Incident result = incident.close(); // GH-90000

            // THEN
            assertThat(result).isSameAs(incident); // GH-90000
            assertThat(incident.getStatus()).isEqualTo("CLOSED [GH-90000]");
        }

        @Test
        @DisplayName("full lifecycle from OPEN to CLOSED [GH-90000]")
        void fullLifecycle() { // GH-90000
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            UUID assigneeId = UUID.randomUUID(); // GH-90000

            // WHEN
            incident.assignTo(assigneeId) // GH-90000
                    .startInvestigation() // GH-90000
                    .resolve("Remediated [GH-90000]")
                    .close(); // GH-90000

            // THEN
            assertThat(incident.getStatus()).isEqualTo("CLOSED [GH-90000]");
            assertThat(incident.getAssigneeId()).isEqualTo(assigneeId); // GH-90000
            assertThat(incident.getInvestigationStartedAt()).isNotNull(); // GH-90000
            assertThat(incident.getResolvedAt()).isNotNull(); // GH-90000
            assertThat(incident.getResolution()).isEqualTo("Remediated [GH-90000]");
        }
    }

    @Nested
    @DisplayName("isOpen() Tests [GH-90000]")
    class IsOpenTests {

        @Test
        @DisplayName("isOpen() returns true for OPEN status [GH-90000]")
        void isOpenReturnsTrueForOpen() { // GH-90000
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            assertThat(incident.isOpen()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isOpen() returns true for ASSIGNED status [GH-90000]")
        void isOpenReturnsTrueForAssigned() { // GH-90000
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            incident.assignTo(UUID.randomUUID()); // GH-90000
            assertThat(incident.isOpen()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isOpen() returns false for INVESTIGATING status [GH-90000]")
        void isOpenReturnsFalseForInvestigating() { // GH-90000
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            incident.startInvestigation(); // GH-90000
            assertThat(incident.isOpen()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isOpen() returns false for RESOLVED status [GH-90000]")
        void isOpenReturnsFalseForResolved() { // GH-90000
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            incident.resolve("Fixed [GH-90000]");
            assertThat(incident.isOpen()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isOpen() returns false for CLOSED status [GH-90000]")
        void isOpenReturnsFalseForClosed() { // GH-90000
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            incident.close(); // GH-90000
            assertThat(incident.isOpen()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Time To Resolution Tests [GH-90000]")
    class TimeToResolutionTests {

        @Test
        @DisplayName("getTimeToResolutionMs() returns -1 before resolution [GH-90000]")
        void getTimeToResolutionMsReturnsNegativeBeforeResolution() { // GH-90000
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            assertThat(incident.getTimeToResolutionMs()).isEqualTo(-1); // GH-90000
        }

        @Test
        @DisplayName("getTimeToResolutionMs() returns positive value after resolution [GH-90000]")
        void getTimeToResolutionMsReturnsPositiveAfterResolution() throws InterruptedException { // GH-90000
            // GIVEN
            Incident incident = Incident.of(WORKSPACE_ID, TITLE, SEVERITY); // GH-90000
            Thread.sleep(10); // Ensure measurable time // GH-90000
            incident.resolve("Fixed [GH-90000]");

            // THEN
            assertThat(incident.getTimeToResolutionMs()).isGreaterThan(0); // GH-90000
        }

        @Test
        @DisplayName("getTimeToResolutionMs() returns -1 if detectedAt is null [GH-90000]")
        void getTimeToResolutionMsReturnsNegativeIfDetectedAtNull() { // GH-90000
            Incident incident = Incident.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .title(TITLE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .resolvedAt(Instant.now()) // GH-90000
                    .build(); // GH-90000

            assertThat(incident.getTimeToResolutionMs()).isEqualTo(-1); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates incident with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            UUID assigneeId = UUID.randomUUID(); // GH-90000
            UUID reporterId = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            Incident incident = Incident.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .title(TITLE) // GH-90000
                    .description("Detailed description of the incident [GH-90000]")
                    .severity(SEVERITY) // GH-90000
                    .status("INVESTIGATING [GH-90000]")
                    .priority(1) // GH-90000
                    .assigneeId(assigneeId) // GH-90000
                    .reporterId(reporterId) // GH-90000
                    .category("UNAUTHORIZED_ACCESS [GH-90000]")
                    .rootCause("Compromised credentials [GH-90000]")
                    .resolution("Credentials rotated [GH-90000]")
                    .detectedAt(now.minusSeconds(3600)) // GH-90000
                    .investigationStartedAt(now.minusSeconds(1800)) // GH-90000
                    .resolvedAt(now) // GH-90000
                    .createdAt(now.minusSeconds(3600)) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(3) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(incident.getId()).isEqualTo(id); // GH-90000
            assertThat(incident.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(incident.getTitle()).isEqualTo(TITLE); // GH-90000
            assertThat(incident.getDescription()).isEqualTo("Detailed description of the incident [GH-90000]");
            assertThat(incident.getSeverity()).isEqualTo(SEVERITY); // GH-90000
            assertThat(incident.getStatus()).isEqualTo("INVESTIGATING [GH-90000]");
            assertThat(incident.getPriority()).isEqualTo(1); // GH-90000
            assertThat(incident.getAssigneeId()).isEqualTo(assigneeId); // GH-90000
            assertThat(incident.getReporterId()).isEqualTo(reporterId); // GH-90000
            assertThat(incident.getCategory()).isEqualTo("UNAUTHORIZED_ACCESS [GH-90000]");
            assertThat(incident.getRootCause()).isEqualTo("Compromised credentials [GH-90000]");
            assertThat(incident.getResolution()).isEqualTo("Credentials rotated [GH-90000]");
            assertThat(incident.getVersion()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("builder defaults status to OPEN [GH-90000]")
        void builderDefaultsStatusToOpen() { // GH-90000
            Incident incident = Incident.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .title(TITLE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .build(); // GH-90000

            assertThat(incident.getStatus()).isEqualTo("OPEN [GH-90000]");
        }

        @Test
        @DisplayName("builder defaults priority to 3 [GH-90000]")
        void builderDefaultsPriorityTo3() { // GH-90000
            Incident incident = Incident.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .title(TITLE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .build(); // GH-90000

            assertThat(incident.getPriority()).isEqualTo(3); // GH-90000
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Incident incident1 = Incident.builder().id(id).severity("HIGH [GH-90000]").build();
            Incident incident2 = Incident.builder().id(id).severity("LOW [GH-90000]").build();

            assertThat(incident1).isEqualTo(incident2); // GH-90000
            assertThat(incident1.hashCode()).isEqualTo(incident2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            Incident incident1 = Incident.builder().id(UUID.randomUUID()).build(); // GH-90000
            Incident incident2 = Incident.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(incident1).isNotEqualTo(incident2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Severity Tests [GH-90000]")
    class SeverityTests {

        @Test
        @DisplayName("can create incident with various severities [GH-90000]")
        void canCreateWithVariousSeverities() { // GH-90000
            String[] severities = {"CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"};

            for (String severity : severities) { // GH-90000
                Incident incident = Incident.of(WORKSPACE_ID, TITLE, severity); // GH-90000
                assertThat(incident.getSeverity()).isEqualTo(severity); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Category Tests [GH-90000]")
    class CategoryTests {

        @Test
        @DisplayName("can set various incident categories [GH-90000]")
        void canSetVariousCategories() { // GH-90000
            String[] categories = {
                    "DATA_BREACH",
                    "MALWARE",
                    "UNAUTHORIZED_ACCESS",
                    "PHISHING",
                    "DOS_ATTACK",
                    "INSIDER_THREAT"
            };

            for (String category : categories) { // GH-90000
                Incident incident = Incident.builder() // GH-90000
                        .workspaceId(WORKSPACE_ID) // GH-90000
                        .title(TITLE) // GH-90000
                        .severity(SEVERITY) // GH-90000
                        .category(category) // GH-90000
                        .build(); // GH-90000

                assertThat(incident.getCategory()).isEqualTo(category); // GH-90000
            }
        }
    }
}
