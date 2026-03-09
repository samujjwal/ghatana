package com.ghatana.aiplatform.adapters.guardian;

import com.ghatana.platform.domain.domain.models.agent.AgentInfo;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for GuardianAgentAdapter.
 *
 * Tests validate:
 * - Guardian agent creation from core Agent model
 * - Threat assessment based on device events
 * - Health score calculation from device metrics
 * - Threat level determination and action recommendations
 * - Product adapter composition pattern (has-a core agent)
 * - Threat metrics tracking and reporting
 *
 * @see GuardianAgentAdapter
 */
@DisplayName("Guardian Agent Adapter Integration Tests")
class GuardianAgentAdapterIntegrationTest {

    private GuardianAgentAdapter guardianAgent;
    private AgentInfo coreAgent;

    @BeforeEach
    void setUp() {
        // GIVEN: Core agent from domain models
        coreAgent = createCoreAgent("agent-guardian-001");

        // AND: Guardian agent adapter extending core agent
        guardianAgent = GuardianAgentAdapter.create()
                .withCoreAgent(coreAgent)
                .withThreatLevel(GuardianAgentAdapter.ThreatLevel.MEDIUM)
                .withHealthThreshold(0.70)
                .withAlertPattern(GuardianAgentAdapter.AlertPattern.ENSEMBLE)
                .withMetrics(MetricsCollectorFactory.createNoop())
                .build();
    }

    /**
     * Verifies Guardian adapter preserves core Agent model.
     *
     * GIVEN: Core Agent model
     * WHEN: Guardian adapter is created
     * THEN: Core agent remains accessible and unchanged (composition pattern)
     */
    @Test
    @DisplayName("Should preserve core Agent model in adapter")
    void shouldPreserveCoreAgentModel() {
        // WHEN: Access core agent through adapter
        AgentInfo retrieved = guardianAgent.getCoreAgent();

        // THEN: Core agent unchanged
        assertThat(retrieved)
                .as("Core agent should be preserved")
                .isSameAs(coreAgent);
        assertThat(retrieved.getId())
                .as("Agent ID should match")
                .isEqualTo("agent-guardian-001");
    }

    /**
     * Verifies threat assessment from device events.
     *
     * GIVEN: Device event with malware detected
     * WHEN: assessThreat() is called
     * THEN: Returns ThreatAssessment with HIGH threat level and quarantine recommendation
     */
    @Test
    @DisplayName("Should assess high threat from malware detection")
    void shouldAssessHighThreatFromMalware() {
        // GIVEN: Event with malware indicator
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("malware_detected", true);
        eventData.put("unauthorized_access", true);

        // WHEN: Assess threat
        GuardianAgentAdapter.ThreatAssessment assessment = guardianAgent.assessThreat(eventData);

        // THEN: High threat detected
        assertThat(assessment.isThreat)
                .as("Should detect threat")
                .isTrue();
        assertThat(assessment.threatLevel)
                .as("Should classify as HIGH (malware + unauthorized = 5 indicators)")
                .isEqualTo(GuardianAgentAdapter.ThreatLevel.CRITICAL);
        assertThat(assessment.recommendedAction)
                .as("Should recommend quarantine")
                .isEqualTo("ISOLATE_DEVICE_IMMEDIATELY");
        assertThat(assessment.evidence)
                .as("Should capture evidence")
                .contains("Malware detected", "Unauthorized access attempt");
    }

    /**
     * Verifies threat assessment with multiple indicators.
     *
     * GIVEN: Event with multiple threat indicators (malware, privilege escalation, network anomaly)
     * WHEN: assessThreat() is called
     * THEN: Returns ThreatAssessment with CRITICAL level and immediate isolation recommendation
     */
    @Test
    @DisplayName("Should assess critical threat from multiple indicators")
    void shouldAssessCriticalThreatFromMultipleIndicators() {
        // GIVEN: Event with multiple threat indicators
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("malware_detected", true);           // +3 indicators
        eventData.put("privilege_escalation", true);       // +2 indicators
        eventData.put("abnormal_network", true);           // +1 indicator
        // Total: 6 indicators → CRITICAL

        // WHEN: Assess threat
        GuardianAgentAdapter.ThreatAssessment assessment = guardianAgent.assessThreat(eventData);

        // THEN: Critical threat detected
        assertThat(assessment.isThreat)
                .as("Should detect critical threat")
                .isTrue();
        assertThat(assessment.threatLevel)
                .as("Should classify as CRITICAL (6 indicators)")
                .isEqualTo(GuardianAgentAdapter.ThreatLevel.CRITICAL);
        assertThat(assessment.suspiciousIndicators)
                .as("Should count all indicators")
                .isEqualTo(6);
        assertThat(assessment.recommendedAction)
                .as("Should recommend immediate isolation")
                .isEqualTo("ISOLATE_DEVICE_IMMEDIATELY");
    }

