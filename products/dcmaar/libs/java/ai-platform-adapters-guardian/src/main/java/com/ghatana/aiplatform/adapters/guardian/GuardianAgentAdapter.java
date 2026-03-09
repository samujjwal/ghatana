package com.ghatana.aiplatform.adapters.guardian;

import com.ghatana.platform.domain.domain.models.agent.AgentInfo;
import com.ghatana.platform.observability.MetricsCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guardian-specific agent implementation extending core AgentInfo model.
 *
 * <p><b>Purpose</b><br>
 * Demonstrates product-specific agent adaptation. Guardian agents monitor
 * device health and security, extending the core Agent model with:
 * - Guardian-specific capabilities (threat detection, health scoring)
 * - Guardian-specific configuration (threat levels, health thresholds)
 * - Guardian-specific metrics (alert patterns, device classifications)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GuardianAgentAdapter agent = GuardianAgentAdapter.create()
 *     .withCoreAgent(coreAgentInfo)  // Start from core Agent
 *     .withThreatLevel(GuardianAgentAdapter.ThreatLevel.MEDIUM)
 *     .withHealthThreshold(0.80)
 *     .withAlertPattern(GuardianAgentAdapter.AlertPattern.ANOMALY_BASED)
 *     .build();
 *
 * // Use as core agent
 * String agentId = agent.getCoreAgent().getId();
 *
 * // Access Guardian extensions
 * GuardianAgentAdapter.ThreatAssessment threat = agent.assessThreat(eventData);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Product adapter: Extends core Agent model for Guardian product
 * - Composition pattern: Has-a core Agent, adds Guardian capabilities
 * - Interoperability: Core Agent remains globally shared, adapters are product-specific
 * - Extension point: Demonstrates how products extend core models without duplication
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap for Guardian-specific state.
 * Core agent info is immutable (from AgentInfo).
 *
 * Pattern: Adapter
 */
public class GuardianAgentAdapter {
    private static final Logger logger = LoggerFactory.getLogger(GuardianAgentAdapter.class);

    private final AgentInfo coreAgent;
    private final ThreatLevel threatLevel;
    private final double healthThreshold;
    private final AlertPattern alertPattern;
    private final Map<String, Long> threatMetrics;
    private final MetricsCollector metrics;

    /**
     * Guardian threat severity levels.
     * Used for threat assessment and alert routing.
     */
    public enum ThreatLevel {
        LOW,      // Non-critical, informational alerts
        MEDIUM,   // Moderate risk, requires attention
        HIGH,     // Critical risk, immediate action needed
        CRITICAL  // System threat, emergency protocol
    }

    /**
     * Guardian alert detection patterns.
     * Configures how Guardian agent identifies anomalies.
     */
    public enum AlertPattern {
        SIGNATURE_BASED,   // Known threat signatures
        ANOMALY_BASED,     // Statistical deviation detection
        BEHAVIOR_BASED,    // Device behavior patterns
        ENSEMBLE           // Combined detection methods
    }

    /**
     * Private constructor (use builder instead).
     */
    private GuardianAgentAdapter(
            AgentInfo coreAgent,
            ThreatLevel threatLevel,
            double healthThreshold,
            AlertPattern alertPattern,
            MetricsCollector metrics
    ) {
        this.coreAgent = coreAgent;
        this.threatLevel = threatLevel;
        this.healthThreshold = healthThreshold;
        this.alertPattern = alertPattern;
        this.metrics = metrics;
        this.threatMetrics = new ConcurrentHashMap<>();
    }

    /**
     * Builder for Guardian agent adapters.
     *
     * @return new builder instance
     */
    public static GuardianAgentBuilder create() {
        return new GuardianAgentBuilder();
    }

