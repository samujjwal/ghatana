package com.ghatana.phr.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Data record entity
 *
 * @doc.type record
 * @doc.purpose Data record entity
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public class PatientRecord {
    private String recordId;
    private String patientId;
    private String tenantId;
    private String recordType;
    private Map<String, Object> data = new HashMap<>();
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;

    public PatientRecord() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public PatientRecord(String recordId, String patientId, String tenantId) {
        this();
        this.recordId = recordId;
        this.patientId = patientId;
        this.tenantId = tenantId;
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
