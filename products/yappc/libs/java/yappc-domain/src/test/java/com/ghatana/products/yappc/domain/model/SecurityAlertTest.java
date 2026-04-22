package com.ghatana.products.yappc.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SecurityAlert} domain model.
 *
 * @doc.type class
 * @doc.purpose Validates SecurityAlert entity behavior, lifecycle transitions, and severity checks
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("SecurityAlert Domain Model Tests [GH-90000]")
class SecurityAlertTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID(); // GH-90000
    private static final String ALERT_TYPE = "VULNERABILITY";
    private static final String SEVERITY = "CRITICAL";
    private static final String TITLE = "Critical vulnerability detected in production";

    @Nested
    @DisplayName("Factory Method Tests [GH-90000]")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates alert with required fields and defaults [GH-90000]")
        void ofCreatesWithRequiredFieldsAndDefaults() { // GH-90000
            // WHEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE); // GH-90000

            // THEN
            assertThat(alert.getWorkspaceId()).isEqualTo(WORKSPACE_ID); // GH-90000
            assertThat(alert.getAlertType()).isEqualTo(ALERT_TYPE); // GH-90000
            assertThat(alert.getSeverity()).isEqualTo(SEVERITY); // GH-90000
            assertThat(alert.getTitle()).isEqualTo(TITLE); // GH-90000
            assertThat(alert.getStatus()).isEqualTo("OPEN [GH-90000]");
            assertThat(alert.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(alert.getUpdatedAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields [GH-90000]")
        void ofThrowsForNullRequiredFields() { // GH-90000
            assertThatThrownBy(() -> SecurityAlert.of(null, ALERT_TYPE, SEVERITY, TITLE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("workspaceId must not be null [GH-90000]");

            assertThatThrownBy(() -> SecurityAlert.of(WORKSPACE_ID, null, SEVERITY, TITLE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("alertType must not be null [GH-90000]");

            assertThatThrownBy(() -> SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, null, TITLE)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("severity must not be null [GH-90000]");

            assertThatThrownBy(() -> SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("title must not be null [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Lifecycle Transition Tests [GH-90000]")
    class LifecycleTransitionTests {

        @Test
        @DisplayName("acknowledge() sets status and records user/timestamp [GH-90000]")
        void acknowledgeSetsStatusAndDetails() { // GH-90000
            // GIVEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE); // GH-90000
            UUID userId = UUID.randomUUID(); // GH-90000
            Instant beforeAck = Instant.now(); // GH-90000

            // WHEN
            SecurityAlert result = alert.acknowledge(userId); // GH-90000

            // THEN
            assertThat(result).isSameAs(alert); // GH-90000
            assertThat(alert.getStatus()).isEqualTo("ACKNOWLEDGED [GH-90000]");
            assertThat(alert.getAcknowledgedBy()).isEqualTo(userId); // GH-90000
            assertThat(alert.getAcknowledgedAt()).isAfterOrEqualTo(beforeAck); // GH-90000
        }

        @Test
        @DisplayName("resolve() sets status and records user/timestamp [GH-90000]")
        void resolveSetsStatusAndDetails() { // GH-90000
            // GIVEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE); // GH-90000
            alert.acknowledge(UUID.randomUUID()); // GH-90000
            UUID resolverId = UUID.randomUUID(); // GH-90000
            Instant beforeResolve = Instant.now(); // GH-90000

            // WHEN
            SecurityAlert result = alert.resolve(resolverId); // GH-90000

            // THEN
            assertThat(result).isSameAs(alert); // GH-90000
            assertThat(alert.getStatus()).isEqualTo("RESOLVED [GH-90000]");
            assertThat(alert.getResolvedBy()).isEqualTo(resolverId); // GH-90000
            assertThat(alert.getResolvedAt()).isAfterOrEqualTo(beforeResolve); // GH-90000
        }

        @Test
        @DisplayName("full lifecycle from OPEN to RESOLVED [GH-90000]")
        void fullLifecycle() { // GH-90000
            // GIVEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE); // GH-90000
            UUID ackUser = UUID.randomUUID(); // GH-90000
            UUID resolveUser = UUID.randomUUID(); // GH-90000

            // WHEN
            alert.acknowledge(ackUser).resolve(resolveUser); // GH-90000

            // THEN
            assertThat(alert.getStatus()).isEqualTo("RESOLVED [GH-90000]");
            assertThat(alert.getAcknowledgedBy()).isEqualTo(ackUser); // GH-90000
            assertThat(alert.getResolvedBy()).isEqualTo(resolveUser); // GH-90000
            assertThat(alert.getAcknowledgedAt()).isNotNull(); // GH-90000
            assertThat(alert.getResolvedAt()).isNotNull(); // GH-90000
            assertThat(alert.getAcknowledgedAt()).isBeforeOrEqualTo(alert.getResolvedAt()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Status Check Tests [GH-90000]")
    class StatusCheckTests {

        @Test
        @DisplayName("isOpen() returns true for OPEN status [GH-90000]")
        void isOpenReturnsTrueForOpenStatus() { // GH-90000
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE); // GH-90000
            assertThat(alert.isOpen()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isOpen() returns false for ACKNOWLEDGED status [GH-90000]")
        void isOpenReturnsFalseForAcknowledged() { // GH-90000
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE); // GH-90000
            alert.acknowledge(UUID.randomUUID()); // GH-90000
            assertThat(alert.isOpen()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isOpen() returns false for RESOLVED status [GH-90000]")
        void isOpenReturnsFalseForResolved() { // GH-90000
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE); // GH-90000
            alert.resolve(UUID.randomUUID()); // GH-90000
            assertThat(alert.isOpen()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isOpen() is case-insensitive [GH-90000]")
        void isOpenIsCaseInsensitive() { // GH-90000
            SecurityAlert alert = SecurityAlert.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .alertType(ALERT_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .status("open [GH-90000]")
                    .build(); // GH-90000

            assertThat(alert.isOpen()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Severity Check Tests [GH-90000]")
    class SeverityCheckTests {

        @Test
        @DisplayName("isCritical() returns true for CRITICAL severity [GH-90000]")
        void isCriticalReturnsTrueForCritical() { // GH-90000
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "CRITICAL", TITLE); // GH-90000
            assertThat(alert.isCritical()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("isCritical() returns false for HIGH severity [GH-90000]")
        void isCriticalReturnsFalseForHigh() { // GH-90000
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "HIGH", TITLE); // GH-90000
            assertThat(alert.isCritical()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isCritical() returns false for MEDIUM severity [GH-90000]")
        void isCriticalReturnsFalseForMedium() { // GH-90000
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "MEDIUM", TITLE); // GH-90000
            assertThat(alert.isCritical()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("isCritical() is case-insensitive [GH-90000]")
        void isCriticalIsCaseInsensitive() { // GH-90000
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "critical", TITLE); // GH-90000
            assertThat(alert.isCritical()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder Tests [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builder creates alert with all fields [GH-90000]")
        void builderCreatesWithAllFields() { // GH-90000
            // GIVEN
            UUID id = UUID.randomUUID(); // GH-90000
            UUID resourceId = UUID.randomUUID(); // GH-90000
            UUID ackBy = UUID.randomUUID(); // GH-90000
            UUID resolvedBy = UUID.randomUUID(); // GH-90000
            Instant now = Instant.now(); // GH-90000

            // WHEN
            SecurityAlert alert = SecurityAlert.builder() // GH-90000
                    .id(id) // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .alertType(ALERT_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .description("Detailed description of the security alert [GH-90000]")
                    .source("SCAN [GH-90000]")
                    .resourceId(resourceId) // GH-90000
                    .status("RESOLVED [GH-90000]")
                    .acknowledgedBy(ackBy) // GH-90000
                    .acknowledgedAt(now.minusSeconds(3600)) // GH-90000
                    .resolvedBy(resolvedBy) // GH-90000
                    .resolvedAt(now) // GH-90000
                    .createdAt(now.minusSeconds(7200)) // GH-90000
                    .updatedAt(now) // GH-90000
                    .version(3) // GH-90000
                    .build(); // GH-90000

            // THEN
            assertThat(alert.getId()).isEqualTo(id); // GH-90000
            assertThat(alert.getSource()).isEqualTo("SCAN [GH-90000]");
            assertThat(alert.getResourceId()).isEqualTo(resourceId); // GH-90000
            assertThat(alert.getAcknowledgedBy()).isEqualTo(ackBy); // GH-90000
            assertThat(alert.getResolvedBy()).isEqualTo(resolvedBy); // GH-90000
            assertThat(alert.getVersion()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("builder defaults status to OPEN [GH-90000]")
        void builderDefaultsStatusToOpen() { // GH-90000
            SecurityAlert alert = SecurityAlert.builder() // GH-90000
                    .workspaceId(WORKSPACE_ID) // GH-90000
                    .alertType(ALERT_TYPE) // GH-90000
                    .severity(SEVERITY) // GH-90000
                    .title(TITLE) // GH-90000
                    .build(); // GH-90000

            assertThat(alert.getStatus()).isEqualTo("OPEN [GH-90000]");
        }
    }

    @Nested
    @DisplayName("Equality Tests [GH-90000]")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id [GH-90000]")
        void equalsReturnsTrueForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            SecurityAlert alert1 = SecurityAlert.builder().id(id).severity("HIGH [GH-90000]").build();
            SecurityAlert alert2 = SecurityAlert.builder().id(id).severity("LOW [GH-90000]").build();

            assertThat(alert1).isEqualTo(alert2); // GH-90000
            assertThat(alert1.hashCode()).isEqualTo(alert2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("equals returns false for different ids [GH-90000]")
        void equalsReturnsFalseForDifferentIds() { // GH-90000
            SecurityAlert alert1 = SecurityAlert.builder().id(UUID.randomUUID()).build(); // GH-90000
            SecurityAlert alert2 = SecurityAlert.builder().id(UUID.randomUUID()).build(); // GH-90000

            assertThat(alert1).isNotEqualTo(alert2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Alert Type Tests [GH-90000]")
    class AlertTypeTests {

        @Test
        @DisplayName("can set various alert types [GH-90000]")
        void canSetVariousAlertTypes() { // GH-90000
            String[] types = {"VULNERABILITY", "POLICY_VIOLATION", "ANOMALY", "INTRUSION", "DATA_EXFILTRATION"};

            for (String type : types) { // GH-90000
                SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, type, SEVERITY, TITLE); // GH-90000
                assertThat(alert.getAlertType()).isEqualTo(type); // GH-90000
            }
        }
    }

    @Nested
    @DisplayName("Source Tests [GH-90000]")
    class SourceTests {

        @Test
        @DisplayName("can set various alert sources [GH-90000]")
        void canSetVariousSources() { // GH-90000
            String[] sources = {"SCAN", "CLOUD_TRAIL", "WAF", "IDS", "SIEM", "MANUAL"};

            for (String source : sources) { // GH-90000
                SecurityAlert alert = SecurityAlert.builder() // GH-90000
                        .workspaceId(WORKSPACE_ID) // GH-90000
                        .alertType(ALERT_TYPE) // GH-90000
                        .severity(SEVERITY) // GH-90000
                        .title(TITLE) // GH-90000
                        .source(source) // GH-90000
                        .build(); // GH-90000

                assertThat(alert.getSource()).isEqualTo(source); // GH-90000
            }
        }
    }
}
