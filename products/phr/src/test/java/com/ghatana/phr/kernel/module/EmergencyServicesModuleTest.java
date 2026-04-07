package com.ghatana.phr.kernel.module;

import com.ghatana.phr.kernel.service.EmergencyAccessLogService;
import com.ghatana.phr.kernel.service.EmergencyAccessReviewWorkflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @doc.type class
 * @doc.purpose Verifies emergency services module exposes both logging and review workflow surfaces
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EmergencyServicesModule")
class EmergencyServicesModuleTest {

    @Test
    @DisplayName("exposes both emergency logging and review workflow")
    void exposesEmergencyServices() {
        EmergencyAccessReviewWorkflow workflow = new EmergencyAccessReviewWorkflow(
            new EmergencyAccessNotificationSenderAdapter(),
                new EmergencyAccessReviewAuditLoggerAdapter());
        EmergencyAccessLogService service = Mockito.mock(EmergencyAccessLogService.class);
        EmergencyServicesModule module = new EmergencyServicesModule(service, workflow);

        assertEquals("phr-emergency-services", module.getModuleId());
        assertSame(service, module.getEmergencyAccessLogService());
        assertSame(workflow, module.getEmergencyAccessReviewWorkflow());
    }

    private static final class EmergencyAccessNotificationSenderAdapter
            implements com.ghatana.phr.kernel.service.EmergencyAccessNotificationSender {
        @Override
        public io.activej.promise.Promise<Void> notifyComplianceLead(
                com.ghatana.phr.kernel.service.EmergencyAccessReviewCase reviewCase,
                com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent event) {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> scheduleMandatoryReview(
                com.ghatana.phr.kernel.service.EmergencyAccessReviewCase reviewCase,
                com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent event) {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> notifyEscalation(
                com.ghatana.phr.kernel.service.EmergencyAccessReviewCase reviewCase,
                com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent event) {
            return io.activej.promise.Promise.complete();
        }
    }

    private static final class EmergencyAccessReviewAuditLoggerAdapter
            implements com.ghatana.phr.kernel.service.EmergencyAccessReviewAuditLogger {
        @Override
        public io.activej.promise.Promise<Void> logReviewQueued(
                com.ghatana.phr.kernel.service.EmergencyAccessReviewCase reviewCase,
                com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent event) {
            return io.activej.promise.Promise.complete();
        }

        @Override
        public io.activej.promise.Promise<Void> logReviewCompleted(
                com.ghatana.phr.kernel.service.EmergencyAccessReviewCase reviewCase,
                com.ghatana.phr.kernel.service.EmergencyAccessLogService.EmergencyAccessEvent event) {
            return io.activej.promise.Promise.complete();
        }
    }
}