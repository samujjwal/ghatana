package com.ghatana.phr.hie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.api.FhirApiResponse;
import com.ghatana.phr.fhir.server.FhirBundleSupport;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Coordinates patient summary export from FHIR runtime to Nepal HIE with HL7 transformation and ACK handling
 * @doc.layer product
 * @doc.pattern Service
 */
public class NepalHieIntegrationService implements KernelLifecycleAware {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PhrFhirR4Server fhirServer;
    private final NepalHieClient hieClient;
    private final NepalHieMessageBuilder messageBuilder;
    private final NepalHieConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public NepalHieIntegrationService(
        PhrFhirR4Server fhirServer,
        NepalHieClient hieClient,
        NepalHieMessageBuilder messageBuilder,
        NepalHieConfig config
    ) {
        this.fhirServer = fhirServer;
        this.hieClient = hieClient;
        this.messageBuilder = messageBuilder;
        this.config = config;
    }

    @Override
    public Promise<Void> start() {
        started.set(true);
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started.set(false);
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return started.get();
    }

    @Override
    public String getName() {
        return "phr-nepal-hie-integration";
    }

    public Promise<NepalHieSyncResult> submitPatientSummary(String patientId, String correlationId) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("Nepal HIE integration service is not running"));
        }

        return fhirServer.getResource("Patient", patientId)
            .then(patientResponse -> {
                if (patientResponse.statusCode() != 200) {
                    return Promise.ofException(new IllegalStateException("Patient FHIR resource not found for " + patientId));
                }
                return fhirServer.searchResources("Observation", Map.of("patient", patientId))
                    .then(observationResponse -> fhirServer.searchResources("MedicationRequest", Map.of("subject", patientId))
                        .then(medicationResponse -> fhirServer.searchResources("Immunization", Map.of("patient", patientId))
                            .then(immunizationResponse -> submitBuiltSummary(
                                patientId,
                                correlationId,
                                patientResponse,
                                observationResponse,
                                medicationResponse,
                                immunizationResponse
                            ))));
            });
    }

    private Promise<NepalHieSyncResult> submitBuiltSummary(
        String patientId,
        String correlationId,
        FhirApiResponse patientResponse,
        FhirApiResponse observationResponse,
        FhirApiResponse medicationResponse,
        FhirApiResponse immunizationResponse
    ) {
        String bundle = buildPatientSummaryBundle(patientResponse, observationResponse, medicationResponse, immunizationResponse);
        String hl7Message = messageBuilder.buildPatientSummaryMessage(patientId, correlationId, bundle, config);
        return hieClient.submitMessage(patientId, correlationId, hl7Message)
            .map(ack -> new NepalHieSyncResult(
                patientId,
                ack.messageControlId(),
                ack.acknowledgementCode(),
                ack.accepted(),
                ack.textMessage(),
                hl7Message
            ));
    }

    private String buildPatientSummaryBundle(
        FhirApiResponse patientResponse,
        FhirApiResponse observationResponse,
        FhirApiResponse medicationResponse,
        FhirApiResponse immunizationResponse
    ) {
        try {
            List<String> payloads = new ArrayList<>();
            payloads.add(patientResponse.body());
            appendEntries(payloads, observationResponse.body());
            appendEntries(payloads, medicationResponse.body());
            appendEntries(payloads, immunizationResponse.body());
            return FhirBundleSupport.toCollectionBundle(payloads);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to build patient summary bundle for Nepal HIE", exception);
        }
    }

    private void appendEntries(List<String> payloads, String bundleJson) throws Exception {
        JsonNode bundle = OBJECT_MAPPER.readTree(bundleJson);
        for (JsonNode entry : bundle.path("entry")) {
            payloads.add(OBJECT_MAPPER.writeValueAsString(entry.path("resource")));
        }
    }
}