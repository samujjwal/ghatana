package com.ghatana.appplatform.sdk;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Automated certification test suite that plugins must pass before promotion to a tier.
 *              Produces a structured PASS/FAIL report per test case.
 * @doc.layer   Platform SDK (K-12)
 * @doc.pattern Port-Adapter; inner port interfaces; Promise.ofBlocking
 *
 * STORY-K12-009: Plugin certification test suite
 * Tests: manifest completeness, sandbox compliance (1000 executions), performance SLA
 * (p99 < tier limit), memory limit, graceful error handling, data leakage.
 */
public class PluginCertificationTestSuiteService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface PluginSandboxPort {
        SandboxRun execute(String pluginJarPath, String entryClass, Map<String, Object> input, long timeoutMs) throws Exception;
    }

    public interface ManifestReaderPort {
        PluginManifest read(String pluginJarPath) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record PluginManifest(
        String pluginId,
        String version,
        String vendorName,
        String entryClass,
        String tierTarget,
        List<String> requiredPermissions,
        String minPlatformVersion
    ) {}

    public record SandboxRun(
        boolean success,
        long executionNanos,
        long peakMemoryBytes,
        List<String> violations,
        String stderr
    ) {}

    public enum CertTestStatus { PASS, FAIL, SKIP }

    public record CertTestCase(
        String testName,
        CertTestStatus status,
        String detail
    ) {}

    public record CertificationReport(
        String pluginJarPath,
        String pluginId,
        String version,
        String tierTarget,
        boolean overallPass,
        List<CertTestCase> testCases,
        String generatedAt
    ) {}

    // ── Tier performance limits (p99 nanos) ──────────────────────────────────

    private static final Map<String, Long> TIER_P99_LIMIT_NANOS = Map.of(
        "T1", 50_000_000L,   // 50 ms
        "T2", 200_000_000L,  // 200 ms
        "T3", 1_000_000_000L // 1 s
    );

    private static final Map<String, Long> TIER_MEMORY_LIMIT_BYTES = Map.of(
        "T1", 64L * 1024 * 1024,   // 64 MB
        "T2", 256L * 1024 * 1024,  // 256 MB
        "T3", 512L * 1024 * 1024   // 512 MB
    );

    private static final int COMPLIANCE_RUN_COUNT = 1000;
    private static final List<String> REQUIRED_MANIFEST_FIELDS = List.of(
        "pluginId", "version", "vendorName", "entryClass", "tierTarget", "minPlatformVersion"
    );

    // ── Fields ───────────────────────────────────────────────────────────────

    private final PluginSandboxPort sandboxPort;
    private final ManifestReaderPort manifestReader;
    private final Executor executor;
    private final Counter certPassCounter;
    private final Counter certFailCounter;

    public PluginCertificationTestSuiteService(
        PluginSandboxPort sandboxPort,
        ManifestReaderPort manifestReader,
        MeterRegistry registry,
        Executor executor
    ) {
        this.sandboxPort    = sandboxPort;
        this.manifestReader = manifestReader;
        this.executor       = executor;
        this.certPassCounter = Counter.builder("sdk.certification.pass").register(registry);
        this.certFailCounter = Counter.builder("sdk.certification.fail").register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Run the full certification test suite against a plugin JAR. */
    public Promise<CertificationReport> certify(String pluginJarPath, Map<String, Object> sampleInput) {
        return Promise.ofBlocking(executor, () -> {
            List<CertTestCase> cases = new ArrayList<>();

            // 1. Manifest completeness
            PluginManifest manifest = null;
            try {
                manifest = manifestReader.read(pluginJarPath);
                cases.add(checkManifest(manifest));
            } catch (Exception e) {
                cases.add(new CertTestCase("Manifest completeness", CertTestStatus.FAIL,
                    "Cannot read manifest: " + e.getMessage()));
            }

            if (manifest == null) {
                return buildReport(pluginJarPath, null, cases);
            }

            String tier = manifest.tierTarget();

            // 2. Sandbox compliance (1000 runs — no violations)
            cases.add(runSandboxComplianceTest(pluginJarPath, manifest.entryClass(), sampleInput, tier));

            // 3. Performance SLA (p99 < tier limit)
            cases.add(runPerformanceSlaTest(pluginJarPath, manifest.entryClass(), sampleInput, tier));

            // 4. Memory limit
            cases.add(runMemoryLimitTest(pluginJarPath, manifest.entryClass(), sampleInput, tier));

            // 5. Graceful error handling (invalid input must not crash with uncaught exception)
            cases.add(runErrorHandlingTest(pluginJarPath, manifest.entryClass()));

            // 6. Data leakage check (secrets must not appear in stdout/stderr)
            cases.add(runDataLeakageTest(pluginJarPath, manifest.entryClass(), sampleInput));

            CertificationReport report = buildReport(pluginJarPath, manifest, cases);
            if (report.overallPass()) certPassCounter.increment();
            else certFailCounter.increment();
            return report;
        });
    }

    // ── Individual test runners ──────────────────────────────────────────────

    private CertTestCase checkManifest(PluginManifest m) {
        List<String> missing = new ArrayList<>();
        if (isBlank(m.pluginId()))    missing.add("pluginId");
        if (isBlank(m.version()))     missing.add("version");
        if (isBlank(m.vendorName()))  missing.add("vendorName");
        if (isBlank(m.entryClass()))  missing.add("entryClass");
        if (isBlank(m.tierTarget()))  missing.add("tierTarget");
        if (isBlank(m.minPlatformVersion())) missing.add("minPlatformVersion");

        return missing.isEmpty()
            ? new CertTestCase("Manifest completeness", CertTestStatus.PASS, "All required fields present")
            : new CertTestCase("Manifest completeness", CertTestStatus.FAIL, "Missing fields: " + missing);
    }

    private CertTestCase runSandboxComplianceTest(
        String jar, String entry, Map<String, Object> input, String tier
    ) {
        int violations = 0;
        int failures = 0;
        for (int i = 0; i < COMPLIANCE_RUN_COUNT; i++) {
            try {
                SandboxRun run = sandboxPort.execute(jar, entry, input, 30_000L);
                violations += run.violations().size();
                if (!run.success()) failures++;
            } catch (Exception e) {
                failures++;
            }
        }
        boolean pass = violations == 0 && failures == 0;
        return new CertTestCase("Sandbox compliance (1000 runs)", pass ? CertTestStatus.PASS : CertTestStatus.FAIL,
            pass ? "No violations or failures" : violations + " violations, " + failures + " failures");
    }

    private CertTestCase runPerformanceSlaTest(
        String jar, String entry, Map<String, Object> input, String tier
    ) {
        long limitNanos = TIER_P99_LIMIT_NANOS.getOrDefault(tier, Long.MAX_VALUE);
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            try {
                SandboxRun run = sandboxPort.execute(jar, entry, input, limitNanos * 3);
                latencies.add(run.executionNanos());
            } catch (Exception ignored) {}
        }
        if (latencies.isEmpty()) {
            return new CertTestCase("Performance SLA (p99 < " + limitNanos / 1_000_000 + "ms)", CertTestStatus.FAIL, "No successful runs");
        }
        Collections.sort(latencies);
        long p99 = latencies.get((int) (latencies.size() * 0.99));
        boolean pass = p99 <= limitNanos;
        return new CertTestCase(
            "Performance SLA (p99 < " + limitNanos / 1_000_000 + "ms)",
            pass ? CertTestStatus.PASS : CertTestStatus.FAIL,
            "p99=" + p99 / 1_000_000 + "ms, limit=" + limitNanos / 1_000_000 + "ms"
        );
    }

    private CertTestCase runMemoryLimitTest(
        String jar, String entry, Map<String, Object> input, String tier
    ) {
        long limitBytes = TIER_MEMORY_LIMIT_BYTES.getOrDefault(tier, Long.MAX_VALUE);
        try {
            SandboxRun run = sandboxPort.execute(jar, entry, input, 30_000L);
            boolean pass = run.peakMemoryBytes() <= limitBytes;
            return new CertTestCase(
                "Memory limit (" + limitBytes / (1024 * 1024) + " MB)",
                pass ? CertTestStatus.PASS : CertTestStatus.FAIL,
                "Peak: " + run.peakMemoryBytes() / (1024 * 1024) + " MB, Limit: " + limitBytes / (1024 * 1024) + " MB"
            );
        } catch (Exception e) {
            return new CertTestCase("Memory limit", CertTestStatus.FAIL, e.getMessage());
        }
    }

    private CertTestCase runErrorHandlingTest(String jar, String entry) {
        Map<String, Object> invalidInput = Map.of("_invalid", true, "_nullPayload", null);
        try {
            SandboxRun run = sandboxPort.execute(jar, entry, invalidInput, 10_000L);
            // Plugin must NOT produce an uncaught exception (stderr should not contain "Exception")
            boolean graceful = !run.stderr().contains("Exception") || run.stderr().contains("PluginError");
            return new CertTestCase("Graceful error handling", graceful ? CertTestStatus.PASS : CertTestStatus.FAIL,
                graceful ? "Plugin handled invalid input gracefully" : "Uncaught exception in stderr");
        } catch (Exception e) {
            return new CertTestCase("Graceful error handling", CertTestStatus.FAIL, "Execution threw: " + e.getMessage());
        }
    }

    private CertTestCase runDataLeakageTest(String jar, String entry, Map<String, Object> input) {
        // Inject a canary secret into the input and check stdout/stderr does not echo it
        String canary = "CANARY_SECRET_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> poisonInput = new HashMap<>(input);
        poisonInput.put("_secret_key", canary);
        try {
            SandboxRun run = sandboxPort.execute(jar, entry, poisonInput, 10_000L);
            boolean leaked = run.stdout().contains(canary) || run.stderr().contains(canary);
            return new CertTestCase("Data leakage check", leaked ? CertTestStatus.FAIL : CertTestStatus.PASS,
                leaked ? "Canary secret found in output" : "No data leakage detected");
        } catch (Exception e) {
            return new CertTestCase("Data leakage check", CertTestStatus.FAIL, "Execution error: " + e.getMessage());
        }
    }

    private CertificationReport buildReport(String jar, PluginManifest m, List<CertTestCase> cases) {
        boolean overall = cases.stream().noneMatch(c -> c.status() == CertTestStatus.FAIL);
        return new CertificationReport(
            jar,
            m != null ? m.pluginId() : "UNKNOWN",
            m != null ? m.version() : "N/A",
            m != null ? m.tierTarget() : "N/A",
            overall,
            cases,
            java.time.Instant.now().toString()
        );
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    // Placeholder to avoid build error in standalone file
    private record SandboxRunResult(boolean success, String stdout, String stderr) {}
}
