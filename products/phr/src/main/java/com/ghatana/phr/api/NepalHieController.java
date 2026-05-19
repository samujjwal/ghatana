package com.ghatana.phr.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.hie.NepalHieIntegrationService;
import com.ghatana.phr.hie.NepalHieSyncResult;
import io.activej.promise.Promise;

/**
 * @doc.type class
 * @doc.purpose Thin API facade for Nepal HIE patient summary submission
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class NepalHieController implements KernelLifecycleAware {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final NepalHieIntegrationService integrationService;

    public NepalHieController(NepalHieIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    public Promise<PhrApiResponse> submitPatientSummary(String patientId, String correlationId) {
        return integrationService.submitPatientSummary(patientId, correlationId)
            .map(result -> PhrApiResponse.json(result.accepted() ? 202 : 502, serialize(result)));
    }

    private String serialize(NepalHieSyncResult result) {
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Nepal HIE sync result", exception);
        }
    }

    @Override
    public Promise<Void> start() {
        return integrationService.start();
    }

    @Override
    public Promise<Void> stop() {
        return integrationService.stop();
    }

    @Override
    public boolean isHealthy() {
        return integrationService.isHealthy();
    }

    @Override
    public String getName() {
        return "NepalHieController";
    }
}
