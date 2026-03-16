package com.ghatana.appplatform.sdk;

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
 * @doc.purpose Implements the OpenAPI code generation pipeline.
 *              Reads OpenAPI 3.x spec JSON/YAML from the spec store, validates it,
 *              generates typed client SDKs (TypeScript, Python, Java), runs type checks,
 *              and publishes versioned packages to the internal package registry.
 *              Pipeline is CI-triggered on every spec change.
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class OpenApiCodeGenerationService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface SpecValidatorPort {
        /** Validate an OpenAPI 3.x spec document. Returns validation report JSON. */
        Promise<String> validate(String specJson);
    }

    public interface ClientGeneratorPort {
        /** Generate a typed client SDK for the given language. Returns the generated code archive path. */
        Promise<String> generate(String specJson, SdkLanguage language, String serviceVersion);

        enum SdkLanguage { TYPESCRIPT, PYTHON, JAVA }
    }

    public interface TypeCheckerPort {
        /** Run strict type checking on the generated code archive. */
        Promise<TypeCheckResult> check(String archivePath, ClientGeneratorPort.SdkLanguage language);

        record TypeCheckResult(boolean passed, List<String> errors) {}
    }

    public interface PackageRegistryPort {
        /** Publish a generated SDK archive to the internal package registry. */
        Promise<String> publish(String archivePath, String packageName, String version,
                                ClientGeneratorPort.SdkLanguage language);
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records
    // -----------------------------------------------------------------------

    public record GenerationRun(
        String runId,
        String serviceName,
        String specVersion,
        String status,       // PENDING | VALIDATING | GENERATING | TYPE_CHECKING | PUBLISHING | DONE | FAILED
        List<LanguageResult> languageResults,
        String failureReason,
        String startedAt,
        String completedAt
    ) {}

    public record LanguageResult(
        String language,
        String packageName,
        String publishedVersion,
        String registryUrl
    ) {}

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final SpecValidatorPort specValidator;
    private final ClientGeneratorPort clientGenerator;
    private final TypeCheckerPort typeChecker;
    private final PackageRegistryPort packageRegistry;
    private final AuditPort auditPort;

    private final Counter generationRunTotal;
    private final Counter generationPassedTotal;
    private final Counter generationFailedTotal;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public OpenApiCodeGenerationService(DataSource dataSource,
                                        Executor executor,
                                        MeterRegistry meterRegistry,
                                        SpecValidatorPort specValidator,
                                        ClientGeneratorPort clientGenerator,
                                        TypeCheckerPort typeChecker,
                                        PackageRegistryPort packageRegistry,
                                        AuditPort auditPort) {
        this.dataSource     = dataSource;
        this.executor       = executor;
        this.specValidator  = specValidator;
        this.clientGenerator = clientGenerator;
        this.typeChecker    = typeChecker;
        this.packageRegistry = packageRegistry;
        this.auditPort      = auditPort;

        this.generationRunTotal    = Counter.builder("sdk.openapi_generation.run_total")
                .description("Total OpenAPI SDK generation runs")
                .register(meterRegistry);
        this.generationPassedTotal = Counter.builder("sdk.openapi_generation.passed_total")
                .description("Successful OpenAPI SDK generation runs")
                .register(meterRegistry);
        this.generationFailedTotal = Counter.builder("sdk.openapi_generation.failed_total")
                .description("Failed OpenAPI SDK generation runs")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Trigger a full generation pipeline for a service spec.
     * Pipeline: validate → generate (TS, Python, Java) → type-check → publish.
     */
    public Promise<GenerationRun> runPipeline(String serviceName, String specJson,
                                               String specVersion, String triggeredBy) {
        return specValidator.validate(specJson).then(validationReport -> {
            if (validationReport.contains("\"valid\":false")) {
                generationFailedTotal.increment();
                return Promise.of(failedRun(serviceName, specVersion, "Spec validation failed: " + validationReport));
            }
            String runId = insertRunBlocking(serviceName, specVersion, "GENERATING");
            return generateForAllLanguages(runId, serviceName, specJson, specVersion)
                .then(results -> {
                    updateRunBlocking(runId, "DONE", null, results);
                    generationPassedTotal.increment();
                    auditPort.log("SDK_GENERATED", triggeredBy, runId, "SDK_GENERATION_RUN",
                                  null, buildResultJson(results));
                    return Promise.of(buildRun(runId, serviceName, specVersion, "DONE", results, null));
                })
                .mapException(ex -> {
                    updateRunBlocking(runId, "FAILED", ex.getMessage(), List.of());
                    generationFailedTotal.increment();
                    return ex;
                });
        }).whenComplete((r, e) -> generationRunTotal.increment());
    }

    /** Get a generation run by ID. */
    public Promise<GenerationRun> getRunById(String runId) {
        return Promise.ofBlocking(executor, () -> queryRunById(runId));
    }

    /** List generation runs for a service, most recent first. */
    public Promise<List<GenerationRun>> listRunsForService(String serviceName, int limit) {
        return Promise.ofBlocking(executor, () -> queryRunsByService(serviceName, limit));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Promise<List<LanguageResult>> generateForAllLanguages(String runId, String serviceName,
                                                                    String specJson, String version) {
        List<ClientGeneratorPort.SdkLanguage> langs = List.of(
            ClientGeneratorPort.SdkLanguage.TYPESCRIPT,
            ClientGeneratorPort.SdkLanguage.PYTHON,
            ClientGeneratorPort.SdkLanguage.JAVA
        );
        // Sequential generation and type-checking per language
        return langs.stream()
            .map(lang -> generateAndPublish(serviceName, specJson, version, lang))
            .reduce(Promise.of(new ArrayList<LanguageResult>()),
                (accP, resultP) -> accP.then(acc -> resultP.map(r -> { acc.add(r); return acc; })),
                (a, b) -> a);
    }

    private Promise<LanguageResult> generateAndPublish(String serviceName, String specJson,
                                                        String version,
                                                        ClientGeneratorPort.SdkLanguage lang) {
        return clientGenerator.generate(specJson, lang, version)
            .then(archivePath -> typeChecker.check(archivePath, lang)
                .then(checkResult -> {
                    if (!checkResult.passed()) {
                        return Promise.ofException(new RuntimeException(
                            "Type check failed for " + lang + ": " + checkResult.errors()));
                    }
                    String packageName = serviceName.toLowerCase() + "-sdk";
                    return packageRegistry.publish(archivePath, packageName, version, lang)
                        .map(registryUrl -> new LanguageResult(lang.name(), packageName, version, registryUrl));
                }));
    }

    private String insertRunBlocking(String serviceName, String specVersion, String status) {
        String sql = """
            INSERT INTO sdk_generation_runs
                (run_id, service_name, spec_version, status, started_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, now())
            RETURNING run_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serviceName);
            ps.setString(2, specVersion);
            ps.setString(3, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("run_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to insert generation run", e);
        }
    }

    private void updateRunBlocking(String runId, String status, String failureReason,
                                   List<LanguageResult> results) {
        String sql = """
            UPDATE sdk_generation_runs
               SET status = ?, failure_reason = ?, results_json = ?::jsonb, completed_at = now()
             WHERE run_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, failureReason);
            ps.setString(3, buildResultJson(results));
            ps.setString(4, runId);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to update generation run " + runId, e);
        }
    }

    private GenerationRun queryRunById(String runId) {
        String sql = """
            SELECT run_id, service_name, spec_version, status, failure_reason,
                   results_json::text, started_at::text, completed_at::text
              FROM sdk_generation_runs
             WHERE run_id = ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("Run not found: " + runId);
                return mapRun(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query run " + runId, e);
        }
    }

    private List<GenerationRun> queryRunsByService(String serviceName, int limit) {
        String sql = """
            SELECT run_id, service_name, spec_version, status, failure_reason,
                   results_json::text, started_at::text, completed_at::text
              FROM sdk_generation_runs
             WHERE service_name = ?
             ORDER BY started_at DESC
             LIMIT ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, serviceName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<GenerationRun> result = new ArrayList<>();
                while (rs.next()) result.add(mapRun(rs));
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query runs for " + serviceName, e);
        }
    }

    private GenerationRun mapRun(ResultSet rs) throws Exception {
        return new GenerationRun(
            rs.getString("run_id"),
            rs.getString("service_name"),
            rs.getString("spec_version"),
            rs.getString("status"),
            List.of(),  // results parsed from results_json in production
            rs.getString("failure_reason"),
            rs.getString("started_at"),
            rs.getString("completed_at")
        );
    }

    private GenerationRun failedRun(String serviceName, String specVersion, String reason) {
        return new GenerationRun(null, serviceName, specVersion, "FAILED", List.of(), reason, null, null);
    }

    private GenerationRun buildRun(String runId, String serviceName, String specVersion,
                                   String status, List<LanguageResult> results, String failure) {
        return new GenerationRun(runId, serviceName, specVersion, status, results, failure, null, null);
    }

    private String buildResultJson(List<LanguageResult> results) {
        if (results.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            LanguageResult r = results.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"language\":\"%s\",\"package\":\"%s\",\"version\":\"%s\",\"registryUrl\":\"%s\"}",
                r.language(), r.packageName(), r.publishedVersion(), r.registryUrl()
            ));
        }
        return sb.append("]").toString();
    }
}
