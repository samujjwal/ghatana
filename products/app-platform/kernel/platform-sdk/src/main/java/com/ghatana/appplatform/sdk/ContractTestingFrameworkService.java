package com.ghatana.appplatform.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Implements consumer-driven contract testing for HTTP and event-based
 *              service contracts.  Consumers define their expected request/response
 *              or event format; producers verify they satisfy all registered contracts
 *              before every deployment.  Contract format is Pact-compatible.
 *              Contracts are stored in the central contract broker.
 *              A CI gate blocks deployment if any contract is broken.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class ContractTestingFrameworkService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface ContractVerifierPort {
        /**
         * Verify that the provider (service) satisfies the contract.
         *
         * @param contract a Pact-format contract JSON
         * @param providerBaseUrl the live or stubbed URL of the provider
         * @return VerificationResult
         */
        Promise<VerificationResult> verifyHttp(String contractJson, String providerBaseUrl);

        Promise<VerificationResult> verifyEvent(String contractJson, String sampleEventJson);

        record VerificationResult(boolean passed, List<String> failures) {}
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    public record Contract(
        String contractId,
        String consumerService,
        String providerService,
        String contractType,    // HTTP | EVENT
        String specJson,        // Pact-compatible JSON
        String contractVersion,
        String status,          // ACTIVE | DEPRECATED
        String createdAt
    ) {}

    public record VerificationRun(
        String runId,
        String contractId,
        String providerVersion,
        String status,           // PENDING | VERIFYING | PASSED | FAILED
        List<String> failures,
        String verifiedAt
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final ContractVerifierPort verifier;
    private final AuditPort auditPort;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Counter contractPublishedTotal;
    private final Counter verificationPassedTotal;
    private final Counter verificationFailedTotal;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public ContractTestingFrameworkService(DataSource dataSource,
                                            Executor executor,
                                            MeterRegistry meterRegistry,
                                            ContractVerifierPort verifier,
                                            AuditPort auditPort) {
        this.dataSource  = dataSource;
        this.executor    = executor;
        this.verifier    = verifier;
        this.auditPort   = auditPort;

        this.contractPublishedTotal   = Counter.builder("sdk.contract.published_total")
                .description("Total contracts published to the broker")
                .register(meterRegistry);
        this.verificationPassedTotal  = Counter.builder("sdk.contract.verification_passed_total")
                .description("Contract verifications that passed")
                .register(meterRegistry);
        this.verificationFailedTotal  = Counter.builder("sdk.contract.verification_failed_total")
                .description("Contract verifications that failed (CI gate trigger)")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Publish a new consumer contract to the broker. */
    public Promise<Contract> publishContract(String consumerService, String providerService,
                                              String contractType, String specJson,
                                              String contractVersion, String publishedBy) {
        return Promise.ofBlocking(executor, () -> {
            String contractId = insertContractBlocking(consumerService, providerService,
                                                       contractType, specJson, contractVersion);
            contractPublishedTotal.increment();
            auditPort.log("CONTRACT_PUBLISHED", publishedBy, contractId, "SDK_CONTRACT",
                          null, specJson);
            return new Contract(contractId, consumerService, providerService, contractType,
                                specJson, contractVersion, "ACTIVE", null);
        });
    }

    /**
     * Verify all active contracts for a provider.
     * Called by CI on provider service deployment.
     * Returns the list of verification runs — any FAILED run should block the deployment.
     */
    public Promise<List<VerificationRun>> verifyForProvider(String providerService,
                                                             String providerVersion,
                                                             String providerBaseUrl) {
        return Promise.ofBlocking(executor, () -> queryContractsForProvider(providerService))
            .then(contracts -> {
                List<Promise<VerificationRun>> runs = contracts.stream()
                    .map(c -> verifySingleContract(c, providerVersion, providerBaseUrl))
                    .toList();
                return Promise.all(runs);
            });
    }

    /** Retrieve all contracts registered for a consumer service. */
    public Promise<List<Contract>> listByConsumer(String consumerService) {
        return Promise.ofBlocking(executor, () -> queryContractsByConsumer(consumerService));
    }

    /** Deprecate a contract version (no longer verified). */
    public Promise<Void> deprecateContract(String contractId, String deprecatedBy) {
        return Promise.ofBlocking(executor, () -> {
            setContractStatus(contractId, "DEPRECATED");
            auditPort.log("CONTRACT_DEPRECATED", deprecatedBy, contractId, "SDK_CONTRACT", null, null);
            return null;
        });
    }

    /** Get the compatibility matrix: consumer → provider → latest verification status. */
    public Promise<List<VerificationRun>> getCompatibilityMatrix() {
        return Promise.ofBlocking(executor, this::queryLatestVerifications);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Promise<VerificationRun> verifySingleContract(Contract contract, String providerVersion,
                                                           String providerBaseUrl) {
        Promise<ContractVerifierPort.VerificationResult> resultP =
            "HTTP".equals(contract.contractType())
                ? verifier.verifyHttp(contract.specJson(), providerBaseUrl)
                : verifier.verifyEvent(contract.specJson(), extractSampleEventJson(contract.specJson()));

        return resultP.map(result -> {
            String status = result.passed() ? "PASSED" : "FAILED";
            if (result.passed()) verificationPassedTotal.increment();
            else verificationFailedTotal.increment();
            String runId = insertRunBlocking(contract.contractId(), providerVersion, status, result.failures());
            return new VerificationRun(runId, contract.contractId(), providerVersion,
                                       status, result.failures(), null);
        });
    }

    private String insertContractBlocking(String consumer, String provider, String type,
                                          String specJson, String version) {
        String sql = """
            INSERT INTO sdk_contracts
                (contract_id, consumer_service, provider_service, contract_type, spec_json,
                 contract_version, status, created_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?::jsonb, ?, 'ACTIVE', now())
            RETURNING contract_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, consumer);
            ps.setString(2, provider);
            ps.setString(3, type);
            ps.setString(4, specJson);
            ps.setString(5, version);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("contract_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert contract", e);
        }
    }

    private String insertRunBlocking(String contractId, String providerVersion,
                                     String status, List<String> failures) {
        String sql = """
            INSERT INTO sdk_contract_verification_runs
                (run_id, contract_id, provider_version, status, failures_json, verified_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?::jsonb, now())
            RETURNING run_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contractId);
            ps.setString(2, providerVersion);
            ps.setString(3, status);
            ps.setString(4, toJsonArray(failures));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("run_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert verification run", e);
        }
    }

    private void setContractStatus(String contractId, String status) {
        String sql = "UPDATE sdk_contracts SET status = ? WHERE contract_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, contractId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update contract status for " + contractId, e);
        }
    }

    private List<Contract> queryContractsForProvider(String providerService) {
        String sql = """
            SELECT contract_id, consumer_service, provider_service, contract_type,
                   spec_json::text, contract_version, status, created_at::text
              FROM sdk_contracts
             WHERE provider_service = ? AND status = 'ACTIVE'
            """;
        return queryContracts(sql, providerService);
    }

    private List<Contract> queryContractsByConsumer(String consumerService) {
        String sql = """
            SELECT contract_id, consumer_service, provider_service, contract_type,
                   spec_json::text, contract_version, status, created_at::text
              FROM sdk_contracts
             WHERE consumer_service = ?
             ORDER BY created_at DESC
            """;
        return queryContracts(sql, consumerService);
    }

    private List<Contract> queryContracts(String sql, String param) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                List<Contract> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new Contract(
                        rs.getString("contract_id"),
                        rs.getString("consumer_service"),
                        rs.getString("provider_service"),
                        rs.getString("contract_type"),
                        rs.getString("spec_json"),
                        rs.getString("contract_version"),
                        rs.getString("status"),
                        rs.getString("created_at")
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query contracts", e);
        }
    }

    private List<VerificationRun> queryLatestVerifications() {
        String sql = """
            SELECT DISTINCT ON (contract_id) run_id, contract_id, provider_version,
                   status, failures_json::text, verified_at::text
              FROM sdk_contract_verification_runs
             ORDER BY contract_id, verified_at DESC
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<VerificationRun> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new VerificationRun(
                    rs.getString("run_id"),
                    rs.getString("contract_id"),
                    rs.getString("provider_version"),
                    rs.getString("status"),
                    parseFailuresJson(rs.getString("failures_json")),
                    rs.getString("verified_at")
                ));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to query latest verifications", e);
        }
    }

    /**
     * Parse a JSON array of failure strings from the stored {@code failures_json} column.
     * Returns an empty list when the value is null or not a valid JSON array.
     */
    private static List<String> parseFailuresJson(String failuresJson) {
        if (failuresJson == null || failuresJson.isBlank()) return List.of();
        try {
            JsonNode arr = MAPPER.readTree(failuresJson);
            if (!arr.isArray()) return List.of();
            List<String> result = new ArrayList<>();
            arr.forEach(n -> result.add(n.asText()));
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * Extract a sample event JSON from a Pact-format event contract spec.
     * Looks for {@code interactions[0].contents} (Pact v3 message format).
     * Falls back to {@code interactions[0].request.body} (HTTP Pact), then to {@code "{}"}
     * so the verifier can at least check structural compliance.
     */
    private static String extractSampleEventJson(String specJson) {
        try {
            JsonNode root = MAPPER.readTree(specJson);
            JsonNode interactions = root.path("interactions");
            if (!interactions.isMissingNode() && interactions.isArray() && !interactions.isEmpty()) {
                JsonNode first = interactions.get(0);
                // Pact v3 message format
                JsonNode contents = first.path("contents");
                if (!contents.isMissingNode()) return MAPPER.writeValueAsString(contents);
                // Pact HTTP format
                JsonNode body = first.path("request").path("body");
                if (!body.isMissingNode()) return MAPPER.writeValueAsString(body);
            }
            // Check for top-level 'example' or 'schema' for non-standard specs
            JsonNode example = root.path("example");
            if (!example.isMissingNode()) return MAPPER.writeValueAsString(example);
        } catch (Exception ignored) {
            // Fall through to empty object
        }
        return "{}";
    }

    private String toJsonArray(List<String> items) {
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }
}
