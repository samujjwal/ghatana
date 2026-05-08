package com.ghatana.digitalmarketing.persistence.googleads;

import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCredentialRepository;
import com.ghatana.digitalmarketing.application.privacy.ContactEncryptionService;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCredential;
import com.ghatana.digitalmarketing.persistence.campaign.DmPersistenceException;
import io.activej.promise.Promise;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for {@link DmGoogleAdsCredentialRepository} (DMOS-P1-015).
 *
 * <p>Encrypts access and refresh tokens at rest using AES-GCM before storing in database.
 * Decrypts tokens when reading from database.</p>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL adapter for Google Ads credential storage with encryption (DMOS-P1-015)
 * @doc.layer persistence
 * @doc.pattern Repository
 */
public final class PostgresDmGoogleAdsCredentialRepository implements DmGoogleAdsCredentialRepository {

    private final DataSource dataSource;
    private final ContactEncryptionService encryptionService;
    private final Executor executor;

    public PostgresDmGoogleAdsCredentialRepository(DataSource dataSource, ContactEncryptionService encryptionService, Executor executor) {
        this.dataSource = dataSource;
        this.encryptionService = encryptionService;
        this.executor = executor;
    }

    @Override
    public Promise<DmGoogleAdsCredential> save(DmGoogleAdsCredential credential) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO dmos_google_ads_credentials (
                    id, tenant_id, connector_id, access_token, refresh_token,
                    expires_at, scopes, created_at, updated_at, revoked, revoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    access_token = EXCLUDED.access_token,
                    refresh_token = EXCLUDED.refresh_token,
                    expires_at = EXCLUDED.expires_at,
                    scopes = EXCLUDED.scopes,
                    updated_at = EXCLUDED.updated_at,
                    revoked = EXCLUDED.revoked,
                    revoked_at = EXCLUDED.revoked_at
                """;

            String encryptedAccessToken = encryptionService.encrypt(credential.getAccessToken());
            String encryptedRefreshToken = encryptionService.encrypt(credential.getRefreshToken());

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                bindCredential(credential, encryptedAccessToken, encryptedRefreshToken, stmt);
                int updatedRows = stmt.executeUpdate();
                if (updatedRows == 0) {
                    throw new DmPersistenceException("No rows affected while saving credential " + credential.getId(), null);
                }
                return credential;
            } catch (SQLException e) {
                throw new DmPersistenceException("Failed to save Google Ads credential " + credential.getId(), e);
            }
        });
    }

    @Override
    public Promise<Optional<DmGoogleAdsCredential>> findById(String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, connector_id, access_token, refresh_token,
                       expires_at, scopes, created_at, updated_at, revoked, revoked_at
                FROM dmos_google_ads_credentials
                WHERE id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new DmPersistenceException("Failed to find Google Ads credential by id " + id, e);
            }
        });
    }

    @Override
    public Promise<Optional<DmGoogleAdsCredential>> findByConnectorId(String connectorId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                SELECT id, tenant_id, connector_id, access_token, refresh_token,
                       expires_at, scopes, created_at, updated_at, revoked, revoked_at
                FROM dmos_google_ads_credentials
                WHERE connector_id = ?
                """;

            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, connectorId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw new DmPersistenceException("Failed to find Google Ads credential by connectorId " + connectorId, e);
            }
        });
    }

    @Override
    public Promise<DmGoogleAdsCredential> update(DmGoogleAdsCredential credential) {
        return save(credential);
    }

    @Override
    public Promise<Void> delete(String id) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM dmos_google_ads_credentials WHERE id = ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, id);
                stmt.executeUpdate();
                return null;
            } catch (SQLException e) {
                throw new DmPersistenceException("Failed to delete Google Ads credential " + id, e);
            }
        });
    }

    private static void bindCredential(
            DmGoogleAdsCredential credential,
            String encryptedAccessToken,
            String encryptedRefreshToken,
            PreparedStatement stmt) throws SQLException {
        stmt.setString(1, credential.getId());
        stmt.setString(2, credential.getTenantId());
        stmt.setString(3, credential.getConnectorId());
        stmt.setString(4, encryptedAccessToken);
        stmt.setString(5, encryptedRefreshToken);
        stmt.setTimestamp(6, Timestamp.from(credential.getExpiresAt()));
        stmt.setString(7, String.join(",", credential.getScopes()));
        stmt.setTimestamp(8, Timestamp.from(credential.getCreatedAt()));
        stmt.setTimestamp(9, Timestamp.from(credential.getUpdatedAt()));
        stmt.setBoolean(10, credential.isRevoked());
        stmt.setTimestamp(11, credential.getRevokedAt() != null ? Timestamp.from(credential.getRevokedAt()) : null);
    }

    private DmGoogleAdsCredential mapRow(ResultSet rs) throws SQLException {
        String encryptedAccessToken = rs.getString("access_token");
        String encryptedRefreshToken = rs.getString("refresh_token");

        String decryptedAccessToken = encryptionService.decrypt(encryptedAccessToken);
        String decryptedRefreshToken = encryptionService.decrypt(encryptedRefreshToken);

        return DmGoogleAdsCredential.builder()
            .id(rs.getString("id"))
            .tenantId(rs.getString("tenant_id"))
            .connectorId(rs.getString("connector_id"))
            .accessToken(decryptedAccessToken)
            .refreshToken(decryptedRefreshToken)
            .expiresAt(rs.getTimestamp("expires_at").toInstant())
            .scopes(List.of(rs.getString("scopes").split(",")))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .revoked(rs.getBoolean("revoked"))
            .revokedAt(rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toInstant() : null)
            .build();
    }
}