    /**
     * Assesses threat level for device event.
     *
     * GIVEN: Device event data
     * WHEN: assessThreat() is called
     * THEN: Returns ThreatAssessment with severity, evidence, recommended action
     *
     * @param eventData device event to assess
     * @return threat assessment result
     */
    public ThreatAssessment assessThreat(Map<String, Object> eventData) {
        if (eventData == null || eventData.isEmpty()) {
            return ThreatAssessment.noThreat();
        }

        try {
            // Extract threat indicators from event
            int suspiciousIndicators = 0;
            List<String> evidence = new ArrayList<>();

            // Check for known threat patterns
            if (eventData.containsKey("malware_detected")) {
                suspiciousIndicators += 3;
                evidence.add("Malware detected");
            }
            if (eventData.containsKey("unauthorized_access")) {
                suspiciousIndicators += 2;
                evidence.add("Unauthorized access attempt");
            }
            if (eventData.containsKey("abnormal_network")) {
                suspiciousIndicators += 1;
                evidence.add("Abnormal network activity");
            }
            if (eventData.containsKey("privilege_escalation")) {
                suspiciousIndicators += 2;
                evidence.add("Privilege escalation detected");
            }

            ThreatLevel detectedThreat = determineThreatLevel(suspiciousIndicators);
            boolean isThreat = detectedThreat != ThreatLevel.LOW;

            // Record threat metrics
            threatMetrics.compute(
                    detectedThreat.name(),
                    (k, v) -> v == null ? 1 : v + 1
            );

            metrics.incrementCounter(
                    "guardian.threat.detected",
                    "agent_id", coreAgent.getId(),
                    "threat_level", detectedThreat.name(),
                    "alert_pattern", alertPattern.name()
            );

            return new ThreatAssessment(
                    isThreat,
                    detectedThreat,
                    suspiciousIndicators,
                    evidence,
                    recommendAction(detectedThreat)
            );

        } catch (Exception e) {
            logger.error("Error assessing threat for agent: {}", coreAgent.getId(), e);
            metrics.incrementCounter(
                    "guardian.threat.assessment.error",
                    "agent_id", coreAgent.getId(),
                    "error", e.getClass().getSimpleName()
            );
            return ThreatAssessment.noThreat();
        }
    }

    /**
     * Calculates device health score based on device metrics.
     *
     * @param metrics device metrics (CPU usage %, RAM %, storage usage %)
     * @return health score 0.0 (critical) to 1.0 (healthy)
     */
    public double calculateHealthScore(Map<String, Double> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 1.0;  // Unknown = assume healthy
        }

        double cpuUsage = metrics.getOrDefault("cpu_usage", 0.0);
        double ramUsage = metrics.getOrDefault("ram_usage", 0.0);
        double storageUsage = metrics.getOrDefault("storage_usage", 0.0);

        // Health degrades as resource usage increases
        double cpuHealth = 1.0 - (cpuUsage / 100.0);
        double ramHealth = 1.0 - (ramUsage / 100.0);
        double storageHealth = 1.0 - (storageUsage / 100.0);

        // Average health (weighted: RAM 50%, CPU 30%, Storage 20%)
        double healthScore = (ramHealth * 0.5) + (cpuHealth * 0.3) + (storageHealth * 0.2);

