package com.ghatana.yappc.core.kpi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KPI Reporter for generating formatted reports and dashboards Week 10 Day 50: KPI measurement and
 * reporting
 *
 * @doc.type class
 * @doc.purpose KPI Reporter for generating formatted reports and dashboards Week 10 Day 50: KPI measurement and
 * @doc.layer platform
 * @doc.pattern Component
 */
public class KPIReporter {
    private static final Logger logger = LoggerFactory.getLogger(KPIReporter.class);

    private final KPICollector collector;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public KPIReporter(KPICollector collector) {
        this.collector = collector;
    }

    /**
 * Generate a text-based KPI report */
    public String generateTextReport() {
        KPICollector.KPIReport report = collector.generateReport();
        StringBuilder sb = new StringBuilder();

        sb.append("=".repeat(80)).append("\n");
        sb.append("                           YAPPC KPI REPORT\n");
        sb.append("=".repeat(80)).append("\n");
        sb.append("Generated at: ")
                .append(
                        report.getGeneratedAt()
                                .atZone(ZoneId.systemDefault())
                                .format(TIMESTAMP_FORMAT))
                .append("\n\n");

        // Summary section
        sb.append("SUMMARY\n");
        sb.append("-".repeat(40)).append("\n");
        sb.append(String.format("Total Metrics Tracked: %d%n", report.getMetricSummaries().size()));
        sb.append(
                String.format(
                        "Total Data Points: %d%n",
                        report.getMetricSummaries().values().stream()
                                .mapToInt(KPICollector.MetricSummary::getCount)
                                .sum()));
        sb.append("\n");

        // Metrics details
        sb.append("METRIC DETAILS\n");
        sb.append("-".repeat(40)).append("\n");

        report.getMetricSummaries()
                .values()
                .forEach(
                        summary -> {
                            sb.append(
                                    formatMetricSummary(
                                            summary,
                                            report.getTrends().get(summary.getMetricName())));
                            sb.append("\n");
                        });

        return sb.toString();
    }

