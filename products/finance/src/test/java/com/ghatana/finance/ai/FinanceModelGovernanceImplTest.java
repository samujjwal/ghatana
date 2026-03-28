package com.ghatana.finance.ai;

import com.ghatana.kernel.ai.ModelGovernanceService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceModelGovernanceImplTest {

    @Test
    void shouldRecordPerformanceAndAlertOnDegradation() {
        ModelApprovalRepository approvals = new ModelApprovalRepository();
        ModelPerformanceRepository performance = new ModelPerformanceRepository();
        CapturingAlertService alerts = new CapturingAlertService();
        FinanceModelGovernanceImpl governance = new FinanceModelGovernanceImpl(approvals, performance, alerts);

        governance.recordModelPerformance(
            "risk-model-v1",
            new ModelGovernanceService.ModelPerformanceMetrics(0.55, 0.72, 3_000L, java.util.Map.of())
        );

        assertEquals(1, performance.findByModelId("risk-model-v1").size());
        assertEquals(1, alerts.alertCount);
        assertTrue(alerts.lastMessage.contains("risk-model-v1"));
    }

    @Test
    void shouldEvaluateComplianceAgainstMetadata() {
        FinanceModelGovernanceImpl governance = new FinanceModelGovernanceImpl(
            new ModelApprovalRepository(),
            new ModelPerformanceRepository(),
            new CapturingAlertService()
        );
        governance.registerModel(new ModelGovernanceService.ModelRegistration(
            "model-approved",
            "Approved Model",
            "1.0.0",
            "risk",
            java.util.Map.of("jurisdiction", "np")
        ));

        assertTrue(governance.isModelCompliant("model-approved", new JurisdictionPolicy("np")));
        assertFalse(governance.isModelCompliant("model-approved", new JurisdictionPolicy("us")));
    }

    private static final class CapturingAlertService extends AlertService {
        private int alertCount;
        private String lastMessage = "";

        @Override
        public void sendAlert(String title, String message) {
            alertCount++;
            lastMessage = title + ":" + message;
        }
    }

    private record JurisdictionPolicy(String expectedJurisdiction) implements ModelGovernanceService.CompliancePolicy {
        @Override
        public String getPolicyId() {
            return "jurisdiction";
        }

        @Override
        public String getName() {
            return "Jurisdiction policy";
        }

        @Override
        public boolean evaluate(ModelGovernanceService.ModelMetadata metadata) {
            return expectedJurisdiction.equals(metadata.getAttributes().get("jurisdiction"));
        }

        @Override
        public java.util.List<String> getRequirements() {
            return java.util.List.of("jurisdiction=" + expectedJurisdiction);
        }
    }
}
