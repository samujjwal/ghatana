package com.ghatana.phr.repository;

import com.ghatana.phr.model.PatientRecord;
import com.ghatana.phr.model.PatientRecords;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final String SELECT_BY_PATIENT_SQL = """
        SELECT record_id, patient_id, tenant_id, record_type, data_json, created_at, updated_at, created_by, updated_by
        FROM phr_patient_records
        WHERE patient_id = ?
        ORDER BY created_at ASC, record_id ASC
        """;

    private static final String SELECT_BY_ID_SQL = """
        SELECT record_id, patient_id, tenant_id, record_type, data_json, created_at, updated_at, created_by, updated_by
        FROM phr_patient_records
        WHERE record_id = ?
        """;

    private static final String SELECT_BY_TENANT_SQL = """
        SELECT record_id, patient_id, tenant_id, record_type, data_json, created_at, updated_at, created_by, updated_by
        FROM phr_patient_records
        WHERE tenant_id = ?
        ORDER BY created_at ASC, record_id ASC
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO phr_patient_records (record_id, patient_id, tenant_id, record_type, data_json, created_at, updated_at, created_by, updated_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (record_id) DO UPDATE SET
            patient_id = EXCLUDED.patient_id,
            tenant_id = EXCLUDED.tenant_id,
            record_type = EXCLUDED.record_type,
            data_json = EXCLUDED.data_json,
            created_at = EXCLUDED.created_at,
            updated_at = EXCLUDED.updated_at,
            created_by = EXCLUDED.created_by,
            updated_by = EXCLUDED.updated_by
        """;

    private static final String DELETE_SQL = "DELETE FROM phr_patient_records WHERE record_id = ?";

    private final DataSource dataSource;
    private final Map<String, PatientRecord> records = new ConcurrentHashMap<>();

    public PatientRecordRepository() {
        this.dataSource = null;
    }

    public PatientRecordRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        PhrPersistenceSupport.migrate(dataSource);
    }

    public PatientRecords findByPatientId(String patientId) {
        if (dataSource != null) {
            return new PatientRecords(findByPatientIdJdbc(patientId));
        }
        List<PatientRecord> patientRecords = records.values().stream()
            .filter(record -> record.getPatientId().equals(patientId))
            .collect(Collectors.toList());
        return new PatientRecords(patientRecords);
    }

    public PatientRecord findById(String recordId) {
        if (dataSource != null) {
            return findByIdJdbc(recordId);
        }
        return records.get(recordId);
    }

    public void save(PatientRecord record) {
        if (record.getRecordId() == null) {
            record.setRecordId(UUID.randomUUID().toString());
        }
        if (dataSource != null) {
            saveJdbc(record);
            return;
        }
        records.put(record.getRecordId(), record);
    }

    public void delete(String recordId) {
        if (dataSource != null) {
            deleteJdbc(recordId);
            return;
        }
        records.remove(recordId);
    }

    public List<PatientRecord> findByTenantId(String tenantId) {
        if (dataSource != null) {
            return findByTenantIdJdbc(tenantId);
        }
        return records.values().stream()
            .filter(record -> record.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
    }

    private List<PatientRecord> findByPatientIdJdbc(String patientId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_PATIENT_SQL)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PatientRecord> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRecord(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load records for patient: " + patientId, exception);
        }
    }

    private PatientRecord findByIdJdbc(String recordId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setString(1, recordId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapRecord(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load record: " + recordId, exception);
        }
    }

    private List<PatientRecord> findByTenantIdJdbc(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_TENANT_SQL)) {
            statement.setString(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PatientRecord> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapRecord(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load records for tenant: " + tenantId, exception);
        }
    }

    private void saveJdbc(PatientRecord record) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, record.getRecordId());
            statement.setString(2, record.getPatientId());
            statement.setString(3, record.getTenantId());
            statement.setString(4, record.getRecordType());
            statement.setString(5, PhrPersistenceSupport.writeMapJson(record.getData()));
            statement.setTimestamp(6, PhrPersistenceSupport.toTimestamp(record.getCreatedAt()));
            statement.setTimestamp(7, PhrPersistenceSupport.toTimestamp(record.getUpdatedAt()));
            statement.setString(8, record.getCreatedBy());
            statement.setString(9, record.getUpdatedBy());
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save patient record: " + record.getRecordId(), exception);
        }
    }

    private void deleteJdbc(String recordId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, recordId);
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete patient record: " + recordId, exception);
        }
    }

    private static PatientRecord mapRecord(ResultSet resultSet) throws SQLException {
        PatientRecord record = new PatientRecord();
        record.setRecordId(resultSet.getString("record_id"));
        record.setPatientId(resultSet.getString("patient_id"));
        record.setTenantId(resultSet.getString("tenant_id"));
        record.setRecordType(resultSet.getString("record_type"));
        record.setData(PhrPersistenceSupport.readMapJson(resultSet.getString("data_json")));
        record.setCreatedAt(PhrPersistenceSupport.toInstant(resultSet.getTimestamp("created_at")));
        record.setUpdatedAt(PhrPersistenceSupport.toInstant(resultSet.getTimestamp("updated_at")));
        record.setCreatedBy(resultSet.getString("created_by"));
        record.setUpdatedBy(resultSet.getString("updated_by"));
        return record;
    }
}
