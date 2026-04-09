package com.ghatana.phr.repository;

import com.ghatana.phr.model.PatientConsent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data access layer for Consent
 *
 * @doc.type class
 * @doc.purpose Data access layer for Consent
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ConsentRepository {
    private static final String SELECT_BY_PATIENT_AND_PURPOSE_SQL = """
        SELECT consent_id, patient_id, tenant_id, purpose, granted, timestamp, expires_at, granted_by
        FROM phr_patient_consents
        WHERE patient_id = ? AND purpose = ?
        ORDER BY timestamp DESC
        LIMIT 1
        """;

    private static final String SELECT_BY_PATIENT_SQL = """
        SELECT consent_id, patient_id, tenant_id, purpose, granted, timestamp, expires_at, granted_by
        FROM phr_patient_consents
        WHERE patient_id = ?
        ORDER BY timestamp DESC
        """;

    private static final String SELECT_BY_ID_SQL = """
        SELECT consent_id, patient_id, tenant_id, purpose, granted, timestamp, expires_at, granted_by
        FROM phr_patient_consents
        WHERE consent_id = ?
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO phr_patient_consents (consent_id, patient_id, tenant_id, purpose, granted, timestamp, expires_at, granted_by)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (consent_id) DO UPDATE SET
            patient_id = EXCLUDED.patient_id,
            tenant_id = EXCLUDED.tenant_id,
            purpose = EXCLUDED.purpose,
            granted = EXCLUDED.granted,
            timestamp = EXCLUDED.timestamp,
            expires_at = EXCLUDED.expires_at,
            granted_by = EXCLUDED.granted_by
        """;

    private static final String DELETE_SQL = "DELETE FROM phr_patient_consents WHERE consent_id = ?";

    private final DataSource dataSource;
    private final Map<String, PatientConsent> consents = new ConcurrentHashMap<>();

    public ConsentRepository() {
        this.dataSource = null;
    }

    public ConsentRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
        PhrPersistenceSupport.migrate(dataSource);
    }

    public PatientConsent findByPatientAndPurpose(String patientId, String purpose) {
        if (dataSource != null) {
            return findByPatientAndPurposeJdbc(patientId, purpose);
        }
        return consents.values().stream()
            .filter(consent -> consent.getPatientId().equals(patientId)
                && consent.getPurpose().equals(purpose))
            .findFirst()
            .orElse(null);
    }

    public List<PatientConsent> findByPatientId(String patientId) {
        if (dataSource != null) {
            return findByPatientIdJdbc(patientId);
        }
        return consents.values().stream()
            .filter(consent -> consent.getPatientId().equals(patientId))
            .collect(Collectors.toList());
    }

    public void save(PatientConsent consent) {
        if (consent.getConsentId() == null) {
            consent.setConsentId(UUID.randomUUID().toString());
        }
        if (dataSource != null) {
            saveJdbc(consent);
            return;
        }
        consents.put(consent.getConsentId(), consent);
    }

    public void delete(String consentId) {
        if (dataSource != null) {
            deleteJdbc(consentId);
            return;
        }
        consents.remove(consentId);
    }

    public PatientConsent findById(String consentId) {
        if (dataSource != null) {
            return findByIdJdbc(consentId);
        }
        return consents.get(consentId);
    }

    private PatientConsent findByPatientAndPurposeJdbc(String patientId, String purpose) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_PATIENT_AND_PURPOSE_SQL)) {
            statement.setString(1, patientId);
            statement.setString(2, purpose);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapConsent(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load consent for patient: " + patientId, exception);
        }
    }

    private List<PatientConsent> findByPatientIdJdbc(String patientId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_PATIENT_SQL)) {
            statement.setString(1, patientId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PatientConsent> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(mapConsent(resultSet));
                }
                return results;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load consents for patient: " + patientId, exception);
        }
    }

    private PatientConsent findByIdJdbc(String consentId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setString(1, consentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapConsent(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load consent: " + consentId, exception);
        }
    }

    private void saveJdbc(PatientConsent consent) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, consent.getConsentId());
            statement.setString(2, consent.getPatientId());
            statement.setString(3, consent.getTenantId());
            statement.setString(4, consent.getPurpose());
            statement.setBoolean(5, consent.isGranted());
            statement.setTimestamp(6, PhrPersistenceSupport.toTimestamp(consent.getTimestamp()));
            statement.setTimestamp(7, PhrPersistenceSupport.toTimestamp(consent.getExpiresAt()));
            statement.setString(8, consent.getGrantedBy());
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save consent: " + consent.getConsentId(), exception);
        }
    }

    private void deleteJdbc(String consentId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, consentId);
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete consent: " + consentId, exception);
        }
    }

    private static PatientConsent mapConsent(ResultSet resultSet) throws SQLException {
        PatientConsent consent = new PatientConsent();
        consent.setConsentId(resultSet.getString("consent_id"));
        consent.setPatientId(resultSet.getString("patient_id"));
        consent.setTenantId(resultSet.getString("tenant_id"));
        consent.setPurpose(resultSet.getString("purpose"));
        consent.setGranted(resultSet.getBoolean("granted"));
        consent.setTimestamp(PhrPersistenceSupport.toInstant(resultSet.getTimestamp("timestamp")));
        consent.setExpiresAt(PhrPersistenceSupport.toInstant(resultSet.getTimestamp("expires_at")));
        consent.setGrantedBy(resultSet.getString("granted_by"));
        return consent;
    }
}