    /**
     * Verifies no threat assessment from clean events.
     *
     * GIVEN: Device event with no threat indicators
     * WHEN: assessThreat() is called
     * THEN: Returns ThreatAssessment with LOW threat level and continue monitoring
     */
    @Test
    @DisplayName("Should classify clean events as no threat")
    void shouldClassifyCleanEventsAsNoThreat() {
        // GIVEN: Clean device event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("cpu_usage", 25.0);
        eventData.put("ram_usage", 45.0);

        // WHEN: Assess threat
        GuardianAgentAdapter.ThreatAssessment assessment = guardianAgent.assessThreat(eventData);

        // THEN: No threat detected
        assertThat(assessment.isThreat)
                .as("Should not detect threat")
                .isFalse();
        assertThat(assessment.threatLevel)
                .as("Should classify as LOW")
                .isEqualTo(GuardianAgentAdapter.ThreatLevel.LOW);
        assertThat(assessment.recommendedAction)
                .as("Should recommend monitoring")
                .isEqualTo("LOG_AND_CONTINUE_MONITORING");
    }

    /**
     * Verifies health score calculation from device metrics.
     *
     * GIVEN: Device metrics (CPU, RAM, storage usage)
     * WHEN: calculateHealthScore() is called
     * THEN: Returns score reflecting resource utilization (weighted: RAM 50%, CPU 30%, Storage 20%)
     */
    @Test
    @DisplayName("Should calculate health score from device metrics")
    void shouldCalculateHealthScore() {
        // GIVEN: Device metrics
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("cpu_usage", 50.0);        // 50% usage → 0.5 health
        metrics.put("ram_usage", 60.0);        // 60% usage → 0.4 health
        metrics.put("storage_usage", 80.0);    // 80% usage → 0.2 health

        // WHEN: Calculate health score
        double healthScore = guardianAgent.calculateHealthScore(metrics);

        // THEN: Health score reflects weighted average
        // Health = (0.4 * 0.5) + (0.5 * 0.3) + (0.2 * 0.2)
        //        = 0.20 + 0.15 + 0.04 = 0.39
        assertThat(healthScore)
                .as("Should calculate weighted health score (RAM 50%, CPU 30%, Storage 20%)")
                .isBetween(0.38, 0.40);
    }

    /**
     * Verifies health threshold check.
     *
     * GIVEN: Device health below configured threshold
     * WHEN: isUnhealthy() is called
     * THEN: Returns true (device health degraded)
     */
    @Test
    @DisplayName("Should detect unhealthy devices below threshold")
    void shouldDetectUnhealthyDevicesBelowThreshold() {
        // GIVEN: Device with health score 0.65 and threshold 0.70
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("cpu_usage", 50.0);
        metrics.put("ram_usage", 65.0);
        metrics.put("storage_usage", 70.0);

        // WHEN: Check if unhealthy
        boolean isUnhealthy = guardianAgent.isUnhealthy(metrics);

        // THEN: Device marked unhealthy
        assertThat(isUnhealthy)
                .as("Device health < 0.70 threshold → unhealthy")
                .isTrue();
    }

    /**
     * Verifies healthy device above threshold.
     *
     * GIVEN: Device health above configured threshold
     * WHEN: isUnhealthy() is called
     * THEN: Returns false (device is healthy)
     */
    @Test
    @DisplayName("Should classify healthy devices above threshold")
    void shouldClassifyHealthyDevicesAboveThreshold() {
        // GIVEN: Device with health score 0.85 and threshold 0.70
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("cpu_usage", 20.0);
        metrics.put("ram_usage", 30.0);
        metrics.put("storage_usage", 40.0);

        // WHEN: Check if unhealthy
        boolean isUnhealthy = guardianAgent.isUnhealthy(metrics);

        // THEN: Device marked healthy
        assertThat(isUnhealthy)
                .as("Device health > 0.70 threshold → healthy")
                .isFalse();
    }

