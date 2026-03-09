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
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI command for AI-powered dependency upgrade analysis and recommendations.
 *
 * <p>Week 9 Day 42: AI dependency upgrade advisor CLI integration.
 */
@Command(
        name = "deps-upgrade",
        description = "AI-powered dependency upgrade advisor with security analysis",
        mixinStandardHelpOptions = true)
/**
 * DepsUpgradeCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose DepsUpgradeCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DepsUpgradeCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DepsUpgradeCommand.class);

    @Parameters(index = "0", description = "Project directory to analyze", defaultValue = ".")
    private File projectDir;

    @Option(
            names = {"-s", "--strategy"},
            description = "Upgrade strategy: ${COMPLETION-CANDIDATES}",
            defaultValue = "BALANCED")
    private AIDependencyUpgradeAdvisor.UpgradeStrategy strategy;

    @Option(
            names = {"-f", "--format"},
            description = "Output format: ${COMPLETION-CANDIDATES}",
            defaultValue = "CONSOLE")
    private OutputFormat format;

    @Option(
            names = {"-o", "--output"},
            description = "Output file (for JSON/HTML formats)")
    private File outputFile;

    @Option(
            names = {"--simulate"},
            description = "Simulate upgrades without generating recommendations")
    private boolean simulate;

    @Option(
            names = {"--security-only"},
            description = "Only show security-related upgrades")
    private boolean securityOnly;

    @Option(
            names = {"--max-risk"},
            description = "Maximum acceptable risk score (0.0-1.0)",
            defaultValue = "0.7")
    private double maxRisk;

    @Option(
            names = {"--detailed"},
            description = "Show detailed analysis including compatibility assessment")
    private boolean detailed;

    private final AIDependencyUpgradeAdvisor upgradeAdvisor;

    public DepsUpgradeCommand() {
        this.upgradeAdvisor = new AIDependencyUpgradeAdvisor();
    }

    @Override
    public Integer call() throws Exception {
        try {
            if (!projectDir.exists() || !projectDir.isDirectory()) {
                log.error("❌ Project directory does not exist: {}", projectDir);
                return 1;
            }

            log.info("🔍 AI Dependency Upgrade Advisor");
            log.info("📁 Analyzing: {}", projectDir.getAbsolutePath());
            log.info("📋 Strategy: {}", strategy);
            log.info("");;

            // Analyze dependencies and get recommendations
            UpgradeRecommendations recommendations =
                    upgradeAdvisor.analyzeAndRecommendUpgrades(
                            projectDir.getAbsolutePath(), strategy);

            // Filter recommendations based on options
            List<UpgradeRecommendation> filteredRecommendations =
                    filterRecommendations(recommendations.recommendations());

            if (simulate) {
                return runSimulation(filteredRecommendations);
            } else {
                return displayRecommendations(recommendations, filteredRecommendations);
            }

        } catch (Exception e) {
            log.error("❌ Error during dependency analysis: {}", e.getMessage());
            if (detailed) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private List<UpgradeRecommendation> filterRecommendations(
            List<UpgradeRecommendation> recommendations) {
        return recommendations.stream()
                .filter(rec -> rec.riskScore() <= maxRisk)
                .filter(rec -> !securityOnly || !rec.securityFixes().isEmpty())
                .collect(Collectors.toList());
    }

    private Integer runSimulation(List<UpgradeRecommendation> recommendations) {
        if (recommendations.isEmpty()) {
            log.info("ℹ️  No upgrades to simulate based on current filters");
            return 0;
        }

        log.info("🧪 Running upgrade simulation...");
        log.info("");;

        UpgradeSimulationResult simulation =
                upgradeAdvisor.simulateUpgrades(projectDir.getAbsolutePath(), recommendations);

        displaySimulationResults(simulation);
        return 0;
    }

    private Integer displayRecommendations(
            UpgradeRecommendations recommendations,
            List<UpgradeRecommendation> filteredRecommendations)
            throws IOException {

        switch (format) {
            case CONSOLE -> displayConsoleReport(recommendations, filteredRecommendations);
            case JSON -> generateJsonReport(recommendations, filteredRecommendations);
            case HTML -> generateHtmlReport(recommendations, filteredRecommendations);
        }

        return 0;
    }

    private void displayConsoleReport(
            UpgradeRecommendations recommendations,
            List<UpgradeRecommendation> filteredRecommendations) {

        log.info("📊 DEPENDENCY ANALYSIS SUMMARY");
        log.info("═".repeat(50));

        // Security overview
        SecurityAnalysisResult security = recommendations.securityAnalysis();
        int criticalVulns = security.criticalVulnerabilities().size();
        int otherVulns = security.otherVulnerabilities().size();

        if (criticalVulns > 0 || otherVulns > 0) {
            log.info("🔒 SECURITY ANALYSIS:");
            if (criticalVulns > 0) {
                log.info("  ⚠️  {} critical/high severity vulnerabilities found", criticalVulns);
            }
            if (otherVulns > 0) {
                log.info("  ℹ️  {} other vulnerabilities found", otherVulns);
            }
            log.info(String.format("  📈 Security Score: %.2f/1.0", security.securityScore()));
            log.info("");;
        }

        // Upgrade recommendations
        if (filteredRecommendations.isEmpty()) {
            log.info("✅ No upgrades recommended with current filters");
            return;
        }

        log.info("📦 UPGRADE RECOMMENDATIONS:");
        log.info("─".repeat(50));

        // Group by priority
        var byPriority =
                filteredRecommendations.stream()
                        .collect(Collectors.groupingBy(UpgradeRecommendation::priority));

        for (UpgradePriority priority : UpgradePriority.values()) {
            List<UpgradeRecommendation> recs = byPriority.get(priority);
            if (recs == null || recs.isEmpty()) continue;

            String priorityIcon =
                    switch (priority) {
                        case CRITICAL_SECURITY -> "🚨";
                        case HIGH -> "🔴";
                        case MEDIUM -> "🟡";
                        case LOW -> "🟢";
                    };

            log.info("{} {} PRIORITY ({} upgrades)", priorityIcon, priority, recs.size());

            for (UpgradeRecommendation rec : recs) {
                displayUpgradeRecommendation(rec);
            }
            log.info("");;
        }

        // Upgrade plan
        displayUpgradePlan(recommendations.upgradePlan());

        // Summary statistics
        displaySummaryStatistics(recommendations, filteredRecommendations);
    }

    private void displayUpgradeRecommendation(UpgradeRecommendation rec) {
        log.info("  📦 {}: {} → {} ({})", rec.dependency().name(),
                rec.dependency().version(),
                rec.targetVersion(),
                rec.upgradeType());

        log.info(String.format("     🎯 Risk Score: %.2f  🏷️  Ecosystem: %s", rec.riskScore(), rec.dependency().ecosystem()));

        if (!rec.securityFixes().isEmpty()) {
            log.info("     🔒 Security Fixes: {}", String.join(", ", rec.securityFixes()));
        }

        if (detailed) {
            if (!rec.benefits().isEmpty()) {
                log.info("     ✅ Benefits: {}", String.join(", ", rec.benefits()));
            }
            if (!rec.risks().isEmpty()) {
                log.info("     ⚠️  Risks: {}", String.join(", ", rec.risks()));
            }
        }

        log.info("");;
    }

    private void displayUpgradePlan(UpgradePlan plan) {
        log.info("📋 UPGRADE EXECUTION PLAN:");
        log.info("─".repeat(50));

        for (UpgradePhase phase : plan.phases()) {
            log.info("Phase {}: {} ({} upgrades)", phase.phaseNumber(), phase.name(), phase.upgrades().size());
            log.info("  📝 {}", phase.description());

            if (!phase.prerequisites().isEmpty()) {
                log.info("  📋 Prerequisites: {}", String.join(", ", phase.prerequisites()));
            }
            log.info("");;
        }

        log.info(String.format("⏱️  Estimated Effort: %.1f hours", plan.estimatedEffortHours()));

        if (!plan.riskMitigations().isEmpty()) {
            log.info("🛡️  Risk Mitigations:");
            for (String mitigation : plan.riskMitigations()) {
                log.info("  • {}", mitigation);
            }
        }
        log.info("");;
    }

    private void displaySimulationResults(UpgradeSimulationResult simulation) {
        log.info("🧪 UPGRADE SIMULATION RESULTS");
        log.info("═".repeat(50));

        log.info(String.format("📊 Overall Risk Score: %.2f", simulation.overallRiskScore()));
        log.info("📦 Total Changes: {} dependencies", simulation.projectedChanges().size());
        log.info("");;

        if (!simulation.projectedChanges().isEmpty()) {
            log.info("📝 PROJECTED CHANGES:");
            simulation
                    .projectedChanges()
                    .projectedChanges().forEach((dep, change) ->
                        log.info("  • {}: {}", dep, change));
                        log.info("");
        }

        if (!simulation.potentialIssues().isEmpty()) {
            log.info("⚠️  POTENTIAL ISSUES:");
            simulation.potentialIssues().forEach(issue ->
                log.info("  • {}", issue));
                log.info("");
        }

        // Individual simulation results
        if (detailed) {
            log.info("📋 DETAILED SIMULATION RESULTS:");
            for (AIDependencyUpgradeAdvisor.SimulationResult result :
                    simulation.individualResults()) {
                String status = result.success() ? "✅" : "❌";
                log.info(String.format("%s %s → %s (Risk: %.2f)", status,
                        result.upgrade().dependency().name(),
                        result.upgrade().targetVersion(),
                        result.riskScore()));

                if (!result.success()) {
                    log.info("    Issues: {}", String.join(", ", result.potentialIssues()));
                }
            }
        }
    }

    private void displaySummaryStatistics(
            UpgradeRecommendations recommendations,
            List<UpgradeRecommendation> filteredRecommendations) {
        log.info("📈 SUMMARY STATISTICS");
        log.info("─".repeat(50));

        int totalDeps = recommendations.currentDependencies().unifiedDependencies().size();
        int upgradeableDeps = recommendations.recommendations().size();
        int filteredDeps = filteredRecommendations.size();

        log.info("📦 Total Dependencies: {}", totalDeps);
        log.info(String.format("🔄 Upgradeable Dependencies: %d (%.1f%%)", upgradeableDeps, (upgradeableDeps * 100.0 / totalDeps)));
        log.info("✅ Recommended Upgrades: {}", filteredDeps);

        // Ecosystem breakdown
        var ecosystemCounts = recommendations.currentDependencies().ecosystemDependencies();
        if (!ecosystemCounts.isEmpty()) {
            log.info("🏗️  By Ecosystem:");
            ecosystemCounts.forEach(
                    (ecosystem, deps) ->
                            log.info("  • {}: {} dependencies", ecosystem, deps.size());
        }

        log.info("");;
        log.info("📅 Analysis completed at: {}", recommendations.analysisTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private void generateJsonReport(
            UpgradeRecommendations recommendations,
            List<UpgradeRecommendation> filteredRecommendations)
            throws IOException {
        File output = outputFile != null ? outputFile : new File("upgrade-recommendations.json");

        log.info("📄 Generating JSON report: {}", output.getName());

        // Create simplified JSON structure
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"analysisTime\": \"")
                .append(recommendations.analysisTime())
                .append("\",\n");
        json.append("  \"strategy\": \"").append(recommendations.strategy()).append("\",\n");
        json.append("  \"totalDependencies\": ")
                .append(recommendations.currentDependencies().unifiedDependencies().size())
                .append(",\n");
        json.append("  \"recommendedUpgrades\": ")
                .append(filteredRecommendations.size())
                .append(",\n");
        json.append("  \"securityScore\": ")
                .append(recommendations.securityAnalysis().securityScore())
                .append(",\n");
        json.append("  \"upgrades\": [\n");

        for (int i = 0; i < filteredRecommendations.size(); i++) {
            UpgradeRecommendation rec = filteredRecommendations.get(i);
            json.append("    {\n");
            json.append("      \"dependency\": \"").append(rec.dependency().name()).append("\",\n");
            json.append("      \"currentVersion\": \"")
                    .append(rec.dependency().version())
                    .append("\",\n");
            json.append("      \"targetVersion\": \"").append(rec.targetVersion()).append("\",\n");
            json.append("      \"priority\": \"").append(rec.priority()).append("\",\n");
            json.append("      \"riskScore\": ").append(rec.riskScore()).append(",\n");
            json.append("      \"securityFixes\": ")
                    .append(rec.securityFixes().size())
                    .append("\n");
            json.append("    }");
            if (i < filteredRecommendations.size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        try (FileWriter writer = new FileWriter(output)) {
            writer.write(json.toString());
        }

        log.info("✅ JSON report saved to: {}", output.getAbsolutePath());
    }

    private void generateHtmlReport(
            UpgradeRecommendations recommendations,
            List<UpgradeRecommendation> filteredRecommendations)
            throws IOException {
        File output = outputFile != null ? outputFile : new File("upgrade-recommendations.html");

        log.info("📄 Generating HTML report: {}", output.getName());

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><title>Dependency Upgrade Recommendations</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append(".priority-critical { color: #ff0000; font-weight: bold; }\n");
        html.append(".priority-high { color: #ff6600; font-weight: bold; }\n");
        html.append(".priority-medium { color: #ff9900; }\n");
        html.append(".priority-low { color: #339900; }\n");
        html.append("table { border-collapse: collapse; width: 100%; }\n");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        html.append("th { background-color: #f2f2f2; }\n");
        html.append("</style></head><body>\n");

        html.append("<h1>🔍 Dependency Upgrade Recommendations</h1>\n");
        html.append("<p><strong>Analysis Time:</strong> ")
                .append(recommendations.analysisTime())
                .append("</p>\n");
        html.append("<p><strong>Strategy:</strong> ")
                .append(recommendations.strategy())
                .append("</p>\n");
        html.append("<p><strong>Security Score:</strong> ")
                .append(String.format("%.2f", recommendations.securityAnalysis().securityScore()))
                .append("</p>\n");

        html.append("<table>\n");
        html.append(
                "<tr><th>Dependency</th><th>Current</th><th>Target</th><th>Priority</th><th>Risk</th><th>Security"
                    + " Fixes</th></tr>\n");

        for (UpgradeRecommendation rec : filteredRecommendations) {
            String priorityClass =
                    "priority-" + rec.priority().name().toLowerCase().replace("_", "-");
            html.append("<tr>\n");
            html.append("<td>").append(rec.dependency().name()).append("</td>\n");
            html.append("<td>").append(rec.dependency().version()).append("</td>\n");
            html.append("<td>").append(rec.targetVersion()).append("</td>\n");
            html.append("<td class=\"")
                    .append(priorityClass)
                    .append("\">")
                    .append(rec.priority())
                    .append("</td>\n");
            html.append("<td>").append(String.format("%.2f", rec.riskScore())).append("</td>\n");
            html.append("<td>").append(rec.securityFixes().size()).append("</td>\n");
            html.append("</tr>\n");
        }

        html.append("</table>\n");
        html.append("</body></html>\n");

        try (FileWriter writer = new FileWriter(output)) {
            writer.write(html.toString());
        }

        log.info("✅ HTML report saved to: {}", output.getAbsolutePath());
    }
}