        return Math.max(0.0, Math.min(1.0, healthScore));
    }

    /**
     * Checks if device health is below Guardian threshold.
     *
     * @param metrics device metrics
     * @return true if health score below threshold (unhealthy)
     */
    public boolean isUnhealthy(Map<String, Double> metrics) {
        return calculateHealthScore(metrics) < healthThreshold;
    }

    /**
     * Determines threat level based on suspicious indicator count.
     *
     * @param indicators count of detected threat indicators
     * @return appropriate threat level
     */
    private ThreatLevel determineThreatLevel(int indicators) {
        if (indicators >= 5) return ThreatLevel.CRITICAL;
        if (indicators >= 3) return ThreatLevel.HIGH;
        if (indicators >= 1) return ThreatLevel.MEDIUM;
        return ThreatLevel.LOW;
    }

    /**
     * Recommends action based on threat level.
     *
     * @param threatLevel detected threat level
     * @return recommended action
     */
    private String recommendAction(ThreatLevel threatLevel) {
        return switch (threatLevel) {
            case CRITICAL -> "ISOLATE_DEVICE_IMMEDIATELY";
            case HIGH -> "QUARANTINE_AND_SCAN";
            case MEDIUM -> "MONITOR_AND_ALERT_USER";
            case LOW -> "LOG_AND_CONTINUE_MONITORING";
        };
    }

    // ===== Accessors =====

    /**
     * Gets core Agent model.
     * @return immutable core agent info
     */
    public AgentInfo getCoreAgent() {
        return coreAgent;
    }

    /**
     * Gets Guardian threat configuration.
     * @return configured threat level
     */
    public ThreatLevel getThreatLevel() {
        return threatLevel;
    }

    /**
     * Gets health threshold.
     * @return health score threshold (0.0 - 1.0)
     */
    public double getHealthThreshold() {
        return healthThreshold;
    }

    /**
     * Gets alert detection pattern.
     * @return configured alert pattern
     */
    public AlertPattern getAlertPattern() {
        return alertPattern;
    }

    /**
     * Gets threat metrics.
     * @return map of threat level counts
     */
    public Map<String, Long> getThreatMetrics() {
        return new HashMap<>(threatMetrics);
    }

    /**
     * Threat assessment result.
     * Immutable value object representing threat analysis.
     *
     * Pattern: Value Object
     */
    public static class ThreatAssessment {
        public final boolean isThreat;
        public final ThreatLevel threatLevel;
        public final int suspiciousIndicators;
        public final List<String> evidence;
        public final String recommendedAction;
        public final Instant assessedAt;

        private ThreatAssessment(
                boolean isThreat,
                ThreatLevel threatLevel,
                int suspiciousIndicators,
                List<String> evidence,
                String recommendedAction
        ) {
            this.isThreat = isThreat;
            this.threatLevel = threatLevel;
            this.suspiciousIndicators = suspiciousIndicators;
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
            this.recommendedAction = recommendedAction;
            this.assessedAt = Instant.now();
        }

        public static ThreatAssessment noThreat() {
            return new ThreatAssessment(false, ThreatLevel.LOW, 0, Collections.emptyList(), "CONTINUE");
        }

        @Override
        public String toString() {
            return "ThreatAssessment{" +
                    "isThreat=" + isThreat +
                    ", threatLevel=" + threatLevel +
                    ", indicators=" + suspiciousIndicators +
                    ", evidence=" + evidence.size() +
                    ", action='" + recommendedAction + '\'' +
                    '}';
        }
    }

    /**
     * Builder for Guardian agent adapters.
     *
     * Pattern: Builder
     */
    public static class GuardianAgentBuilder {
        private AgentInfo coreAgent;
        private ThreatLevel threatLevel = ThreatLevel.MEDIUM;
        private double healthThreshold = 0.70;
        private AlertPattern alertPattern = AlertPattern.ENSEMBLE;
        private MetricsCollector metrics = null;

        /**
         * Sets core agent.
         * @param coreAgent core agent info
         * @return this builder
         */
        public GuardianAgentBuilder withCoreAgent(AgentInfo coreAgent) {
            this.coreAgent = coreAgent;
            return this;
        }

        /**
         * Sets threat level.
         * @param threatLevel threat level
         * @return this builder
         */
        public GuardianAgentBuilder withThreatLevel(ThreatLevel threatLevel) {
            this.threatLevel = threatLevel;
            return this;
        }

        /**
         * Sets health threshold.
         * @param healthThreshold health score threshold (0.0 - 1.0)
         * @return this builder
         */
        public GuardianAgentBuilder withHealthThreshold(double healthThreshold) {
            if (healthThreshold < 0.0 || healthThreshold > 1.0) {
                throw new IllegalArgumentException("Health threshold must be between 0.0 and 1.0");
            }
            this.healthThreshold = healthThreshold;
            return this;
        }

        /**
         * Sets alert pattern.
         * @param alertPattern alert detection pattern
         * @return this builder
         */
        public GuardianAgentBuilder withAlertPattern(AlertPattern alertPattern) {
            this.alertPattern = alertPattern;
            return this;
        }

        /**
         * Sets metrics collector.
         * @param metrics metrics collector
         * @return this builder
         */
        public GuardianAgentBuilder withMetrics(MetricsCollector metrics) {
            this.metrics = metrics;
            return this;
        }

        /**
         * Builds Guardian agent adapter.
         *
         * @return configured Guardian agent adapter
         * @throws IllegalArgumentException if required fields missing
         */
        public GuardianAgentAdapter build() {
            if (coreAgent == null) {
                throw new IllegalArgumentException("Core agent must be provided");
            }
            if (metrics == null) {
                throw new IllegalArgumentException("Metrics collector must be provided");
            }

            return new GuardianAgentAdapter(
                    coreAgent,
                    threatLevel,
                    healthThreshold,
                    alertPattern,
                    metrics
            );
        }
    }
}
