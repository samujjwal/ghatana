package com.ghatana.phr.api;

import com.ghatana.phr.hie.NepalHieIntegrationService;
import com.ghatana.phr.hie.NepalHieSyncResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NepalHieControllerTest extends EventloopTestBase {

    @Test
    void returnsAcceptedStatusWhenSubmissionIsAccepted() {
        NepalHieIntegrationService service = new NepalHieIntegrationService(null, null, null, null) {
            @Override
            public Promise<NepalHieSyncResult> submitPatientSummary(String patientId, String correlationId) {
                return Promise.of(new NepalHieSyncResult(patientId, "ctrl-4", "AA", true, "Accepted", "MSH|^~\\&|..."));
            }
        };
        NepalHieController controller = new NepalHieController(service);

        NepalHieApiResponse response = runPromise(() -> controller.submitPatientSummary("patient-1", "corr-4"));

        assertEquals(202, response.statusCode());
        assertTrue(response.body().contains("\"acknowledgementCode\":\"AA\""));
    }
}
