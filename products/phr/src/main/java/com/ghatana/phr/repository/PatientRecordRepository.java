package com.ghatana.phr.repository;

import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.PatientRecords;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Data access layer for PatientRecord
 *
 * @doc.type record
 * @doc.purpose Data access layer for PatientRecord
 * @doc.layer product
 * @doc.pattern Repository
 */
public class PatientRecordRepository {
    private final Map<String, PatientRecord> records = new HashMap<>();

    public PatientRecords findByPatientId(String patientId) {
        List<PatientRecord> patientRecords = records.values().stream()
            .filter(record -> record.getPatientId().equals(patientId))
            .collect(Collectors.toList());
        return new PatientRecords(patientRecords);
    }

    public PatientRecord findById(String recordId) {
        return records.get(recordId);
    }

    public void save(PatientRecord record) {
        if (record.getRecordId() == null) {
            record.setRecordId(UUID.randomUUID().toString());
        }
        records.put(record.getRecordId(), record);
    }

    public void delete(String recordId) {
        records.remove(recordId);
    }

    public List<PatientRecord> findByTenantId(String tenantId) {
        return records.values().stream()
            .filter(record -> record.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
    }
}