    /**
     * Verifies Guardian configuration accessibility.
     *
     * GIVEN: Guardian agent with specific configuration
     * WHEN: Accessors are called
     * THEN: Configuration matches what was set
     */
    @Test
    @DisplayName("Should provide access to Guardian configuration")
    void shouldProvideAccessToGuardianConfiguration() {
        // WHEN: Access configuration
        GuardianAgentAdapter.ThreatLevel threatLevel = guardianAgent.getThreatLevel();
        double healthThreshold = guardianAgent.getHealthThreshold();
        GuardianAgentAdapter.AlertPattern alertPattern = guardianAgent.getAlertPattern();

        // THEN: Configuration matches builder settings
        assertThat(threatLevel)
                .as("Should return configured threat level")
                .isEqualTo(GuardianAgentAdapter.ThreatLevel.MEDIUM);
        assertThat(healthThreshold)
                .as("Should return configured health threshold")
                .isEqualTo(0.70);
        assertThat(alertPattern)
                .as("Should return configured alert pattern")
                .isEqualTo(GuardianAgentAdapter.AlertPattern.ENSEMBLE);
    }

    /**
     * Verifies threat metrics accumulation.
     *
     * GIVEN: Multiple threat assessments
     * WHEN: getThreatMetrics() is called
     * THEN: Returns counts of each threat level detected
     */
    @Test
    @DisplayName("Should accumulate threat metrics across assessments")
    void shouldAccumulateThreatMetrics() {
        // GIVEN: Multiple threat assessments
        Map<String, Object> lowThreatEvent = new HashMap<>();
        guardianAgent.assessThreat(lowThreatEvent);

        Map<String, Object> mediumThreatEvent = new HashMap<>();
        mediumThreatEvent.put("unauthorized_access", true);
        guardianAgent.assessThreat(mediumThreatEvent);

        Map<String, Object> highThreatEvent = new HashMap<>();
        highThreatEvent.put("malware_detected", true);
        highThreatEvent.put("privilege_escalation", true);
        guardianAgent.assessThreat(highThreatEvent);

        // WHEN: Get threat metrics
        Map<String, Long> metrics = guardianAgent.getThreatMetrics();

        // THEN: Metrics should track threat level counts
        assertThat(metrics)
                .as("Should track threat level occurrences")
                .containsEntry("MEDIUM", 1L)
                .containsEntry("CRITICAL", 1L);
    }

    /**
     * Verifies adapter builder validation.
     *
     * GIVEN: Builder with missing required fields
     * WHEN: build() is called
     * THEN: Throws IllegalArgumentException
     */
    @Test
    @DisplayName("Should validate required fields during build")
    void shouldValidateRequiredFieldsDuringBuild() {
        // GIVEN: Builder without core agent
        GuardianAgentAdapter.GuardianAgentBuilder builder = GuardianAgentAdapter.create()
                .withMetrics(MetricsCollectorFactory.createNoop());

        // WHEN: Build without core agent
        // THEN: Throws exception
        assertThatThrownBy(builder::build)
                .as("Should require core agent")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Core agent");
    }

    /**
     * Verifies health threshold validation.
     *
     * GIVEN: Invalid health threshold (> 1.0)
     * WHEN: withHealthThreshold() is called
     * THEN: Throws IllegalArgumentException
     */
    @Test
    @DisplayName("Should validate health threshold range")
    void shouldValidateHealthThresholdRange() {
        // GIVEN: Invalid threshold
        GuardianAgentAdapter.GuardianAgentBuilder builder = GuardianAgentAdapter.create()
                .withCoreAgent(coreAgent);

        // WHEN: Set invalid threshold
        // THEN: Throws exception
        assertThatThrownBy(() -> builder.withHealthThreshold(1.5))
                .as("Should reject threshold > 1.0")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0.0 and 1.0");
    }

    /**
     * Creates mock core Agent model for testing.
     * Demonstrates product adapter extending core model.
     *
     * @param agentId agent identifier
     * @return core agent info (from domain-models library)
     */
    private AgentInfo createCoreAgent(String agentId) {
        // This would normally be created from core domain-models
        // For testing, we create a minimal AgentInfo instance
        AgentInfo agent = new AgentInfo();
        agent.setId(agentId);
        agent.setType("DEVICE_MONITOR");
        agent.setStatus("ACTIVE");
        agent.setMetadata(new HashMap<>());
        return agent;
    }
}
