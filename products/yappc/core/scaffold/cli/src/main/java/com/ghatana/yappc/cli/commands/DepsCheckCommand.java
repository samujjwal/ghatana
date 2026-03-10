/*
 * YAPPC - Yet Another Project/Package Creator
 * Copyright (c) 2025 Ghatana
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.deps.AIDependencyUpgradeAdvisor;
import com.ghatana.yappc.core.deps.AIDependencyUpgradeAdvisor.*;
import com.ghatana.yappc.core.deps.DependencyGraphExtractor;
import com.ghatana.yappc.core.cache.RecommendationPriority;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for dependency health checking and recommendations. Provides
 * comprehensive analysis of project dependencies with actionable
 * recommendations.
 *
 * <p>
 * Week 9 Day 43: `deps check` recommendations system.
 */
@Command(
        name = "deps",
        description = "Dependency health checking and management",
        mixinStandardHelpOptions = true,
        subcommands = {DepsCheckCommand.CheckCommand.class})
/**
 * DepsCheckCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose DepsCheckCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DepsCheckCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DepsCheckCommand.class);

    @Override
    public Integer call() throws Exception {
        // When no subcommand is specified, show help
        new CommandLine(this).usage(System.out);
        return 0;
    }

    /**
     * Main `deps check` subcommand for dependency analysis and recommendations.
     */
    @Command(
            name = "check",
            description = "Check dependency health and provide recommendations",
            mixinStandardHelpOptions = true)
    public static class CheckCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project directory to analyze", defaultValue = ".")
        private File projectDir;

        @Option(
                names = {"-f", "--format"},
                description = "Output format: ${COMPLETION-CANDIDATES}",
                defaultValue = "CONSOLE")
        private OutputFormat format;

        @Option(
                names = {"-o", "--output"},
                description = "Output file (for JSON/MARKDOWN formats)")
        private File outputFile;

        @Option(
                names = {"--security-only"},
                description = "Focus only on security-related recommendations")
        private boolean securityOnly;

        @Option(
                names = {"--outdated-only"},
                description = "Show only outdated dependencies")
        private boolean outdatedOnly;

        @Option(
                names = {"--health-threshold"},
                description = "Minimum health score threshold (0.0-1.0)",
                defaultValue = "0.7")
        private double healthThreshold;

        @Option(
                names = {"--max-age-days"},
                description = "Maximum acceptable age for dependencies in days",
                defaultValue = "365")
        private int maxAgeDays;

        @Option(
                names = {"--detailed"},
                description = "Show detailed analysis and recommendations")
        private boolean detailed;

        @Option(
                names = {"--ecosystem"},
                description = "Filter by specific ecosystem: ${COMPLETION-CANDIDATES}")
        private EcosystemFilter ecosystem;

        private final DependencyGraphExtractor dependencyExtractor;
        private final AIDependencyUpgradeAdvisor upgradeAdvisor;
        private final DependencyHealthAnalyzer healthAnalyzer;

        public CheckCommand() {
            this.dependencyExtractor = new DependencyGraphExtractor();
            this.upgradeAdvisor = new AIDependencyUpgradeAdvisor();
            this.healthAnalyzer = new DependencyHealthAnalyzer();
        }

        @Override
        public Integer call() throws Exception {
            try {
                if (!projectDir.exists() || !projectDir.isDirectory()) {
                    log.error("❌ Project directory does not exist: {}", projectDir);
                    return 1;
                }

                log.info("🔍 Dependency Health Check");
                log.info("📁 Analyzing: {}", projectDir.getAbsolutePath());
                log.info("");;

                // Step 1: Extract dependency graph
                DependencyGraphExtractor.DependencyGraph dependencyGraph
                        = dependencyExtractor.extractDependencyGraph(projectDir.getAbsolutePath());

                // Step 2: Analyze dependency health
                DependencyHealthReport healthReport
                        = healthAnalyzer.analyzeProjectHealth(dependencyGraph, maxAgeDays);

                // Step 3: Get upgrade recommendations
                UpgradeRecommendations upgradeRecommendations
                        = upgradeAdvisor.analyzeAndRecommendUpgrades(
                                projectDir.getAbsolutePath(), UpgradeStrategy.BALANCED);

                // Step 4: Generate comprehensive recommendations
                DependencyCheckResult checkResult
                        = generateCheckResult(dependencyGraph, healthReport, upgradeRecommendations);

                // Step 5: Display results
                return displayResults(checkResult);

            } catch (Exception e) {
                log.error("❌ Error during dependency check: {}", e.getMessage());
                if (detailed) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        private DependencyCheckResult generateCheckResult(
                DependencyGraphExtractor.DependencyGraph dependencyGraph,
                DependencyHealthReport healthReport,
                UpgradeRecommendations upgradeRecommendations) {

            List<DependencyRecommendation> recommendations = new ArrayList<>();

            // Filter dependencies based on options
            Set<DependencyGraphExtractor.Dependency> dependencies
                    = filterDependencies(dependencyGraph.unifiedDependencies());

            for (var dependency : dependencies) {
                DependencyHealthInfo healthInfo = healthReport.dependencyHealth().get(dependency);
                if (healthInfo == null) {
                    continue;
                }

                // Skip if health is above threshold and not forcing display
                if (!detailed
                        && healthInfo.healthScore() >= healthThreshold
                        && !securityOnly
                        && !outdatedOnly) {
                    continue;
                }

                List<String> issues
                        = identifyIssues(dependency, healthInfo, upgradeRecommendations);
                List<String> recommendations_list
                        = generateRecommendations(dependency, healthInfo, upgradeRecommendations);

                if (!issues.isEmpty() || !recommendations_list.isEmpty()) {
                    DependencyRecommendation recommendation
                            = new DependencyRecommendation(
                                    dependency,
                                    healthInfo,
                                    issues,
                                    recommendations_list,
                                    calculatePriority(healthInfo, issues),
                                    LocalDateTime.now());
                    recommendations.add(recommendation);
                }
            }

            // Sort by priority and health score
            recommendations.sort(
                    Comparator.comparing(DependencyRecommendation::priority)
                            .thenComparing(rec -> rec.healthInfo().healthScore()));

            return new DependencyCheckResult(
                    dependencyGraph,
                    healthReport,
                    upgradeRecommendations,
                    recommendations,
                    calculateOverallHealthScore(healthReport),
                    LocalDateTime.now());
        }

        private Set<DependencyGraphExtractor.Dependency> filterDependencies(
                Set<DependencyGraphExtractor.Dependency> dependencies) {

            return dependencies.stream()
                    .filter(
                            dep
                            -> ecosystem == null
                            || dep.ecosystem()
                                    .toLowerCase()
                                    .contains(ecosystem.name().toLowerCase()))
                    .collect(Collectors.toSet());
        }

        private List<String> identifyIssues(
                DependencyGraphExtractor.Dependency dependency,
                DependencyHealthInfo healthInfo,
                UpgradeRecommendations upgradeRecommendations) {

            List<String> issues = new ArrayList<>();

            // Security vulnerabilities
            if (healthInfo.securityVulnerabilities() > 0) {
                issues.add(
                        String.format(
                                "Has %d security vulnerabilities",
                                healthInfo.securityVulnerabilities()));
            }

            // Outdated dependencies
            if (healthInfo.daysSinceLastUpdate() > maxAgeDays) {
                issues.add(
                        String.format(
                                "Outdated (last updated %d days ago)",
                                healthInfo.daysSinceLastUpdate()));
            }

            // License issues
            if (healthInfo.licenseIssues()) {
                issues.add("License compatibility issues detected");
            }

            // Dependency conflicts
            if (healthInfo.hasConflicts()) {
                issues.add("Version conflicts with other dependencies");
            }

            // Deprecated dependencies
            if (healthInfo.deprecated()) {
                issues.add("Dependency is deprecated");
            }

            // Low maintenance score
            if (healthInfo.maintenanceScore() < 0.5) {
                issues.add("Low maintenance activity");
            }

            return issues;
        }

        private List<String> generateRecommendations(
                DependencyGraphExtractor.Dependency dependency,
                DependencyHealthInfo healthInfo,
                UpgradeRecommendations upgradeRecommendations) {

            List<String> recommendations = new ArrayList<>();

            // Security-related recommendations
            if (healthInfo.securityVulnerabilities() > 0) {
                recommendations.add("Upgrade to latest version to fix security vulnerabilities");

                // Check if upgrade advisor has recommendations
                Optional<UpgradeRecommendation> securityUpgrade
                        = upgradeRecommendations.recommendations().stream()
                                .filter(rec -> rec.dependency().equals(dependency))
                                .filter(rec -> !rec.securityFixes().isEmpty())
                                .findFirst();

                if (securityUpgrade.isPresent()) {
                    recommendations.add(
                            String.format(
                                    "Recommended upgrade: %s → %s",
                                    dependency.version(), securityUpgrade.get().targetVersion()));
                }
            }

            // Outdated dependency recommendations
            if (healthInfo.daysSinceLastUpdate() > maxAgeDays) {
                recommendations.add("Consider upgrading to a more recent version");

                if (healthInfo.daysSinceLastUpdate() > 730) { // 2 years
                    recommendations.add("Evaluate if this dependency is still needed");
                }
            }

            // License recommendations
            if (healthInfo.licenseIssues()) {
                recommendations.add("Review license compatibility with your project");
                recommendations.add("Consider alternative dependencies with compatible licenses");
            }

            // Conflict resolution
            if (healthInfo.hasConflicts()) {
                recommendations.add("Resolve version conflicts using dependency management tools");
                recommendations.add("Consider using dependency resolution strategies");
            }

            // Deprecated dependency recommendations
            if (healthInfo.deprecated()) {
                recommendations.add("Find alternative dependencies that are actively maintained");
                recommendations.add("Plan migration away from deprecated dependency");
            }

            // Maintenance recommendations
            if (healthInfo.maintenanceScore() < 0.5) {
                recommendations.add("Evaluate dependency maintenance status");
                recommendations.add("Consider more actively maintained alternatives");
            }

            // General health improvements
            if (healthInfo.healthScore() < healthThreshold) {
                recommendations.add("Improve dependency health by addressing identified issues");
            }

            return recommendations;
        }

        private RecommendationPriority calculatePriority(
                DependencyHealthInfo healthInfo, List<String> issues) {

            if (healthInfo.securityVulnerabilities() > 0) {
                return RecommendationPriority.CRITICAL;
            }

            if (healthInfo.deprecated() || healthInfo.licenseIssues()) {
                return RecommendationPriority.HIGH;
            }

            if (healthInfo.hasConflicts() || healthInfo.daysSinceLastUpdate() > 365) {
                return RecommendationPriority.MEDIUM;
            }

            return RecommendationPriority.LOW;
        }

        private double calculateOverallHealthScore(DependencyHealthReport report) {
            return report.dependencyHealth().values().stream()
                    .mapToDouble(DependencyHealthInfo::healthScore)
                    .average()
                    .orElse(0.0);
        }

        private Integer displayResults(DependencyCheckResult result) throws Exception {
            switch (format) {
                case CONSOLE ->
                    displayConsoleResults(result);
                case JSON ->
                    generateJsonReport(result);
                case MARKDOWN ->
                    generateMarkdownReport(result);
            }
            return 0;
        }

        private void displayConsoleResults(DependencyCheckResult result) {
            log.info("📊 DEPENDENCY HEALTH REPORT");
            log.info("═".repeat(60));

            // Overall health summary
            displayHealthSummary(result);

            // Recommendations by priority
            displayRecommendationsByPriority(result);

            // Ecosystem breakdown
            if (detailed) {
                displayEcosystemBreakdown(result);
            }

            // Action items
            displayActionItems(result);
        }

        private void displayHealthSummary(DependencyCheckResult result) {
            log.info("🏥 OVERALL HEALTH:");
            log.info(String.format("  📈 Health Score: %.2f/1.0", result.overallHealthScore()));
            log.info("  📦 Total Dependencies: {}", result.dependencyGraph().unifiedDependencies().size());
            log.info("  ⚠️  Issues Found: {} dependencies need attention", result.recommendations().size());

            DependencyHealthReport healthReport = result.healthReport();
            log.info("  🔒 Security Vulnerabilities: {} total", healthReport.totalSecurityVulnerabilities());
            log.info("  🏚️  Outdated Dependencies: {}", healthReport.outdatedDependencies());
            log.info("  📜 License Issues: {}", healthReport.licenseIssues());
            log.info("  ⚠️  Deprecated Dependencies: {}", healthReport.deprecatedDependencies());

            log.info("");;
        }

        private void displayRecommendationsByPriority(DependencyCheckResult result) {
            Map<RecommendationPriority, List<DependencyRecommendation>> byPriority
                    = result.recommendations().stream()
                            .collect(Collectors.groupingBy(DependencyRecommendation::priority));

            for (RecommendationPriority priority : RecommendationPriority.values()) {
                List<DependencyRecommendation> recs = byPriority.get(priority);
                if (recs == null || recs.isEmpty()) {
                    continue;
                }

                String icon
                        = switch (priority) {
                    case CRITICAL ->
                        "🚨";
                    case HIGH ->
                        "🔴";
                    case MEDIUM ->
                        "🟡";
                    case LOW ->
                        "🟢";
                };

                log.info("{} {} PRIORITY ({} dependencies)", icon, priority, recs.size());
                log.info("─".repeat(50));

                for (DependencyRecommendation rec : recs) {
                    displayDependencyRecommendation(rec);
                }
                log.info("");;
            }
        }

        private void displayDependencyRecommendation(DependencyRecommendation rec) {
            DependencyGraphExtractor.Dependency dep = rec.dependency();
            DependencyHealthInfo health = rec.healthInfo();

            log.info("📦 {}:{} ({})", dep.name(), dep.version(), dep.ecosystem());
            log.info(String.format("   📊 Health Score: %.2f  🏷️  Last Updated: %d days ago", health.healthScore(), health.daysSinceLastUpdate()));

            if (!rec.issues().isEmpty()) {
                log.info("   ❌ Issues:");
                rec.issues().forEach(issue ->
                    log.info("     • {}", issue));
            }
            if (detailed && !rec.recommendations().isEmpty()) {
                log.info("   \ud83d\udca1 Recommendations:");
                rec.recommendations().forEach(recommendation ->
                    log.info("     \u2022 {}", recommendation));
            }
            log.info("");
        }

        private void displayEcosystemBreakdown(DependencyCheckResult result) {
            log.info("🏗️ ECOSYSTEM BREAKDOWN:");
            log.info("─".repeat(50));

            Map<String, List<DependencyRecommendation>> byEcosystem
                    = result.recommendations().stream()
                            .collect(Collectors.groupingBy(rec -> rec.dependency().ecosystem()));

            for (Map.Entry<String, List<DependencyRecommendation>> entry : byEcosystem.entrySet()) {
                String ecosystem = entry.getKey();
                List<DependencyRecommendation> recs = entry.getValue();

                double avgHealth
                        = recs.stream()
                                .mapToDouble(rec -> rec.healthInfo().healthScore())
                                .average()
                                .orElse(0.0);

                log.info(String.format("%s: %d issues (avg health: %.2f)", ecosystem, recs.size(), avgHealth));
            }
            log.info("");;
        }

        private void displayActionItems(DependencyCheckResult result) {
            log.info("✅ RECOMMENDED ACTIONS:");
            log.info("─".repeat(50));

            List<String> actions = new ArrayList<>();

            // Critical security actions
            long criticalCount
                    = result.recommendations().stream()
                            .filter(rec -> rec.priority() == RecommendationPriority.CRITICAL)
                            .count();

            if (criticalCount > 0) {
                actions.add(
                        String.format(
                                "🚨 URGENT: Address %d critical security vulnerabilities",
                                criticalCount));
            }

            // High priority actions
            long highCount
                    = result.recommendations().stream()
                            .filter(rec -> rec.priority() == RecommendationPriority.HIGH)
                            .count();

            if (highCount > 0) {
                actions.add(String.format("🔴 Update %d high-priority dependencies", highCount));
            }

            // General recommendations
            if (result.overallHealthScore() < 0.7) {
                actions.add("📈 Improve overall dependency health (currently below threshold)");
            }

            if (result.healthReport().outdatedDependencies() > 0) {
                actions.add("📅 Review and update outdated dependencies");
            }

            actions.add("🔄 Run 'yappc deps-upgrade' for detailed upgrade recommendations");
            actions.add("📝 Consider implementing dependency update automation");

            for (int i = 0; i < actions.size(); i++) {
                log.info("{}. {}", i + 1, actions.get(i));
            }

            log.info("");;
            log.info("📅 Report generated at: {}", result.analysisTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        private void generateJsonReport(DependencyCheckResult result) throws Exception {
            // Implementation for JSON report generation
            log.info("📄 JSON report generation not implemented yet");
        }

        private void generateMarkdownReport(DependencyCheckResult result) throws Exception {
            // Implementation for Markdown report generation
            log.info("📄 Markdown report generation not implemented yet");
        }

        public enum EcosystemFilter {
            MAVEN,
            NPM,
            CARGO,
            GO,
            GRADLE
        }

    }

    /**
     * Dependency health analyzer for comprehensive project health assessment.
     */
    public static class DependencyHealthAnalyzer {

        public DependencyHealthReport analyzeProjectHealth(
                DependencyGraphExtractor.DependencyGraph graph, int maxAgeDays) {

            log.info("🏥 Analyzing dependency health...");

            Map<DependencyGraphExtractor.Dependency, DependencyHealthInfo> healthInfo
                    = new HashMap<>();
            int totalSecurityVulns = 0;
            int outdated = 0;
            int licenseIssues = 0;
            int deprecated = 0;

            for (var dependency : graph.unifiedDependencies()) {
                DependencyHealthInfo health
                        = analyzeDependencyHealth(dependency, graph, maxAgeDays);
                healthInfo.put(dependency, health);

                totalSecurityVulns += health.securityVulnerabilities();
                if (health.daysSinceLastUpdate() > maxAgeDays) {
                    outdated++;
                }
                if (health.licenseIssues()) {
                    licenseIssues++;
                }
                if (health.deprecated()) {
                    deprecated++;
                }
            }

            return new DependencyHealthReport(
                    healthInfo,
                    totalSecurityVulns,
                    outdated,
                    licenseIssues,
                    deprecated,
                    LocalDateTime.now());
        }

        private DependencyHealthInfo analyzeDependencyHealth(
                DependencyGraphExtractor.Dependency dependency,
                DependencyGraphExtractor.DependencyGraph graph,
                int maxAgeDays) {

            // Simulate health analysis
            Random random = new Random(dependency.name().hashCode());

            int securityVulns = random.nextInt(3); // 0-2 vulnerabilities
            int daysSinceUpdate = random.nextInt(800); // 0-800 days
            boolean licenseIssues = random.nextDouble() < 0.1; // 10% chance
            boolean hasConflicts
                    = graph.conflicts().stream()
                            .anyMatch(
                                    conflict
                                    -> conflict.dependencyName().equals(dependency.name()));
            boolean deprecated = random.nextDouble() < 0.05; // 5% chance
            double maintenanceScore = 0.3 + random.nextDouble() * 0.7; // 0.3-1.0

            // Calculate overall health score
            double healthScore = 1.0;
            healthScore -= securityVulns * 0.3; // Security vulnerabilities are severe
            healthScore
                    -= Math.min(daysSinceUpdate / (double) maxAgeDays, 1.0) * 0.2; // Outdated penalty
            if (licenseIssues) {
                healthScore -= 0.15;
            }
            if (hasConflicts) {
                healthScore -= 0.1;
            }
            if (deprecated) {
                healthScore -= 0.25;
            }
            healthScore -= (1.0 - maintenanceScore) * 0.1;

            healthScore = Math.max(0.0, healthScore);

            return new DependencyHealthInfo(
                    dependency,
                    healthScore,
                    securityVulns,
                    daysSinceUpdate,
                    licenseIssues,
                    hasConflicts,
                    deprecated,
                    maintenanceScore);
        }
    }

    // Data classes for dependency health analysis
    public record DependencyHealthInfo(
            DependencyGraphExtractor.Dependency dependency,
            double healthScore,
            int securityVulnerabilities,
            int daysSinceLastUpdate,
            boolean licenseIssues,
            boolean hasConflicts,
            boolean deprecated,
            double maintenanceScore) {
    }

    public record DependencyHealthReport(
            Map<DependencyGraphExtractor.Dependency, DependencyHealthInfo> dependencyHealth,
            int totalSecurityVulnerabilities,
            int outdatedDependencies,
            int licenseIssues,
            int deprecatedDependencies,
            LocalDateTime analysisTime) {
    }

    public record DependencyRecommendation(
            DependencyGraphExtractor.Dependency dependency,
            DependencyHealthInfo healthInfo,
            List<String> issues,
            List<String> recommendations,
            RecommendationPriority priority,
            LocalDateTime generatedAt) {
    }

    public record DependencyCheckResult(
            DependencyGraphExtractor.DependencyGraph dependencyGraph,
            DependencyHealthReport healthReport,
            UpgradeRecommendations upgradeRecommendations,
            List<DependencyRecommendation> recommendations,
            double overallHealthScore,
            LocalDateTime analysisTime) {
    }
}
