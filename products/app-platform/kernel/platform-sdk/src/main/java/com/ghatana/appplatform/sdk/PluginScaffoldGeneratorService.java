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
 * @doc.purpose CLI scaffold generator for platform plugins.
 *              Generates a complete ready-to-develop plugin project for T1, T2, or T3 tiers.
 *              Output includes: project structure, manifest.json template, test boilerplate,
 *              sandbox configuration pre-set for the tier, CI workflow (GitHub Actions),
 *              and local development bootstrap script.
 *              Command: {@code siddhanta plugin new --type T1|T2|T3 --domain <domain> --name <name>}
 * @doc.layer   Application
 * @doc.pattern Inner-Port
 */
public class PluginScaffoldGeneratorService {

    // -----------------------------------------------------------------------
    // Inner Ports
    // -----------------------------------------------------------------------

    public interface ScaffoldTemplatePort {
        /** Render a scaffold template for the given tier and options. Returns a zip archive path. */
        Promise<String> render(ScaffoldOptions options);
    }

    public interface FileSystemPort {
        /** Extract the generated zip archive to the target directory. */
        Promise<String> extractTo(String archivePath, String targetDirectory);
    }

    public interface AuditPort {
        Promise<Void> log(String action, String actor, String entityId, String entityType,
                          String beforeJson, String afterJson);
    }

    // -----------------------------------------------------------------------
    // Records and Enums
    // -----------------------------------------------------------------------

    public enum PluginTier { T1, T2, T3 }

    public record ScaffoldOptions(
        PluginTier tier,
        String domain,        // trading | compliance | risk | reporting | data
        String pluginName,
        String authorName,
        String authorEmail,
        String targetDirectory,
        boolean includeLocalSandbox  // generate docker-compose for local dev sandbox
    ) {}

    public record ScaffoldResult(
        String scaffoldId,
        PluginTier tier,
        String domain,
        String pluginName,
        String outputDirectory,
        List<String> generatedFiles,
        String manifestPath,
        String ciWorkflowPath,
        String status,          // DONE | FAILED
        String failureReason,
        String generatedAt
    ) {}

    // -----------------------------------------------------------------------
    // Tier sandbox defaults
    // -----------------------------------------------------------------------

