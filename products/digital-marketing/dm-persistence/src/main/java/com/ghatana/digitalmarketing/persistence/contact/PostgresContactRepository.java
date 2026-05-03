package com.ghatana.digitalmarketing.persistence.contact;

import com.ghatana.digitalmarketing.application.contact.ContactRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.contact.Contact;
import com.ghatana.digitalmarketing.domain.contact.ConsentStatus;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL implementation of ContactRepository with PII-safe lookups using email hash.
 *
 * <p>PII-safe implementation (DMOS-P0-001): Stores email_hash and encrypted_email columns.
 * All email lookups use the hash column for privacy compliance.</p>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL contact repository with PII protection
 * @doc.layer product
 * @doc.pattern Repository
 */
public class PostgresContactRepository implements ContactRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresContactRepository.class);

    private final DataSource dataSource;
    private final Executor executor;

    /**
     * Creates a new PostgresContactRepository.
     *
     * @param dataSource the PostgreSQL data source
     * @param executor the executor for blocking operations
     */
    public PostgresContactRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<Contact> save(Contact contact) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO contacts (id, workspace_id, email_hash, encrypted_email, display_name,
                                      consent_status, consent_purpose, consent_recorded_at,
                                      suppressed, created_at, updated_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id, workspace_id) DO UPDATE SET
                    email_hash = EXCLUDED.email_hash,
                    encrypted_email = EXCLUDED.encrypted_email,
                    display_name = EXCLUDED.display_name,
                    consent_status = EXCLUDED.consent_status,
                    consent_purpose = EXCLUDED.consent_purpose,
                    consent_recorded_at = EXCLUDED.consent_recorded_at,
                    suppressed = EXCLUDED.suppressed,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, contact.getId());
                stmt.setString(2, contact.getWorkspaceId().getValue());
                stmt.setString(3, contact.getEmailHash());
                stmt.setString(4, contact.getEncryptedEmail());
                stmt.setString(5, contact.getDisplayName());
                stmt.setString(6, contact.getConsentStatus().name());
                stmt.setString(7, contact.getConsentPurpose());
                stmt.setObject(8, contact.getConsentRecordedAt());
                stmt.setBoolean(9, contact.isSuppressed());
                stmt.setObject(10, contact.getCreatedAt());
                stmt.setObject(11, contact.getUpdatedAt());
                stmt.setString(12, contact.getCreatedBy());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRowToContact(rs);
                    }
                    throw new DmPersistenceException("Failed to save contact", new IllegalStateException("Contact not found after save"));
                }
            } catch (SQLException e) {
                LOG.error("Failed to save contact: {}", contact.getId(), e);
                throw new DmPersistenceException("Failed to save contact", e);
            }
        });
    }

    @Override
    public Promise<Optional<Contact>> findById(DmWorkspaceId workspaceId, String contactId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM contacts WHERE id = ? AND workspace_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, contactId);
                stmt.setString(2, workspaceId.getValue());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToContact(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("Failed to find contact by id: {}", contactId, e);
                throw new DmPersistenceException("Failed to find contact by id", e);
            }
        });
    }

    @Override
    public Promise<Optional<Contact>> findByEmailHash(DmWorkspaceId workspaceId, String emailHash) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM contacts WHERE email_hash = ? AND workspace_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, emailHash);
                stmt.setString(2, workspaceId.getValue());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRowToContact(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                LOG.error("Failed to find contact by email hash: {}", emailHash, e);
                throw new DmPersistenceException("Failed to find contact by email hash", e);
            }
        });
    }

    @Override
    @Deprecated
    public Promise<Optional<Contact>> findByEmail(DmWorkspaceId workspaceId, String email) {
        // Deprecated method - should hash the email first and use findByEmailHash
        // This is a fallback for migration compatibility
        LOG.warn("Deprecated findByEmail called, should use findByEmailHash instead");
        return findByEmailHash(workspaceId, email); // Will work if legacy data still has raw email
    }

    @Override
    public Promise<List<Contact>> listMarketingEligible(DmWorkspaceId workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT * FROM contacts
                WHERE workspace_id = ?
                  AND consent_status = 'GRANTED'
                  AND suppressed = false
                ORDER BY created_at DESC
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, workspaceId.getValue());

                List<Contact> contacts = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        contacts.add(mapRowToContact(rs));
                    }
                }
                return contacts;
            } catch (SQLException e) {
                LOG.error("Failed to list marketing eligible contacts for workspace: {}", workspaceId.getValue(), e);
                throw new DmPersistenceException("Failed to list marketing eligible contacts", e);
            }
        });
    }

    @Override
    public Promise<Integer> countMarketingEligible(DmWorkspaceId workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT COUNT(*) FROM contacts
                WHERE workspace_id = ?
                  AND consent_status = 'GRANTED'
                  AND suppressed = false
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, workspaceId.getValue());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                LOG.error("Failed to count marketing eligible contacts for workspace: {}", workspaceId.getValue(), e);
                throw new DmPersistenceException("Failed to count marketing eligible contacts", e);
            }
        });
    }

    @Override
    public Promise<Boolean> deleteById(DmWorkspaceId workspaceId, String contactId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM contacts WHERE id = ? AND workspace_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, contactId);
                stmt.setString(2, workspaceId.getValue());

                int rowsAffected = stmt.executeUpdate();
                LOG.info("[DMOS] Contact deleted for DSAR: id={} workspace={} rowsAffected={}",
                    contactId, workspaceId.getValue(), rowsAffected);
                return rowsAffected > 0;
            } catch (SQLException e) {
                LOG.error("Failed to delete contact for DSAR: id={} workspace={}", contactId, workspaceId.getValue(), e);
                throw new DmPersistenceException("Failed to delete contact for DSAR", e);
            }
        });
    }

    private Contact mapRowToContact(ResultSet rs) throws SQLException {
        return Contact.builder()
            .id(rs.getString("id"))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .emailHash(rs.getString("email_hash"))
            .encryptedEmail(rs.getString("encrypted_email"))
            .displayName(rs.getString("display_name"))
            .consentStatus(ConsentStatus.valueOf(rs.getString("consent_status")))
            .consentPurpose(rs.getString("consent_purpose"))
            .consentRecordedAt(rs.getObject("consent_recorded_at", java.time.Instant.class))
            .suppressed(rs.getBoolean("suppressed"))
            .createdAt(rs.getObject("created_at", java.time.Instant.class))
            .updatedAt(rs.getObject("updated_at", java.time.Instant.class))
            .createdBy(rs.getString("created_by"))
            .build();
    }
}
