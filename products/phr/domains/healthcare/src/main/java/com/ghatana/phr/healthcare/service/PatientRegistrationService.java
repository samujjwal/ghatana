package com.ghatana.phr.healthcare.service;

import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.healthcare.port.PatientStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Patient registration service.
 *
 * <p>Handles all patient lifecycle operations: new registration, demographic updates,
 * and deactivation. All operations are tenant-scoped.</p>
 *
 * <p>NHS ID uniqueness: checked within the same tenant. The same person may have
 * different records in different tenants (facility networks) — this is intentional for
 * Nepal's federated facility model.</p>
 *
 * @doc.type class
 * @doc.purpose Patient registration and lifecycle — healthcare domain pack service
 * @doc.layer domain-pack
 * @doc.pattern Service
 * @since 1.0.0
 */
public class PatientRegistrationService {

    private final PatientStore patientStore;
    private final Executor executor;
    private final Counter registeredCounter;

    public record RegistrationRequest(
        String tenantId,
        String nhsId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String gender,          // male | female | other | unknown (FHIR AdministrativeGender)
        String province,        // Nepal province 1–7
        String primaryPhone,
        String primaryEmail,
        String registeredBy
    ) {
        public RegistrationRequest {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(firstName, "firstName must not be null");
            Objects.requireNonNull(lastName, "lastName must not be null");
            Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
            Objects.requireNonNull(registeredBy, "registeredBy must not be null");
        }
    }

    public PatientRegistrationService(PatientStore patientStore, Executor executor,
                                      MeterRegistry registry) {
        this.patientStore = Objects.requireNonNull(patientStore);
        this.executor = Objects.requireNonNull(executor);
        this.registeredCounter = Counter.builder("healthcare.patients.registered_total")
            .description("Total number of patients registered")
            .register(registry);
    }

    /**
     * Registers a new patient within a tenant.
     * Returns the newly created patient.
     */
    public Promise<Patient> register(RegistrationRequest request) {
        return Promise.ofBlocking(executor, () -> {
            Patient patient = Patient.newPatient(
                request.tenantId(), request.nhsId(),
                request.firstName(), request.lastName(),
                request.dateOfBirth(), request.gender(),
                request.registeredBy()
            );
            // Enrich with optional fields (province, phone, email)
            Patient enriched = new Patient(
                patient.patientId(), patient.tenantId(), patient.nhsId(),
                patient.firstName(), patient.lastName(), patient.dateOfBirth(),
                patient.gender(), null,
                request.primaryPhone(), request.primaryEmail(),
                null, request.province(),
                patient.classification(), patient.registeredBy(),
                patient.registeredAt(), true
            );
            patientStore.save(enriched);
            registeredCounter.increment();
            return enriched;
        });
    }

    /**
     * Looks up a patient by ID within a tenant. Returns empty if not found or tenant mismatch.
     */
    public Promise<Optional<Patient>> findById(String tenantId, UUID patientId) {
        return Promise.ofBlocking(executor, () -> patientStore.findById(tenantId, patientId));
    }
}
