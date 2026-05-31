package com.ghatana.phr.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PHR Telemetry Manager")
class PHRTelemetryManagerImplTest {

    @Test
    @DisplayName("drops unsafe high-cardinality PHI tag names")
    void dropsUnsafeHighCardinalityPhiTagNames() {
        PHRTelemetryManagerImpl telemetry = new PHRTelemetryManagerImpl();

        telemetry.incrementCounter(
            "phr.patient.records.created",
            1,
            "patient_id", "patient-123",
            "tenant_id", "tenant-123",
            "resource_type", "patient-record"
        );

        String key = telemetry.counterSnapshot().keySet().iterator().next();
        assertThat(key).contains("resource_type=patient-record");
        assertThat(key).doesNotContain("patient_id");
        assertThat(key).doesNotContain("tenant_id");
        assertThat(key).doesNotContain("patient-123");
        assertThat(key).doesNotContain("tenant-123");
    }
}