    /**
 * Generate an HTML dashboard report */
    public String generateHtmlReport() {
        KPICollector.KPIReport report = collector.generateReport();
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append(
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>YAPPC KPI Dashboard</title>\n");
        html.append("    <style>\n");
        html.append(getHtmlStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");

        html.append("    <div class=\"container\">\n");
        html.append("        <header>\n");
        html.append("            <h1>YAPPC KPI Dashboard</h1>\n");
        html.append("            <p class=\"timestamp\">Generated: ")
                .append(
                        report.getGeneratedAt()
                                .atZone(ZoneId.systemDefault())
                                .format(TIMESTAMP_FORMAT))
                .append("</p>\n");
        html.append("        </header>\n\n");

        // Summary cards
        html.append("        <div class=\"summary-cards\">\n");
        html.append("            <div class=\"card\">\n");
        html.append("                <h3>Total Metrics</h3>\n");
        html.append("                <div class=\"metric-value\">")
                .append(report.getMetricSummaries().size())
                .append("</div>\n");
        html.append("            </div>\n");

        html.append("            <div class=\"card\">\n");
        html.append("                <h3>Data Points</h3>\n");
        html.append("                <div class=\"metric-value\">")
                .append(
                        report.getMetricSummaries().values().stream()
                                .mapToInt(KPICollector.MetricSummary::getCount)
                                .sum())
                .append("</div>\n");
        html.append("            </div>\n");

        // Add performance indicators
        addPerformanceCards(html, report);

        html.append("        </div>\n\n");

        // Detailed metrics table
        html.append("        <div class=\"metrics-table\">\n");
        html.append("            <h2>Detailed Metrics</h2>\n");
        html.append("            <table>\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>Metric</th>\n");
        html.append("                        <th>Count</th>\n");
        html.append("                        <th>Mean</th>\n");
        html.append("                        <th>Min</th>\n");
        html.append("                        <th>Max</th>\n");
        html.append("                        <th>Median</th>\n");
        html.append("                        <th>Trend</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");

        report.getMetricSummaries()
                .values()
                .forEach(
                        summary -> {
                            KPICollector.TrendInfo trend =
                                    report.getTrends().get(summary.getMetricName());
                            html.append("                    <tr>\n");
                            html.append("                        <td class=\"metric-name\">")
                                    .append(summary.getMetricName())
                                    .append("</td>\n");
                            html.append("                        <td>")
                                    .append(summary.getCount())
                                    .append("</td>\n");
                            html.append("                        <td>")
                                    .append(String.format("%.2f", summary.getMean()))
                                    .append("</td>\n");
                            html.append("                        <td>")
                                    .append(String.format("%.2f", summary.getMin()))
                                    .append("</td>\n");
                            html.append("                        <td>")
                                    .append(String.format("%.2f", summary.getMax()))
                                    .append("</td>\n");
                            html.append("                        <td>")
                                    .append(String.format("%.2f", summary.getMedian()))
                                    .append("</td>\n");
                            html.append("                        <td class=\"trend-")
                                    .append(trend.getDirection())
                                    .append("\">")
                                    .append(getTrendIcon(trend.getDirection()))
                                    .append(" ")
                                    .append(String.format("%.1f%%", trend.getChangePercent()))
                                    .append("</td>\n");
                            html.append("                    </tr>\n");
                        });

        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");

        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
 * Generate CSV export of all metrics */
    public String generateCsvReport() {
        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Timestamp,Value,Tags\n");

        collector
                .getAvailableMetrics()
                .forEach(
                        metricName -> {
                            collector
                                    .getMeasurements(metricName)
                                    .forEach(
                                            point -> {
                                                csv.append(metricName).append(",");
                                                csv.append(point.getTimestamp()).append(",");
                                                csv.append(point.getValue()).append(",");
                                                csv.append("\"")
                                                        .append(formatTags(point.getTags()))
                                                        .append("\"");
                                                csv.append("\n");
                                            });
                        });

        return csv.toString();
    }

    /**
 * Save report to file */
    public void saveReport(String content, Path outputPath) throws IOException {
        Files.write(outputPath, content.getBytes());
        logger.info("KPI report saved to: {}", outputPath);
    }

    /**
 * Generate performance improvement suggestions */
    public String generateImprovementSuggestions() {
        KPICollector.KPIReport report = collector.generateReport();
        StringBuilder suggestions = new StringBuilder();

        suggestions.append("PERFORMANCE IMPROVEMENT SUGGESTIONS\n");
        suggestions.append("=".repeat(50)).append("\n\n");

        report.getMetricSummaries()
                .forEach(
                        (metricName, summary) -> {
                            KPICollector.TrendInfo trend = report.getTrends().get(metricName);

                            if (metricName.contains("build.time_to_green")) {
                                analyzeBuildTimeMetrics(suggestions, summary, trend);
                            } else if (metricName.contains("test.success_rate")) {
                                analyzeTestMetrics(suggestions, summary, trend);
                            } else if (metricName.contains("cache.hit_ratio")) {
                                analyzeCacheMetrics(suggestions, summary, trend);
                            } else if (metricName.contains("deployment.time")) {
                                analyzeDeploymentMetrics(suggestions, summary, trend);
                            }
                        });

        return suggestions.toString();
    }

    private String formatMetricSummary(
            KPICollector.MetricSummary summary, KPICollector.TrendInfo trend) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Metric: %s%n", summary.getMetricName()));
        sb.append(String.format("  Count: %d%n", summary.getCount()));
        sb.append(String.format("  Mean: %.2f%n", summary.getMean()));
        sb.append(String.format("  Range: %.2f - %.2f%n", summary.getMin(), summary.getMax()));
        sb.append(String.format("  Median: %.2f%n", summary.getMedian()));
        sb.append(
                String.format(
                        "  Trend: %s (%.1f%%)%n", trend.getDirection(), trend.getChangePercent()));
        return sb.toString();
    }

    private String formatTags(Map<String, String> tags) {
        return tags.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(";"));
    }

    private void addPerformanceCards(StringBuilder html, KPICollector.KPIReport report) {
        // Build success rate
        report.getMetricSummaries().values().stream()
                .filter(s -> s.getMetricName().contains("build.success_rate"))
                .findFirst()
                .ifPresent(
                        summary -> {
                            html.append("            <div class=\"card performance\">\n");
                            html.append("                <h3>Build Success Rate</h3>\n");
                            html.append("                <div class=\"metric-value\">")
                                    .append(String.format("%.1f%%", summary.getMean() * 100))
                                    .append("</div>\n");
                            html.append("            </div>\n");
                        });

        // Test success rate
        report.getMetricSummaries().values().stream()
                .filter(s -> s.getMetricName().contains("test.success_rate"))
                .findFirst()
                .ifPresent(
                        summary -> {
                            html.append("            <div class=\"card performance\">\n");
                            html.append("                <h3>Test Success Rate</h3>\n");
                            html.append("                <div class=\"metric-value\">")
                                    .append(String.format("%.1f%%", summary.getMean() * 100))
                                    .append("</div>\n");
                            html.append("            </div>\n");
                        });
    }

    private String getTrendIcon(String direction) {
        switch (direction) {
            case "increasing":
                return "↗";
            case "decreasing":
                return "↘";
            default:
                return "→";
        }
    }

    private String getHtmlStyles() {
        return """
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            margin: 0;
            padding: 0;
            background-color: #f5f5f5;
        }

        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }

        header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 30px;
            text-align: center;
        }

        header h1 {
            margin: 0 0 10px 0;
            font-size: 2.5em;
        }

        .timestamp {
            margin: 0;
            opacity: 0.9;
        }

        .summary-cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }

        .card {
            background: white;
            padding: 25px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            text-align: center;
        }

        .card h3 {
            margin: 0 0 15px 0;
            color: #333;
            font-size: 1.1em;
        }

        .metric-value {
            font-size: 2.5em;
            font-weight: bold;
            color: #667eea;
        }

        .card.performance .metric-value {
            color: #28a745;
        }

        .metrics-table {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }

        .metrics-table h2 {
            margin: 0 0 20px 0;
            color: #333;
        }

        table {
            width: 100%;
            border-collapse: collapse;
        }

        th, td {
            padding: 12px;
            text-align: left;
            border-bottom: 1px solid #ddd;
        }

        th {
            background-color: #f8f9fa;
            font-weight: 600;
            color: #495057;
        }

        .metric-name {
            font-weight: 500;
            color: #495057;
        }

        .trend-increasing {
            color: #28a745;
            font-weight: bold;
        }

        .trend-decreasing {
            color: #dc3545;
            font-weight: bold;
        }

        .trend-stable {
            color: #6c757d;
        }

        tr:hover {
            background-color: #f8f9fa;
        }
        """;
    }

    private void analyzeBuildTimeMetrics(
            StringBuilder suggestions,
            KPICollector.MetricSummary summary,
            KPICollector.TrendInfo trend) {
        suggestions.append("BUILD TIME OPTIMIZATION:\n");

        if (summary.getMean() > 300000) { // > 5 minutes
            suggestions.append("- Consider implementing incremental builds\n");
            suggestions.append("- Enable build caching and parallelization\n");
            suggestions.append("- Review dependency resolution strategies\n");
        }

        if ("increasing".equals(trend.getDirection())) {
            suggestions.append("- Build times are increasing - investigate recent changes\n");
            suggestions.append("- Consider build performance profiling\n");
        }

        suggestions.append("\n");
    }

    private void analyzeTestMetrics(
            StringBuilder suggestions,
            KPICollector.MetricSummary summary,
            KPICollector.TrendInfo trend) {
        suggestions.append("TEST PERFORMANCE:\n");

        if (summary.getMean() < 0.95) { // < 95% success rate
            suggestions.append("- Test success rate is below target (95%)\n");
            suggestions.append("- Review flaky tests and improve test stability\n");
            suggestions.append("- Consider test isolation improvements\n");
        }

        if ("decreasing".equals(trend.getDirection())) {
            suggestions.append("- Test success rate is declining - immediate attention needed\n");
        }

        suggestions.append("\n");
    }

    private void analyzeCacheMetrics(
            StringBuilder suggestions,
            KPICollector.MetricSummary summary,
            KPICollector.TrendInfo trend) {
        suggestions.append("CACHE OPTIMIZATION:\n");

        if (summary.getMean() < 0.8) { // < 80% hit rate
            suggestions.append("- Cache hit ratio is below optimal (80%)\n");
            suggestions.append("- Review cache sizing and eviction policies\n");
            suggestions.append("- Consider cache warming strategies\n");
        }

        suggestions.append("\n");
    }

    private void analyzeDeploymentMetrics(
            StringBuilder suggestions,
            KPICollector.MetricSummary summary,
            KPICollector.TrendInfo trend) {
        suggestions.append("DEPLOYMENT OPTIMIZATION:\n");

        if (summary.getMean() > 600000) { // > 10 minutes
            suggestions.append("- Deployment times are high - consider optimization\n");
            suggestions.append("- Review deployment pipeline for bottlenecks\n");
            suggestions.append("- Consider blue-green or rolling deployment strategies\n");
        }

        suggestions.append("\n");
    }
}
