package com.ghatana.yappc.services.generate;

import com.ghatana.yappc.domain.generate.Artifact;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * @doc.type class
 * @doc.purpose Runs deterministic assurance checks over generated artifacts before Generate can complete
 * @doc.layer service
 * @doc.pattern Service
 */
public class GenerationAssuranceService {

    private static final List<String> CHECK_IDS = List.of(
            "compile",
            "test",
            "static",
            "security",
            "i18n",
            "a11y");

    public GenerationAssuranceReport assure(ValidatedSpec spec, GeneratedArtifacts artifacts) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(artifacts, "artifacts must not be null");

        List<GenerationAssuranceCheck> checks = new ArrayList<>();
        checks.add(checkCompileReadiness(artifacts));
        checks.add(checkTestReadiness(artifacts));
        checks.add(checkStaticReadiness(artifacts));
        checks.add(checkSecurity(artifacts));
        checks.add(checkI18n(artifacts));
        checks.add(checkA11y(artifacts));

        boolean passed = checks.stream().allMatch(GenerationAssuranceCheck::passed);
        return new GenerationAssuranceReport(passed, checks);
    }

    private GenerationAssuranceCheck checkCompileReadiness(GeneratedArtifacts artifacts) {
        List<String> failures = new ArrayList<>();
        for (Artifact artifact : safeArtifacts(artifacts)) {
            if (artifact == null) {
                failures.add("generated artifact list contains null entry");
                continue;
            }
            if (isBlank(artifact.language())) {
                failures.add(artifactLabel(artifact) + " is missing language");
            }
            if (artifact.sizeBytes() <= 0) {
                failures.add(artifactLabel(artifact) + " has no generated content");
            }
        }
        if (safeArtifacts(artifacts).isEmpty()) {
            failures.add("no artifacts generated");
        }
        return check("compile", failures);
    }

    private GenerationAssuranceCheck checkTestReadiness(GeneratedArtifacts artifacts) {
        boolean hasCode = safeArtifacts(artifacts).stream()
                .filter(Objects::nonNull)
                .anyMatch(artifact -> "code".equalsIgnoreCase(artifact.type()));
        boolean hasPipeline = safeArtifacts(artifacts).stream()
                .filter(Objects::nonNull)
                .anyMatch(artifact -> "pipeline".equalsIgnoreCase(artifact.type()));
        List<String> failures = new ArrayList<>();
        if (hasCode && !hasPipeline) {
            failures.add("code generation requires a CI/test pipeline artifact");
        }
        return check("test", failures);
    }

    private GenerationAssuranceCheck checkStaticReadiness(GeneratedArtifacts artifacts) {
        List<String> failures = new ArrayList<>();
        for (Artifact artifact : safeArtifacts(artifacts)) {
            if (artifact == null) {
                failures.add("generated artifact list contains null entry");
                continue;
            }
            if (isBlank(artifact.id())) {
                failures.add("artifact id is required");
            }
            if (isBlank(artifact.name())) {
                failures.add(artifactLabel(artifact) + " is missing name");
            }
            if (isBlank(artifact.type())) {
                failures.add(artifactLabel(artifact) + " is missing type");
            }
            if (isBlank(artifact.path())) {
                failures.add(artifactLabel(artifact) + " is missing path");
            }
            if (isBlank(artifact.contentRef())) {
                failures.add(artifactLabel(artifact) + " is missing content reference");
            }
        }
        return check("static", failures);
    }

    private GenerationAssuranceCheck checkSecurity(GeneratedArtifacts artifacts) {
        List<String> failures = new ArrayList<>();
        for (Artifact artifact : safeArtifacts(artifacts)) {
            if (artifact == null) {
                failures.add("generated artifact list contains null entry");
                continue;
            }
            String path = artifact.path();
            if (path != null && (path.contains("..") || path.startsWith("/") || path.startsWith("\\"))) {
                failures.add(artifactLabel(artifact) + " has unsafe output path");
            }
            String lowerPath = path == null ? "" : path.toLowerCase(Locale.ROOT);
            if (lowerPath.contains("secret") || lowerPath.contains(".env")) {
                failures.add(artifactLabel(artifact) + " targets a sensitive path");
            }
        }
        return check("security", failures);
    }

    private GenerationAssuranceCheck checkI18n(GeneratedArtifacts artifacts) {
        List<String> failures = new ArrayList<>();
        boolean hasUserFacingArtifact = safeArtifacts(artifacts).stream()
                .filter(Objects::nonNull)
                .anyMatch(this::isUserFacing);
        boolean hasMetadata = artifacts.metadata() != null
                && "true".equalsIgnoreCase(artifacts.metadata().get("i18n_ready"));
        if (hasUserFacingArtifact && !hasMetadata) {
            failures.add("user-facing generation requires i18n readiness metadata");
        }
        return check("i18n", failures);
    }

    private GenerationAssuranceCheck checkA11y(GeneratedArtifacts artifacts) {
        List<String> failures = new ArrayList<>();
        boolean hasWebArtifact = safeArtifacts(artifacts).stream()
                .filter(Objects::nonNull)
                .anyMatch(this::isUserFacing);
        boolean hasMetadata = artifacts.metadata() != null
                && "true".equalsIgnoreCase(artifacts.metadata().get("a11y_ready"));
        if (hasWebArtifact && !hasMetadata) {
            failures.add("web generation requires a11y readiness metadata");
        }
        return check("a11y", failures);
    }

    private GenerationAssuranceCheck check(String id, List<String> failures) {
        return new GenerationAssuranceCheck(id, failures.isEmpty(), List.copyOf(failures));
    }

    private boolean isUserFacing(Artifact artifact) {
        String path = artifact.path() == null ? "" : artifact.path().toLowerCase(Locale.ROOT);
        String type = artifact.type() == null ? "" : artifact.type().toLowerCase(Locale.ROOT);
        return type.contains("ui")
                || path.endsWith(".tsx")
                || path.endsWith(".jsx")
                || path.contains("/components/")
                || path.contains("/routes/");
    }

    private static List<Artifact> safeArtifacts(GeneratedArtifacts artifacts) {
        return artifacts.artifacts() == null ? List.of() : artifacts.artifacts();
    }

    private static String artifactLabel(Artifact artifact) {
        if (artifact == null) {
            return "unknown artifact";
        }
        if (!isBlank(artifact.name())) {
            return artifact.name();
        }
        return isBlank(artifact.path()) ? "unknown artifact" : artifact.path();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * @doc.type record
     * @doc.purpose Result of the Generate assurance pipeline
     * @doc.layer service
     * @doc.pattern DTO
     */
    public record GenerationAssuranceReport(
            boolean passed,
            List<GenerationAssuranceCheck> checks
    ) {
        public GenerationAssuranceReport {
            checks = List.copyOf(checks);
        }

        public String failedCheckIds() {
            return checks.stream()
                    .filter(check -> !check.passed())
                    .map(GenerationAssuranceCheck::id)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
        }

        public String passedCheckIds() {
            return checks.stream()
                    .filter(GenerationAssuranceCheck::passed)
                    .map(GenerationAssuranceCheck::id)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
        }
    }

    /**
     * @doc.type record
     * @doc.purpose Individual Generate assurance check outcome
     * @doc.layer service
     * @doc.pattern DTO
     */
    public record GenerationAssuranceCheck(
            String id,
            boolean passed,
            List<String> failures
    ) {
        public GenerationAssuranceCheck {
            if (!CHECK_IDS.contains(id)) {
                throw new IllegalArgumentException("unknown assurance check id: " + id);
            }
            failures = List.copyOf(failures);
        }
    }
}
