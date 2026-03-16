package com.ghatana.appplatform.recon.service;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Fetches bank/custodian account statements from external sources (SFTP and REST).
 *              Credentials are managed via K-14 (credential rotation port). Each fetch is
 *              checksummed (SHA-256) to detect duplicates. Status lifecycle:
 *              SCHEDULED → FETCHING → RECEIVED → PARSED → ERROR.
 *              Fetch attempts are recorded in statement_fetch_log for audit and retry tracking.
 * @doc.layer   Domain
 * @doc.pattern K-14 CredentialPort for credential rotation; idempotency via checksum dedup.
 */
public class StatementFetchService {

    private static final Logger log = LoggerFactory.getLogger(StatementFetchService.class);

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final StatementFetchPort fetchPort;
    private final CredentialPort    credentialPort;
    private final Counter           fetchedCounter;
    private final Counter           duplicateCounter;
    private final Counter           errorCounter;

    public StatementFetchService(HikariDataSource dataSource, Executor executor,
                                 StatementFetchPort fetchPort, CredentialPort credentialPort,
                                 MeterRegistry registry) {
        this.dataSource       = dataSource;
        this.executor         = executor;
        this.fetchPort        = fetchPort;
        this.credentialPort   = credentialPort;
        this.fetchedCounter   = registry.counter("recon.statement_fetch.fetched");
        this.duplicateCounter = registry.counter("recon.statement_fetch.duplicate");
        this.errorCounter     = registry.counter("recon.statement_fetch.error");
    }

    // ─── Inner ports (K-14) ──────────────────────────────────────────────────

    /**
     * Adapter interface for pulling raw statement data from external sources.
     */
    public interface StatementFetchPort {
        /** Pull via SFTP; returns raw file bytes. */
        byte[] fetchViaSftp(String host, int port, String remotePath, SftpCredential credential);
        /** Pull via REST API; returns response body string. */
        String fetchViaRest(String endpointUrl, String bearerToken);
    }

    /**
     * K-14 credential rotation port.
     */
    public interface CredentialPort {
        SftpCredential getSftpCredential(String accountCode);
        String         getBearerToken(String accountCode);
        void           rotateSftpCredential(String accountCode);
    }

    // ─── Records ─────────────────────────────────────────────────────────────

    public record SftpCredential(String username, String privateKeyPath, String passphrase) {}

    public record FetchConfig(
        String accountCode,
        String sourceType,   // SFTP | REST
        String host,
        int    port,
        String remotePath,
        String restEndpointUrl
    ) {}

    public record FetchResult(
        String fetchId,
        String accountCode,
        String status,       // RECEIVED | DUPLICATE | ERROR
        String checksum,
        int    byteCount,
        String rawContent
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Fetch statements for all configured accounts.
     */
    public Promise<List<FetchResult>> fetchAll(List<FetchConfig> configs) {
        return Promise.ofBlocking(executor, () ->
            configs.stream().map(this::fetchSingle).toList()
        );
    }

    /**
     * Fetch a single account statement.
     */
    public Promise<FetchResult> fetch(FetchConfig config) {
        return Promise.ofBlocking(executor, () -> fetchSingle(config));
    }

    // ─── Core logic ──────────────────────────────────────────────────────────

    private FetchResult fetchSingle(FetchConfig config) {
        String fetchId = UUID.randomUUID().toString();
        recordFetchAttempt(fetchId, config.accountCode(), "FETCHING");
        try {
            byte[] rawBytes = switch (config.sourceType()) {
                case "SFTP" -> {
                    SftpCredential cred = credentialPort.getSftpCredential(config.accountCode());
                    yield fetchPort.fetchViaSftp(config.host(), config.port(),
                                                 config.remotePath(), cred);
                }
                case "REST" -> {
                    String token = credentialPort.getBearerToken(config.accountCode());
                    yield fetchPort.fetchViaRest(config.restEndpointUrl(), token)
                                   .getBytes(StandardCharsets.UTF_8);
                }
                default -> throw new IllegalArgumentException("Unknown source type: " + config.sourceType());
            };

            String checksum = sha256(rawBytes);
            if (isDuplicate(config.accountCode(), checksum)) {
                updateFetchStatus(fetchId, "DUPLICATE");
                duplicateCounter.increment();
                return new FetchResult(fetchId, config.accountCode(), "DUPLICATE",
                                       checksum, rawBytes.length, null);
            }

            String rawContent = new String(rawBytes, StandardCharsets.UTF_8);
            updateFetchStatus(fetchId, "RECEIVED");
            persistRawStatement(fetchId, config.accountCode(), checksum, rawContent);
            fetchedCounter.increment();
            log.info("Fetched statement accountCode={} fetchId={} bytes={}",
                     config.accountCode(), fetchId, rawBytes.length);
            return new FetchResult(fetchId, config.accountCode(), "RECEIVED",
                                   checksum, rawBytes.length, rawContent);

        } catch (Exception ex) {
            log.error("Failed to fetch statement accountCode={} fetchId={}", config.accountCode(), fetchId, ex);
            updateFetchStatus(fetchId, "ERROR");
            errorCounter.increment();
            return new FetchResult(fetchId, config.accountCode(), "ERROR", null, 0, null);
        }
    }

    // ─── DB helpers ──────────────────────────────────────────────────────────

    private void recordFetchAttempt(String fetchId, String accountCode, String status) {
        String sql = """
            INSERT INTO statement_fetch_log (fetch_id, account_code, status, attempted_at)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fetchId);
            ps.setString(2, accountCode);
            ps.setString(3, status);
            ps.setObject(4, Instant.now());
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("recordFetchAttempt failed fetchId={}", fetchId, ex);
        }
    }

    private void updateFetchStatus(String fetchId, String status) {
        String sql = "UPDATE statement_fetch_log SET status = ?, updated_at = ? WHERE fetch_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setObject(2, Instant.now());
            ps.setString(3, fetchId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("updateFetchStatus failed fetchId={}", fetchId, ex);
        }
    }

    private boolean isDuplicate(String accountCode, String checksum) {
        String sql = """
            SELECT 1 FROM statement_fetch_log
            WHERE account_code = ? AND checksum = ? AND status IN ('RECEIVED','PARSED')
            LIMIT 1
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accountCode);
            ps.setString(2, checksum);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            log.error("isDuplicate check failed accountCode={}", accountCode, ex);
            return false;
        }
    }

    private void persistRawStatement(String fetchId, String accountCode,
                                     String checksum, String rawContent) {
        String sql = """
            UPDATE statement_fetch_log
            SET checksum = ?, raw_content = ?, status = 'RECEIVED', updated_at = ?
            WHERE fetch_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, checksum);
            ps.setString(2, rawContent);
            ps.setObject(3, Instant.now());
            ps.setString(4, fetchId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            log.error("persistRawStatement failed fetchId={}", fetchId, ex);
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception ex) {
            throw new RuntimeException("SHA-256 unavailable", ex);
        }
    }
}
