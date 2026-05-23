package com.ghatana.phr.domain.patient;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Patient profile domain entity.
 *
 * @doc.type class
 * @doc.purpose Represents a patient profile in the PHR system
 * @doc.layer product
 * @doc.pattern Domain Entity
 */
public class Patient {

    private final String patientId;
    private final String tenantId;
    private PatientStatus status;
    private final PatientProfile profile;
    private final Instant createdAt;
    private final Instant verifiedAt;
    private final String createdBy;

    private Patient(Builder builder) {
        this.patientId = Objects.requireNonNull(builder.patientId, "patientId must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.profile = Objects.requireNonNull(builder.profile, "profile must not be null");
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.verifiedAt = builder.verifiedAt;
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");
    }

    public String patientId() {
        return patientId;
    }

    public String tenantId() {
        return tenantId;
    }

    public PatientStatus status() {
        return status;
    }

    public PatientProfile profile() {
        return profile;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant verifiedAt() {
        return verifiedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    /**
     * Verify the patient profile.
     *
     * @throws IllegalStateException if profile is already verified
     */
    public void verify() {
        if (status != PatientStatus.PENDING) {
            throw new IllegalStateException("Cannot verify patient in status: " + status);
        }
        this.status = PatientStatus.ACTIVE;
    }

    /**
     * Update patient profile.
     *
     * @param newProfile the new profile
     * @throws IllegalStateException if profile is not active
     */
    public void updateProfile(PatientProfile newProfile) {
        if (status != PatientStatus.ACTIVE) {
            throw new IllegalStateException("Cannot update profile for patient in status: " + status);
        }
        // Profile update logic would go here
    }

    public static Builder builder() {
        return new Builder();
    }

    public static String generatePatientId() {
        return "PAT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static final class Builder {
        private String patientId;
        private String tenantId;
        private PatientStatus status = PatientStatus.PENDING;
        private PatientProfile profile;
        private Instant createdAt = Instant.now();
        private Instant verifiedAt;
        private String createdBy;

        public Builder patientId(String patientId) {
            this.patientId = patientId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder status(PatientStatus status) {
            this.status = status;
            return this;
        }

        public Builder profile(PatientProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder verifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Patient build() {
            return new Patient(this);
        }
    }

    public enum PatientStatus {
        PENDING,
        ACTIVE,
        DEACTIVATED
    }
}
