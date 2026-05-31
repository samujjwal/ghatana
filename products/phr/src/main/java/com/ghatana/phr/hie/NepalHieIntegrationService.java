package com.ghatana.phr.hie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.api.PhrApiResponse;
import com.ghatana.phr.fhir.server.FhirBundleSupport;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Coordinates patient summary export from FHIR runtime to Nepal HIE with HL7 transformation and ACK handling
 * @doc.layer product
 * @doc.pattern Service
 */
public class NepalHieIntegrationService implements KernelLifecycleAware, HieIntegrationContract {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String CONTRACT_ID = "configured-hl7-fhir-hie";

    private final PhrFhirR4Server fhirServer;
    private final NepalHieClient hieClient;
    private final NepalHieMessageBuilder messageBuilder;
    private final NepalHieConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, HieIntegrationStatus> statuses = new ConcurrentHashMap<>();

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

    @Override
    public String contractId() {
        return CONTRACT_ID;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(Operation.EXPORT, Operation.SYNC);
    }

    @Override
    public Promise<HieIntegrationResult> submit(HieIntegrationRequest request) {
        if (request == null || request.operation() == null) {
            return Promise.ofException(new IllegalArgumentException("HIE operation is required"));
        }
        if (!supportedOperations().contains(request.operation())) {
            HieIntegrationResult result = new HieIntegrationResult(
                request.correlationId(),
                request.operation(),
                contractId(),
                "REJECTED",
                false,
                "HIE_OPERATION_NOT_SUPPORTED",
                "Configured HIE contract does not support this operation"
            );
            statuses.put(result.requestId(), new HieIntegrationStatus(
                result.requestId(), result.operation(), result.contractId(), result.status(), result.safeReasonCode(), result.message()));
            return Promise.of(result);
        }

        return submitPatientSummary(request.patientId(), request.correlationId())
            .map(syncResult -> {
                String status = syncResult.accepted() ? "ACCEPTED" : "REJECTED";
                String reason = syncResult.accepted() ? "HIE_ACCEPTED" : "HIE_REJECTED";
                HieIntegrationResult result = new HieIntegrationResult(
                    syncResult.messageControlId(),
                    request.operation(),
                    contractId(),
                    status,
                    syncResult.accepted(),
                    reason,
                    syncResult.message()
                );
                statuses.put(result.requestId(), new HieIntegrationStatus(
                    result.requestId(), result.operation(), result.contractId(), result.status(), result.safeReasonCode(), result.message()));
                return result;
            });
    }

    @Override
    public Promise<HieIntegrationStatus> getStatus(String requestId, String correlationId) {
        HieIntegrationStatus status = statuses.get(requestId);
        if (status != null) {
            return Promise.of(status);
        }
        return Promise.of(new HieIntegrationStatus(
            requestId,
            null,
            contractId(),
            "UNKNOWN",
            "HIE_REQUEST_UNKNOWN",
            "No HIE operation status was found for this request"
        ));
    }

    private Promise<NepalHieSyncResult> submitBuiltSummary(
        String patientId,
        String correlationId,
        PhrApiResponse patientResponse,
        PhrApiResponse observationResponse,
        PhrApiResponse medicationResponse,
        PhrApiResponse immunizationResponse
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
        PhrApiResponse patientResponse,
        PhrApiResponse observationResponse,
        PhrApiResponse medicationResponse,
        PhrApiResponse immunizationResponse
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
