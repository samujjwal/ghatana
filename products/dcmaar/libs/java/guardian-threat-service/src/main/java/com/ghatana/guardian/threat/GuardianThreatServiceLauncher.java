package com.ghatana.guardian.threat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.aiplatform.adapters.guardian.GuardianAgentAdapter;
import com.ghatana.platform.domain.domain.models.agent.AgentInfo;
import com.ghatana.platform.governance.security.TenantExtractionFilter;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.http.server.server.HttpServerBuilder;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Guardian Threat & Health Scoring Service.
 *
 * <p>Exposes a small JSON API that wraps {@link GuardianAgentAdapter} so that
 * non-Java runtimes (e.g. Guardian's Node backend) can offload threat
 * assessment and health scoring to a canonical Java implementation.</p>
 */
public class GuardianThreatServiceLauncher {

    private static final Logger logger = LoggerFactory.getLogger(GuardianThreatServiceLauncher.class);
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static final String DEFAULT_PORT = "8090";

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("GUARDIAN_THREAT_SERVICE_PORT", DEFAULT_PORT));

        // Initialize metrics
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metrics = MetricsCollectorFactory.create(meterRegistry);

        try {
            HttpServerBuilder.create()
                    .withPort(port)
                    .addFilter(TenantExtractionFilter.lenient())
                    .addAsyncRoute(HttpMethod.POST, "/api/v1/guardian/threat-assessment",
                            request -> handleThreatAssessment(request, metrics))
                    .build()
                    .listen();

            logger.info("Guardian Threat Service started on port {}", port);
        } catch (IOException e) {
            logger.error("Failed to start Guardian Threat Service on port {}", port, e);
            System.exit(1);
        }
    }

    private static ObjectMapper createObjectMapper() {
        return JsonUtils.getDefaultMapper();
    }

    private static Promise<HttpResponse> handleThreatAssessment(HttpRequest request, MetricsCollector metrics) {
        return request.loadBody()
                .then(body -> {
                    try {
                        ThreatAssessmentRequest req = parseRequest(body.asArray());
                        ThreatAssessmentResponse response = evaluateThreat(req, metrics);
                        metrics.incrementCounter("guardian.threat.requests", "result", "success");
                        HttpResponse ok = ResponseBuilder.ok()
                                .json(response)
                                .build();
                        return Promise.of(ok);
                    } catch (Exception e) {
                        logger.error("Failed to process threat assessment request", e);
                        metrics.incrementCounter("guardian.threat.requests", "result", "error");
                        HttpResponse error = ResponseBuilder.badRequest()
                                .json(Map.of(
                                        "error", "INVALID_REQUEST",
                                        "message", e.getMessage()
                                ))
                                .build();
                        return Promise.of(error);
                    }
                });
    }

    private static ThreatAssessmentRequest parseRequest(byte[] body) {
        try {
            return OBJECT_MAPPER.readValue(body, ThreatAssessmentRequest.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    // Package-private for tests
    static ThreatAssessmentResponse evaluateThreat(ThreatAssessmentRequest req, MetricsCollector metrics) {
        AgentInfo agent = new AgentInfo();
        agent.setId(req.agentId());
        agent.setType(req.agentType());
        agent.setStatus(req.agentStatus());
        if (req.agentMetadata() != null) {
            agent.setMetadata(req.agentMetadata());
        }

        GuardianAgentAdapter adapter = GuardianAgentAdapter.create()
                .withCoreAgent(agent)
                .withThreatLevel(GuardianAgentAdapter.ThreatLevel.MEDIUM)
                .withHealthThreshold(0.70)
                .withAlertPattern(GuardianAgentAdapter.AlertPattern.ENSEMBLE)
                .withMetrics(metrics)
                .build();

        GuardianAgentAdapter.ThreatAssessment threat = adapter.assessThreat(req.eventData() != null ? req.eventData() : Map.of());

        Double healthScore = null;
        Boolean unhealthy = null;
        Map<String, Double> deviceMetrics = req.deviceMetrics();
        if (deviceMetrics != null && !deviceMetrics.isEmpty()) {
            healthScore = adapter.calculateHealthScore(deviceMetrics);
            unhealthy = adapter.isUnhealthy(deviceMetrics);
        }

        // Record threat assessment metrics
        try {
            metrics.incrementCounter(
                    "guardian.threat.assessments",
                    "threat_level", threat.threatLevel.name(),
                    "is_threat", Boolean.toString(threat.isThreat)
            );
            if (healthScore != null) {
                metrics.recordConfidenceScore("guardian.threat.health_score", healthScore);
            }
        } catch (Exception ignored) {
            // Metrics must never break core threat assessment path
        }

        return new ThreatAssessmentResponse(
                agent.getId(),
                threat.threatLevel.name(),
                threat.isThreat,
                threat.suspiciousIndicators,
                threat.evidence,
                threat.recommendedAction,
                threat.assessedAt,
                healthScore,
                unhealthy
        );
    }

    // ===== DTOs =====

    public record ThreatAssessmentRequest(
            String agentId,
            String agentType,
            String agentStatus,
            Map<String, String> agentMetadata,
            Map<String, Object> eventData,
            Map<String, Double> deviceMetrics
    ) {
    }

    public record ThreatAssessmentResponse(
            String agentId,
            String threatLevel,
            boolean isThreat,
            int suspiciousIndicators,
            List<String> evidence,
            String recommendedAction,
            Instant assessedAt,
            Double healthScore,
            Boolean unhealthy
    ) {
    }
}