    /**
     * Returns the tier-specific sandbox constraints that are pre-configured in the scaffold.
     * T1: no network, no file system, 512MB memory, 30s execution limit.
     * T2: sandboxed data access via approved APIs, 1GB memory, 60s execution limit.
     * T3: configurable per plugin (operator-reviewed), 2GB memory, 120s execution limit.
     */
    public static String sandboxConfigForTier(PluginTier tier) {
        return switch (tier) {
            case T1 -> """
                {
                  "networkAccess": false,
                  "fileSystemAccess": false,
                  "memoryLimitMb": 512,
                  "executionTimeLimitSec": 30,
                  "allowedApis": []
                }
                """;
            case T2 -> """
                {
                  "networkAccess": false,
                  "fileSystemAccess": false,
                  "memoryLimitMb": 1024,
                  "executionTimeLimitSec": 60,
                  "allowedApis": ["rules-engine", "market-data-read", "config-read"]
                }
                """;
            case T3 -> """
                {
                  "networkAccess": "OPERATOR_APPROVED",
                  "fileSystemAccess": "OPERATOR_APPROVED",
                  "memoryLimitMb": 2048,
                  "executionTimeLimitSec": 120,
                  "allowedApis": "OPERATOR_CONFIGURED"
                }
                """;
        };
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final DataSource dataSource;
    private final Executor executor;
    private final ScaffoldTemplatePort templatePort;
    private final FileSystemPort fileSystemPort;
    private final AuditPort auditPort;

    private final Counter scaffoldGeneratedTotal;
    private final Counter scaffoldFailedTotal;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public PluginScaffoldGeneratorService(DataSource dataSource,
                                          Executor executor,
                                          MeterRegistry meterRegistry,
                                          ScaffoldTemplatePort templatePort,
                                          FileSystemPort fileSystemPort,
                                          AuditPort auditPort) {
        this.dataSource    = dataSource;
        this.executor      = executor;
        this.templatePort  = templatePort;
        this.fileSystemPort = fileSystemPort;
        this.auditPort     = auditPort;

        this.scaffoldGeneratedTotal = Counter.builder("sdk.plugin_scaffold.generated_total")
                .description("Total plugin scaffolds generated")
                .register(meterRegistry);
        this.scaffoldFailedTotal    = Counter.builder("sdk.plugin_scaffold.failed_total")
                .description("Failed plugin scaffold generations")
                .register(meterRegistry);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generate a plugin project scaffold matching the given options.
     * Stores the scaffold metadata in the database for audit and replay.
     */
    public Promise<ScaffoldResult> generate(ScaffoldOptions options, String requestedBy) {
        return templatePort.render(options)
            .then(archivePath -> fileSystemPort.extractTo(archivePath, options.targetDirectory())
                .then(outputDir -> Promise.ofBlocking(executor, () -> {
                    List<String> files = buildExpectedFileList(options);
                    String scaffoldId = insertScaffoldBlocking(options, outputDir, files);
                    scaffoldGeneratedTotal.increment();
                    auditPort.log("PLUGIN_SCAFFOLD_GENERATED", requestedBy, scaffoldId,
                                  "SDK_PLUGIN_SCAFFOLD", null, buildOptionsJson(options));
                    String manifestPath = outputDir + "/manifest.json";
                    String ciPath       = outputDir + "/.github/workflows/ci.yml";
                    return new ScaffoldResult(scaffoldId, options.tier(), options.domain(),
                                              options.pluginName(), outputDir, files,
                                              manifestPath, ciPath, "DONE", null, null);
                })))
            .mapException(ex -> {
                scaffoldFailedTotal.increment();
                return new RuntimeException("Scaffold generation failed: " + ex.getMessage(), ex);
            });
    }

    /** List recent scaffold generations (for operator audit view). */
    public Promise<List<ScaffoldResult>> listRecent(int limit) {
        return Promise.ofBlocking(executor, () -> queryRecentScaffolds(limit));
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private List<String> buildExpectedFileList(ScaffoldOptions opts) {
        String name = opts.pluginName();
        List<String> files = new ArrayList<>(List.of(
            "manifest.json",
            "README.md",
            "src/main/" + capitalise(name) + "Plugin.java",
            "src/main/" + capitalise(name) + "PluginLifecycle.java",
            "src/test/" + capitalise(name) + "PluginTest.java",
            "sandbox.json",
            ".github/workflows/ci.yml"
        ));
        if (opts.includeLocalSandbox()) {
            files.add("docker-compose.local.yml");
        }
        if (opts.tier() == PluginTier.T3) {
            files.add("SECURITY_REVIEW.md");
            files.add("COMPLIANCE_CHECKLIST.md");
        }
        return files;
    }

    private String insertScaffoldBlocking(ScaffoldOptions opts, String outputDir,
                                           List<String> files) {
        String sql = """
            INSERT INTO sdk_plugin_scaffolds
                (scaffold_id, plugin_name, tier, domain, output_directory,
                 files_json, generated_at)
            VALUES (gen_random_uuid()::text, ?, ?, ?, ?, ?::jsonb, now())
            RETURNING scaffold_id
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, opts.pluginName());
            ps.setString(2, opts.tier().name());
            ps.setString(3, opts.domain());
            ps.setString(4, outputDir);
            ps.setString(5, toJsonArray(files));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString("scaffold_id");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist scaffold metadata", e);
        }
    }

    private List<ScaffoldResult> queryRecentScaffolds(int limit) {
        String sql = """
            SELECT scaffold_id, plugin_name, tier, domain, output_directory, generated_at::text
              FROM sdk_plugin_scaffolds
             ORDER BY generated_at DESC
             LIMIT ?
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<ScaffoldResult> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new ScaffoldResult(
                        rs.getString("scaffold_id"),
                        PluginTier.valueOf(rs.getString("tier")),
                        rs.getString("domain"),
                        rs.getString("plugin_name"),
                        rs.getString("output_directory"),
                        List.of(),
                        null, null, "DONE", null,
                        rs.getString("generated_at")
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query recent scaffolds", e);
        }
    }

    private String buildOptionsJson(ScaffoldOptions opts) {
        return String.format(
            "{\"tier\":\"%s\",\"domain\":\"%s\",\"pluginName\":\"%s\",\"author\":\"%s\"}",
            opts.tier(), opts.domain(), opts.pluginName(), opts.authorName()
        );
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
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
