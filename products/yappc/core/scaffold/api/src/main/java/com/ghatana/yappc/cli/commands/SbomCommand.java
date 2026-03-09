package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.security.SBOMGenerator;
import com.ghatana.yappc.core.security.SBOMGenerator.SBOMDocument;
import com.ghatana.yappc.core.security.SBOMGenerator.SLSAProvenance;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * SBOM (Software Bill of Materials) Command Week 11 Day 52: Generate SBOM and SLSA provenance
 * documents
 */
@Command(
        name = "sbom",
        description = "Generate Software Bill of Materials (SBOM) and SLSA provenance",
        subcommands = {
            SbomCommand.GenerateCommand.class,
            SbomCommand.VerifyCommand.class,
            SbomCommand.ScanCommand.class
        })
/**
 * SbomCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose SbomCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class SbomCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(SbomCommand.class);

    @Override
    public Integer call() {
        logger.info("YAPPC SBOM Generator");
        logger.info("Generate Software Bill of Materials and SLSA provenance");
        logger.info("");;
        logger.info("Available commands:");
        logger.info("  generate  - Generate SBOM and provenance documents");
        logger.info("  verify    - Verify SBOM integrity");
        logger.info("  scan      - Scan for vulnerabilities");
        return 0;
    }

    @Command(name = "generate", description = "Generate SBOM and SLSA provenance documents")
    static class GenerateCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project root directory", defaultValue = ".")
        private String projectPath;

        @Option(
                names = {"-o", "--output"},
                description = "Output directory",
                defaultValue = ".security")
        private String outputDir;

        @Option(
                names = {"--sbom-format"},
                description = "SBOM format (spdx-json, cyclonedx)",
                defaultValue = "spdx-json")
        private String sbomFormat;

        @Option(
                names = {"--include-provenance"},
                description = "Include SLSA provenance",
                defaultValue = "true")
        private boolean includeProvenance;

        @Option(
                names = {"--include-vulnerabilities"},
                description = "Include vulnerability scan",
                defaultValue = "true")
        private boolean includeVulnerabilities;

        @Override
        public Integer call() {
            try {
                Path projectRoot = Paths.get(projectPath).toAbsolutePath();
                Path outputPath = projectRoot.resolve(outputDir);

                logger.info("🔍 Generating SBOM for project: {}", projectRoot.getFileName());
                logger.info("📁 Output directory: {}", outputPath);
                logger.info("");;

                SBOMGenerator generator = new SBOMGenerator(projectRoot);

                // Generate SBOM
                logger.info("📋 Generating Software Bill of Materials...");
                SBOMDocument sbom = generator.generateSBOM();

                Path sbomFile = outputPath.resolve("sbom.spdx.json");
                generator.writeSBOMToFile(sbom, sbomFile);

                logger.info("✅ SBOM generated: {}", sbomFile);
                printSBOMSummary(sbom);

                // Generate SLSA provenance if requested
                if (includeProvenance) {
                    logger.info("");;
                    logger.info("🔗 Generating SLSA provenance...");
                    SLSAProvenance provenance = generator.generateProvenance();

                    Path provenanceFile = outputPath.resolve("provenance.slsa.json");
                    generator.writeProvenanceToFile(provenance, provenanceFile);

                    logger.info("✅ SLSA provenance generated: {}", provenanceFile);
                }

                // Security recommendations
                printSecurityRecommendations(sbom);

                return 0;

            } catch (Exception e) {
                logger.error("SBOM generation failed", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }

        private void printSBOMSummary(SBOMDocument sbom) {
            logger.info("");;
            logger.info("📊 SBOM SUMMARY:");
            logger.info("-".repeat(25));
            logger.info("📦 Packages:        {}", (sbom.packages != null ? sbom.packages.size() : 0));
            logger.info("🔗 Relationships:   {}", (sbom.relationships != null ? sbom.relationships.size() : 0));

            if (sbom.vulnerabilities != null && !sbom.vulnerabilities.isEmpty()) {
                logger.info("⚠️  Vulnerabilities: {}", sbom.vulnerabilities.size());

                long highSeverity =
                        sbom.vulnerabilities.stream()
                                .mapToLong(
                                        v ->
                                                "HIGH".equals(v.severity)
                                                                || "CRITICAL".equals(v.severity)
                                                        ? 1
                                                        : 0)
                                .sum();

                if (highSeverity > 0) {
                    logger.info("🚨 High/Critical:   {}", highSeverity);
                }
            }
        }

        private void printSecurityRecommendations(SBOMDocument sbom) {
            logger.info("");;
            logger.info("🔒 SECURITY RECOMMENDATIONS:");
            logger.info("-".repeat(30));

            if (sbom.vulnerabilities != null && !sbom.vulnerabilities.isEmpty()) {
                logger.info("⚠️  Found {} known vulnerabilities", sbom.vulnerabilities.size());
                logger.info("   → Review and update affected packages");
                logger.info("   → Consider security patches or alternatives");
            } else {
                logger.info("✅ No known vulnerabilities detected");
            }

            logger.info("📋 Best practices:");
            logger.info("   • Regularly update dependencies");
            logger.info("   • Monitor security advisories");
            logger.info("   • Use dependency scanning in CI/CD");
            logger.info("   • Implement SLSA attestation in releases");
        }
    }

    @Command(name = "verify", description = "Verify SBOM integrity and completeness")
    static class VerifyCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "SBOM file path")
        private String sbomPath;

        @Option(
                names = {"--strict"},
                description = "Enable strict validation")
        private boolean strict;

        @Override
        public Integer call() {
            try {
                Path sbomFile = Paths.get(sbomPath);

                logger.info("🔍 Verifying SBOM: {}", sbomFile.getFileName());
                logger.info("");;

                boolean valid = true;

                // Check file exists
                System.out.print("File exists:              ");
                if (java.nio.file.Files.exists(sbomFile)) {
                    logger.info("✅ PASS");
                } else {
                    logger.info("❌ FAIL");
                    valid = false;
                }

                // Check file format
                System.out.print("Valid JSON format:        ");
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                            new com.fasterxml.jackson.databind.ObjectMapper();
                    mapper.readValue(sbomFile.toFile(), Object.class);
                    logger.info("✅ PASS");
                } catch (Exception e) {
                    logger.info("❌ FAIL - {}", e.getMessage());
                    valid = false;
                }

                // Check SPDX version
                System.out.print("SPDX version present:     ");
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper =
                            new com.fasterxml.jackson.databind.ObjectMapper();
                    java.util.Map<String, Object> data =
                            mapper.readValue(sbomFile.toFile(), java.util.Map.class);
                    if (data.containsKey("spdxVersion")) {
                        logger.info("✅ PASS - {}", data.get("spdxVersion"));
                    } else {
                        logger.info("❌ FAIL");
                        valid = false;
                    }
                } catch (Exception e) {
                    logger.info("❌ FAIL - Cannot parse");
                    valid = false;
                }

                logger.info("");;
                logger.info("Overall Result: {}", (valid ? "✅ VALID" : "❌ INVALID"));

                return valid ? 0 : 1;

            } catch (Exception e) {
                logger.error("SBOM verification failed", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "scan", description = "Scan SBOM for security vulnerabilities")
    static class ScanCommand implements Callable<Integer> {

        @Parameters(
                index = "0",
                description = "SBOM file path",
                defaultValue = ".security/sbom.spdx.json")
        private String sbomPath;

        @Option(
                names = {"--severity"},
                description = "Minimum severity level (LOW, MEDIUM, HIGH, CRITICAL)",
                defaultValue = "MEDIUM")
        private String minSeverity;

        @Option(
                names = {"--format"},
                description = "Output format (table, json, csv)",
                defaultValue = "table")
        private String outputFormat;

        @Override
        public Integer call() {
            try {
                Path sbomFile = Paths.get(sbomPath);

                if (!java.nio.file.Files.exists(sbomFile)) {
                    logger.error("❌ SBOM file not found: {}", sbomFile);
                    logger.error("   Run 'yappc sbom generate' first");
                    return 1;
                }

                logger.info("🔍 Scanning SBOM for vulnerabilities...");
                logger.info("📁 File: {}", sbomFile);
                logger.info("🎯 Min Severity: {}", minSeverity);
                logger.info("");;

                // Parse SBOM and extract vulnerabilities
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();

                java.util.Map<String, Object> sbomData =
                        mapper.readValue(sbomFile.toFile(), java.util.Map.class);

                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> vulnerabilities =
                        (java.util.List<java.util.Map<String, Object>>)
                                sbomData.get("vulnerabilities");

                if (vulnerabilities == null || vulnerabilities.isEmpty()) {
                    logger.info("✅ No vulnerabilities found in SBOM");
                    return 0;
                }

                // Filter by severity
                java.util.List<java.util.Map<String, Object>> filteredVulns =
                        vulnerabilities.stream()
                                .filter(
                                        v ->
                                                meetsSeverityThreshold(
                                                        (String) v.get("severity"), minSeverity))
                                .collect(java.util.stream.Collectors.toList());

                if (filteredVulns.isEmpty()) {
                    logger.info("✅ No vulnerabilities found meeting severity threshold: {}", minSeverity);
                    return 0;
                }

                // Display results
                logger.info("⚠️  Found {} vulnerabilities:", filteredVulns.size());
                logger.info("");;

                if ("table".equals(outputFormat)) {
                    printVulnerabilityTable(filteredVulns);
                } else {
                    logger.info("Other output formats not implemented yet");
                }

                // Summary
                logger.info("");;
                logger.info("🔒 SECURITY SUMMARY:");
                logger.info("-".repeat(20));
                long critical = countBySeverity(filteredVulns, "CRITICAL");
                long high = countBySeverity(filteredVulns, "HIGH");
                long medium = countBySeverity(filteredVulns, "MEDIUM");
                long low = countBySeverity(filteredVulns, "LOW");

                logger.info("🚨 Critical: {}", critical);
                logger.info("⚠️  High:     {}", high);
                logger.info("🟡 Medium:   {}", medium);
                logger.info("ℹ️  Low:      {}", low);

                return filteredVulns.isEmpty() ? 0 : 1;

            } catch (Exception e) {
                logger.error("Vulnerability scan failed", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }

        private boolean meetsSeverityThreshold(String severity, String threshold) {
            java.util.List<String> severityLevels =
                    java.util.List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
            int severityIndex = severityLevels.indexOf(severity);
            int thresholdIndex = severityLevels.indexOf(threshold);
            return severityIndex >= thresholdIndex;
        }

        private void printVulnerabilityTable(
                java.util.List<java.util.Map<String, Object>> vulnerabilities) {
            log.info(String.format("%-15s %-10s %-50s", "ID", "SEVERITY", "DESCRIPTION"));
            logger.info("-".repeat(75));

            for (java.util.Map<String, Object> vuln : vulnerabilities) {
                String id = (String) vuln.get("id");
                String severity = (String) vuln.get("severity");
                String description = (String) vuln.get("description");

                if (description.length() > 47) {
                    description = description.substring(0, 44) + "...";
                }

                String icon = getSeverityIcon(severity);
                log.info(String.format("%-15s %s %-8s %-50s", id, icon, severity, description));
            }
        }

        private String getSeverityIcon(String severity) {
            return switch (severity) {
                case "CRITICAL" -> "🚨";
                case "HIGH" -> "⚠️ ";
                case "MEDIUM" -> "🟡";
                case "LOW" -> "ℹ️ ";
                default -> "  ";
            };
        }

        private long countBySeverity(
                java.util.List<java.util.Map<String, Object>> vulnerabilities, String severity) {
            return vulnerabilities.stream()
                    .mapToLong(v -> severity.equals(v.get("severity")) ? 1 : 0)
                    .sum();
        }
    }
}
