package com.ghatana.phr.observability;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.ExplainabilityFramework;
import com.ghatana.kernel.observability.KernelTelemetryManager;

/**
 * Configuration and setup for PHRTelemetry
 *
 * @doc.type class
 * @doc.purpose Configuration and setup for PHRTelemetry
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class PHRTelemetryConfig {

    public KernelTelemetryManager telemetryManager() {
        return new PHRTelemetryManagerImpl();
    }

    public AuditTrailService auditTrailService() {
        return new PHRAuditTrailServiceImpl();
    }

    public ExplainabilityFramework explainabilityFramework() {
        return new PHRExplainabilityFrameworkImpl();
    }
}
