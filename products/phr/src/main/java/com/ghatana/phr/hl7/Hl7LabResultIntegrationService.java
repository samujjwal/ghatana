package com.ghatana.phr.hl7;

import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.kernel.service.LabResultService;
import com.ghatana.phr.kernel.service.LabResultService.LabObservation;
import com.ghatana.phr.kernel.service.LabResultService.ObservationStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Imports HL7 ORU lab results into the canonical PHR lab-result service
 * @doc.layer product
 * @doc.pattern Service
 */
public final class Hl7LabResultIntegrationService implements KernelLifecycleAware {

    private final LabResultService labResultService;
    private final Hl7OruMessageParser parser;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public Hl7LabResultIntegrationService(LabResultService labResultService) {
        this(labResultService, new Hl7OruMessageParser());
    }

    Hl7LabResultIntegrationService(LabResultService labResultService, Hl7OruMessageParser parser) {
        this.labResultService = labResultService;
        this.parser = parser;
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
        return "phr-hl7-lab-integration";
    }

    public Promise<LabObservation> importObservationMessage(String hl7Message) {
        if (!started.get()) {
            return Promise.ofException(new IllegalStateException("HL7 lab integration not started"));
        }

        Hl7LabResultMessage message = parser.parse(hl7Message);
        Instant observedAt = message.observedAt() != null ? message.observedAt() : Instant.now();

        LabObservation observation = new LabObservation(
            null,
            message.patientId(),
            null,
            message.orderId(),
            message.loincCode(),
            message.observationName(),
            message.observationName(),
            message.value(),
            parseReferenceRangeLow(message.referenceRange()),
            message.unit(),
            message.referenceRange(),
            message.sendingFacility(),
            observedAt,
            observedAt,
            mapStatus(message.status()),
            "Imported from HL7 ORU^R01",
            message.interpretation()
        );

        return labResultService.recordObservation(observation);
    }

    private Double parseReferenceRangeLow(String referenceRange) {
        if (referenceRange == null || referenceRange.isBlank()) {
            return null;
        }
        String normalized = referenceRange.replace('–', '-');
        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex < 0) {
            return null;
        }
        try {
            return Double.parseDouble(normalized.substring(0, separatorIndex));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ObservationStatus mapStatus(String hl7Status) {
        return switch (hl7Status) {
            case "F" -> ObservationStatus.FINAL;
            case "C" -> ObservationStatus.AMENDED;
            case "X" -> ObservationStatus.CANCELLED;
            default -> ObservationStatus.PRELIMINARY;
        };
    }
}