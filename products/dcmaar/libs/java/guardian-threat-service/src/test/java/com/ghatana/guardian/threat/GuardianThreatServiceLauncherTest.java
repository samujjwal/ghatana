package com.ghatana.guardian.threat;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianThreatServiceLauncherTest {

    @Test
    void shouldEvaluateThreatAndHealthFromRequest() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCollector metrics = MetricsCollectorFactory.create(registry);

        GuardianThreatServiceLauncher.ThreatAssessmentRequest request =
                new GuardianThreatServiceLauncher.ThreatAssessmentRequest(
                        "device-1",
                        "DESKTOP_AGENT",
                        "ACTIVE",
                        Map.of("os", "macOS"),
                        Map.of(
                                "malware_detected", true,
                                "unauthorized_access", true
                        ),
                        Map.of(
                                "cpu_usage", 50.0,
                                "ram_usage", 40.0,
                                "storage_usage", 30.0
                        )
                );

        GuardianThreatServiceLauncher.ThreatAssessmentResponse response =
                GuardianThreatServiceLauncher.evaluateThreat(request, metrics);

        assertEquals("device-1", response.agentId());
        assertTrue(response.isThreat());
        // malware_detected (3) + unauthorized_access (2) = 5 → CRITICAL
        assertEquals("CRITICAL", response.threatLevel());
        assertEquals(5, response.suspiciousIndicators());
        assertNotNull(response.healthScore());
        assertNotNull(response.unhealthy());
    }
}
