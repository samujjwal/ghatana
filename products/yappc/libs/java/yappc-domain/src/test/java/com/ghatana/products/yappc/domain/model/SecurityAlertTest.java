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
@DisplayName("SecurityAlert Domain Model Tests")
class SecurityAlertTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final String ALERT_TYPE = "VULNERABILITY";
    private static final String SEVERITY = "CRITICAL";
    private static final String TITLE = "Critical vulnerability detected in production";

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates alert with required fields and defaults")
        void ofCreatesWithRequiredFieldsAndDefaults() {
            // WHEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE);

            // THEN
            assertThat(alert.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(alert.getAlertType()).isEqualTo(ALERT_TYPE);
            assertThat(alert.getSeverity()).isEqualTo(SEVERITY);
            assertThat(alert.getTitle()).isEqualTo(TITLE);
            assertThat(alert.getStatus()).isEqualTo("OPEN");
            assertThat(alert.getCreatedAt()).isNotNull();
            assertThat(alert.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("of() throws NullPointerException for null required fields")
        void ofThrowsForNullRequiredFields() {
            assertThatThrownBy(() -> SecurityAlert.of(null, ALERT_TYPE, SEVERITY, TITLE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("workspaceId must not be null");

            assertThatThrownBy(() -> SecurityAlert.of(WORKSPACE_ID, null, SEVERITY, TITLE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("alertType must not be null");

            assertThatThrownBy(() -> SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, null, TITLE))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("severity must not be null");

            assertThatThrownBy(() -> SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("title must not be null");
        }
    }

    @Nested
    @DisplayName("Lifecycle Transition Tests")
    class LifecycleTransitionTests {

        @Test
        @DisplayName("acknowledge() sets status and records user/timestamp")
        void acknowledgeSetsStatusAndDetails() {
            // GIVEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE);
            UUID userId = UUID.randomUUID();
            Instant beforeAck = Instant.now();

            // WHEN
            SecurityAlert result = alert.acknowledge(userId);

            // THEN
            assertThat(result).isSameAs(alert);
            assertThat(alert.getStatus()).isEqualTo("ACKNOWLEDGED");
            assertThat(alert.getAcknowledgedBy()).isEqualTo(userId);
            assertThat(alert.getAcknowledgedAt()).isAfterOrEqualTo(beforeAck);
        }

        @Test
        @DisplayName("resolve() sets status and records user/timestamp")
        void resolveSetsStatusAndDetails() {
            // GIVEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE);
            alert.acknowledge(UUID.randomUUID());
            UUID resolverId = UUID.randomUUID();
            Instant beforeResolve = Instant.now();

            // WHEN
            SecurityAlert result = alert.resolve(resolverId);

            // THEN
            assertThat(result).isSameAs(alert);
            assertThat(alert.getStatus()).isEqualTo("RESOLVED");
            assertThat(alert.getResolvedBy()).isEqualTo(resolverId);
            assertThat(alert.getResolvedAt()).isAfterOrEqualTo(beforeResolve);
        }

        @Test
        @DisplayName("full lifecycle from OPEN to RESOLVED")
        void fullLifecycle() {
            // GIVEN
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE);
            UUID ackUser = UUID.randomUUID();
            UUID resolveUser = UUID.randomUUID();

            // WHEN
            alert.acknowledge(ackUser).resolve(resolveUser);

            // THEN
            assertThat(alert.getStatus()).isEqualTo("RESOLVED");
            assertThat(alert.getAcknowledgedBy()).isEqualTo(ackUser);
            assertThat(alert.getResolvedBy()).isEqualTo(resolveUser);
            assertThat(alert.getAcknowledgedAt()).isNotNull();
            assertThat(alert.getResolvedAt()).isNotNull();
            assertThat(alert.getAcknowledgedAt()).isBeforeOrEqualTo(alert.getResolvedAt());
        }
    }

    @Nested
    @DisplayName("Status Check Tests")
    class StatusCheckTests {

        @Test
        @DisplayName("isOpen() returns true for OPEN status")
        void isOpenReturnsTrueForOpenStatus() {
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE);
            assertThat(alert.isOpen()).isTrue();
        }

        @Test
        @DisplayName("isOpen() returns false for ACKNOWLEDGED status")
        void isOpenReturnsFalseForAcknowledged() {
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE);
            alert.acknowledge(UUID.randomUUID());
            assertThat(alert.isOpen()).isFalse();
        }

        @Test
        @DisplayName("isOpen() returns false for RESOLVED status")
        void isOpenReturnsFalseForResolved() {
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, SEVERITY, TITLE);
            alert.resolve(UUID.randomUUID());
            assertThat(alert.isOpen()).isFalse();
        }

        @Test
        @DisplayName("isOpen() is case-insensitive")
        void isOpenIsCaseInsensitive() {
            SecurityAlert alert = SecurityAlert.builder()
                    .workspaceId(WORKSPACE_ID)
                    .alertType(ALERT_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .status("open")
                    .build();

            assertThat(alert.isOpen()).isTrue();
        }
    }

    @Nested
    @DisplayName("Severity Check Tests")
    class SeverityCheckTests {

        @Test
        @DisplayName("isCritical() returns true for CRITICAL severity")
        void isCriticalReturnsTrueForCritical() {
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "CRITICAL", TITLE);
            assertThat(alert.isCritical()).isTrue();
        }

        @Test
        @DisplayName("isCritical() returns false for HIGH severity")
        void isCriticalReturnsFalseForHigh() {
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "HIGH", TITLE);
            assertThat(alert.isCritical()).isFalse();
        }

        @Test
        @DisplayName("isCritical() returns false for MEDIUM severity")
        void isCriticalReturnsFalseForMedium() {
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "MEDIUM", TITLE);
            assertThat(alert.isCritical()).isFalse();
        }

        @Test
        @DisplayName("isCritical() is case-insensitive")
        void isCriticalIsCaseInsensitive() {
            SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, ALERT_TYPE, "critical", TITLE);
            assertThat(alert.isCritical()).isTrue();
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("builder creates alert with all fields")
        void builderCreatesWithAllFields() {
            // GIVEN
            UUID id = UUID.randomUUID();
            UUID resourceId = UUID.randomUUID();
            UUID ackBy = UUID.randomUUID();
            UUID resolvedBy = UUID.randomUUID();
            Instant now = Instant.now();

            // WHEN
            SecurityAlert alert = SecurityAlert.builder()
                    .id(id)
                    .workspaceId(WORKSPACE_ID)
                    .alertType(ALERT_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .description("Detailed description of the security alert")
                    .source("SCAN")
                    .resourceId(resourceId)
                    .status("RESOLVED")
                    .acknowledgedBy(ackBy)
                    .acknowledgedAt(now.minusSeconds(3600))
                    .resolvedBy(resolvedBy)
                    .resolvedAt(now)
                    .createdAt(now.minusSeconds(7200))
                    .updatedAt(now)
                    .version(3)
                    .build();

            // THEN
            assertThat(alert.getId()).isEqualTo(id);
            assertThat(alert.getSource()).isEqualTo("SCAN");
            assertThat(alert.getResourceId()).isEqualTo(resourceId);
            assertThat(alert.getAcknowledgedBy()).isEqualTo(ackBy);
            assertThat(alert.getResolvedBy()).isEqualTo(resolvedBy);
            assertThat(alert.getVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("builder defaults status to OPEN")
        void builderDefaultsStatusToOpen() {
            SecurityAlert alert = SecurityAlert.builder()
                    .workspaceId(WORKSPACE_ID)
                    .alertType(ALERT_TYPE)
                    .severity(SEVERITY)
                    .title(TITLE)
                    .build();

            assertThat(alert.getStatus()).isEqualTo("OPEN");
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("equals returns true for same id")
        void equalsReturnsTrueForSameId() {
            UUID id = UUID.randomUUID();
            SecurityAlert alert1 = SecurityAlert.builder().id(id).severity("HIGH").build();
            SecurityAlert alert2 = SecurityAlert.builder().id(id).severity("LOW").build();

            assertThat(alert1).isEqualTo(alert2);
            assertThat(alert1.hashCode()).isEqualTo(alert2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different ids")
        void equalsReturnsFalseForDifferentIds() {
            SecurityAlert alert1 = SecurityAlert.builder().id(UUID.randomUUID()).build();
            SecurityAlert alert2 = SecurityAlert.builder().id(UUID.randomUUID()).build();

            assertThat(alert1).isNotEqualTo(alert2);
        }
    }

    @Nested
    @DisplayName("Alert Type Tests")
    class AlertTypeTests {

        @Test
        @DisplayName("can set various alert types")
        void canSetVariousAlertTypes() {
            String[] types = {"VULNERABILITY", "POLICY_VIOLATION", "ANOMALY", "INTRUSION", "DATA_EXFILTRATION"};

            for (String type : types) {
                SecurityAlert alert = SecurityAlert.of(WORKSPACE_ID, type, SEVERITY, TITLE);
                assertThat(alert.getAlertType()).isEqualTo(type);
            }
        }
    }

    @Nested
    @DisplayName("Source Tests")
    class SourceTests {

        @Test
        @DisplayName("can set various alert sources")
        void canSetVariousSources() {
            String[] sources = {"SCAN", "CLOUD_TRAIL", "WAF", "IDS", "SIEM", "MANUAL"};

            for (String source : sources) {
                SecurityAlert alert = SecurityAlert.builder()
                        .workspaceId(WORKSPACE_ID)
                        .alertType(ALERT_TYPE)
                        .severity(SEVERITY)
                        .title(TITLE)
                        .source(source)
                        .build();

                assertThat(alert.getSource()).isEqualTo(source);
            }
        }
    }
}
