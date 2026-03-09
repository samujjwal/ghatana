package com.ghatana.yappc.cli.commands;

import com.ghatana.yappc.core.profiling.PerformanceProfiler;
import com.ghatana.yappc.core.profiling.PerformanceProfiler.ExportFormat;
import com.ghatana.yappc.core.profiling.PerformanceProfiler.PerformanceSnapshot;
import com.ghatana.yappc.core.profiling.PerformanceProfiler.ProfileReport;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Performance Profiling Command Week 11 Day 54: JVM and application performance profiling */
@Command(
        name = "profile",
        description = "Advanced performance profiling and monitoring",
        subcommands = {
            ProfileCommand.StartCommand.class,
            ProfileCommand.StopCommand.class,
            ProfileCommand.SnapshotCommand.class,
            ProfileCommand.AnalyzeCommand.class
        })
/**
 * ProfileCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose ProfileCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class ProfileCommand implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(ProfileCommand.class);

    @Override
    public Integer call() {
        logger.info("YAPPC Performance Profiler");
        logger.info("Advanced JVM and application performance monitoring");
        logger.info("");;
        logger.info("Available commands:");
        logger.info("  start     - Start profiling session");
        logger.info("  stop      - Stop profiling session and generate report");
        logger.info("  snapshot  - Take immediate performance snapshot");
        logger.info("  analyze   - Analyze existing profiling data");
        return 0;
    }

    @Command(name = "start", description = "Start a new profiling session")
    static class StartCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Session name")
        private String sessionName;

        @Option(
                names = {"-i", "--interval"},
                description = "Sampling interval in milliseconds",
                defaultValue = "1000")
        private long intervalMs;

        @Option(
                names = {"--max-samples"},
                description = "Maximum samples per session",
                defaultValue = "10000")
        private int maxSamples;

        @Override
        public Integer call() {
            try {
                PerformanceProfiler profiler = new PerformanceProfiler();

                logger.info("🚀 Starting profiling session: {}", sessionName);
                logger.info("📊 Sampling interval: {}ms", intervalMs);
                logger.info("📈 Max samples: {}", maxSamples);
                logger.info("");;

                String sessionId = profiler.startProfilingSession(sessionName);

                logger.info("✅ Profiling session started successfully!");
                logger.info("📋 Session ID: {}", sessionId);
                logger.info("");;
                logger.info("💡 Use 'yappc profile stop {}' to stop and generate report", sessionName);

                // Store session info for later use (in real implementation)
                System.setProperty("yappc.profile.active.session", sessionId);

                return 0;

            } catch (Exception e) {
                logger.error("Failed to start profiling session", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "stop", description = "Stop profiling session and generate report")
    static class StopCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Session name or ID", defaultValue = "")
        private String sessionId;

        @Option(
                names = {"-o", "--output"},
                description = "Output directory",
                defaultValue = ".profiling")
        private String outputDir;

        @Option(
                names = {"--format"},
                description = "Report format (json, html, csv)",
                defaultValue = "html")
        private String format;

        @Override
        public Integer call() {
            try {
                PerformanceProfiler profiler = new PerformanceProfiler();

                // If no session ID provided, use the active session
                if (sessionId.isEmpty()) {
                    sessionId = System.getProperty("yappc.profile.active.session");
                    if (sessionId == null) {
                        logger.error("❌ No active profiling session found");
                        logger.error("   Start a session with 'yappc profile start <name>' first");
                        return 1;
                    }
                }

                logger.info("⏹️  Stopping profiling session: {}", sessionId);

                ProfileReport report = profiler.stopProfilingSession(sessionId);

                // Generate report file
                ExportFormat exportFormat = parseExportFormat(format);
                String filename = "profile-report." + format.toLowerCase();
                Path outputPath = Paths.get(outputDir).resolve(filename);

                profiler.exportReport(report, outputPath, exportFormat);

                logger.info("✅ Profiling session stopped successfully!");
                logger.info("📊 Captured {} performance snapshots", report.snapshotCount);
                logger.info("📄 Report saved to: {}", outputPath);

                // Display summary
                printReportSummary(report);

                // Clear active session
                System.clearProperty("yappc.profile.active.session");

                return 0;

            } catch (Exception e) {
                logger.error("Failed to stop profiling session", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }

        private ExportFormat parseExportFormat(String format) {
            return switch (format.toLowerCase()) {
                case "json" -> ExportFormat.JSON;
                case "html" -> ExportFormat.HTML;
                case "csv" -> ExportFormat.CSV;
                default -> throw new IllegalArgumentException("Unsupported format: " + format);
            };
        }

        private void printReportSummary(ProfileReport report) {
            logger.info("");;
            logger.info("📋 PROFILING SUMMARY:");
            logger.info("-".repeat(30));

            if (report.memoryAnalysis != null) {
                logger.info("💾 MEMORY:");
                if (report.memoryAnalysis.heapUsageStats != null) {
                    logger.info("   Avg Heap Usage: {}", formatBytes(report.memoryAnalysis.heapUsageStats.mean));
                    logger.info("   Max Heap Usage: {}", formatBytes(report.memoryAnalysis.heapUsageStats.max));
                }
                if (report.memoryAnalysis.potentialMemoryLeak) {
                    logger.info("   ⚠️  Potential memory leak detected!");
                }
            }

            if (report.cpuAnalysis != null && report.cpuAnalysis.cpuUsageStats != null) {
                logger.info("⚡ CPU:");
                logger.info("   Avg CPU Usage: {}", String.format( "%.1f%%", report.cpuAnalysis.cpuUsageStats.mean * 100));
                logger.info("   Max CPU Usage: {}", String.format( "%.1f%%", report.cpuAnalysis.cpuUsageStats.max * 100));
            }

            if (!report.recommendations.isEmpty()) {
                logger.info("");;
                logger.info("💡 RECOMMENDATIONS:");
                for (var recommendation : report.recommendations) {
                    String icon = getRecommendationIcon(recommendation.priority);
                    logger.info("   {} {} ({})", icon, recommendation.title, recommendation.priority);
                }
            }
        }

        private String getRecommendationIcon(String priority) {
            return switch (priority) {
                case "CRITICAL" -> "🚨";
                case "HIGH" -> "⚠️";
                case "MEDIUM" -> "💡";
                default -> "ℹ️";
            };
        }

        private String formatBytes(double bytes) {
            String[] units = {"B", "KB", "MB", "GB"};
            int unitIndex = 0;
            double size = bytes;

            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }

            return String.format("%.1f %s", size, units[unitIndex]);
        }
    }

    @Command(name = "snapshot", description = "Take immediate performance snapshot")
    static class SnapshotCommand implements Callable<Integer> {

        @Option(
                names = {"-o", "--output"},
                description = "Output file",
                defaultValue = "snapshot.json")
        private String outputFile;

        @Override
        public Integer call() {
            try {
                PerformanceProfiler profiler = new PerformanceProfiler();

                logger.info("📸 Taking performance snapshot...");

                PerformanceSnapshot snapshot = profiler.getCurrentSnapshot();

                // Export snapshot
                Path outputPath = Paths.get(outputFile);
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .configure(
                                        com.fasterxml.jackson.databind.SerializationFeature
                                                .INDENT_OUTPUT,
                                        true);

                mapper.writeValue(outputPath.toFile(), snapshot);

                logger.info("✅ Snapshot captured successfully!");
                logger.info("📄 Saved to: {}", outputPath);

                // Display key metrics
                printSnapshotSummary(snapshot);

                return 0;

            } catch (Exception e) {
                logger.error("Failed to capture performance snapshot", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }

        private void printSnapshotSummary(PerformanceSnapshot snapshot) {
            logger.info("");;
            logger.info("📊 SNAPSHOT SUMMARY:");
            logger.info("-".repeat(25));
            logger.info("⏰ Timestamp: {}", snapshot.timestamp);

            if (snapshot.memoryUsage != null) {
                logger.info("💾 Memory:");
                logger.info("   Heap Used: {}", formatBytes(snapshot.memoryUsage.heapUsed));
                logger.info("   Heap Max:  {}", formatBytes(snapshot.memoryUsage.heapMax));
            }

            if (snapshot.threadInfo != null) {
                logger.info("🧵 Threads: {}", snapshot.threadInfo.threadCount);
                if (snapshot.threadInfo.deadlockedThreadCount > 0) {
                    logger.info("   ⚠️  Deadlocked threads: {}", snapshot.threadInfo.deadlockedThreadCount);
                }
            }

            if (snapshot.cpuUsage != null && snapshot.cpuUsage.processCpuLoad != null) {
                logger.info("⚡ CPU: {}", String.format("%.1f%%", snapshot.cpuUsage.processCpuLoad * 100));
            }
        }

        private String formatBytes(long bytes) {
            String[] units = {"B", "KB", "MB", "GB"};
            int unitIndex = 0;
            double size = bytes;

            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }

            return String.format("%.1f %s", size, units[unitIndex]);
        }
    }

    @Command(name = "analyze", description = "Analyze performance and provide recommendations")
    static class AnalyzeCommand implements Callable<Integer> {

        @Option(
                names = {"-d", "--duration"},
                description = "Analysis duration in seconds",
                defaultValue = "30")
        private int durationSeconds;

        @Option(
                names = {"--memory"},
                description = "Focus on memory analysis")
        private boolean focusMemory;

        @Option(
                names = {"--cpu"},
                description = "Focus on CPU analysis")
        private boolean focusCpu;

        @Override
        public Integer call() {
            try {
                PerformanceProfiler profiler = new PerformanceProfiler();

                logger.info("🔍 Starting performance analysis...");
                logger.info("⏱️  Duration: {} seconds", durationSeconds);
                logger.info("");;

                // Start temporary session for analysis
                String sessionId =
                        profiler.startProfilingSession("analysis-" + System.currentTimeMillis());

                // Wait for specified duration
                logger.info("📊 Collecting performance data...");

                for (int i = 0; i < durationSeconds; i++) {
                    System.out.print(".");
                    if (i % 10 == 9) System.out.print(" ");
                    logger.info("");;

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                logger.info("");;

                // Generate analysis report
                ProfileReport report = profiler.stopProfilingSession(sessionId);

                logger.info("✅ Analysis completed!");
                logger.info("📊 Analyzed {} samples", report.snapshotCount);
                logger.info("");;

                // Display focused analysis
                if (focusMemory && report.memoryAnalysis != null) {
                    printMemoryAnalysis(report.memoryAnalysis);
                } else if (focusCpu && report.cpuAnalysis != null) {
                    printCpuAnalysis(report.cpuAnalysis);
                } else {
                    printFullAnalysis(report);
                }

                return 0;

            } catch (Exception e) {
                logger.error("Performance analysis failed", e);
                logger.error("❌ Error: {}", e.getMessage());
                return 1;
            }
        }

        private void printMemoryAnalysis(PerformanceProfiler.MemoryAnalysis analysis) {
            logger.info("💾 MEMORY ANALYSIS:");
            logger.info("-".repeat(20));

            if (analysis.heapUsageStats != null) {
                logger.info("Heap Usage:");
                logger.info("  Average: {}", formatBytes(analysis.heapUsageStats.mean));
                logger.info("  Maximum: {}", formatBytes(analysis.heapUsageStats.max));
                logger.info("  Samples: {}", analysis.heapUsageStats.count);
            }

            if (analysis.potentialMemoryLeak) {
                logger.info("");;
                logger.info("⚠️  POTENTIAL MEMORY LEAK DETECTED!");
                logger.info("   Consider running a detailed heap analysis");
            }
        }

        private void printCpuAnalysis(PerformanceProfiler.CpuAnalysis analysis) {
            logger.info("⚡ CPU ANALYSIS:");
            logger.info("-".repeat(15));

            if (analysis.cpuUsageStats != null) {
                logger.info("CPU Usage:");
                logger.info("  Average: {}", String.format("%.1f%%", analysis.cpuUsageStats.mean * 100));
                logger.info("  Maximum: {}", String.format("%.1f%%", analysis.cpuUsageStats.max * 100));
                logger.info("  Samples: {}", analysis.cpuUsageStats.count);
            }

            if (analysis.threadAnalysis != null && analysis.threadAnalysis.hasDeadlocks) {
                logger.info("");;
                logger.info("🚨 DEADLOCKS DETECTED!");
                logger.info("   Review thread synchronization");
            }
        }

        private void printFullAnalysis(ProfileReport report) {
            if (report.memoryAnalysis != null) {
                printMemoryAnalysis(report.memoryAnalysis);
                logger.info("");;
            }

            if (report.cpuAnalysis != null) {
                printCpuAnalysis(report.cpuAnalysis);
                logger.info("");;
            }

            if (!report.recommendations.isEmpty()) {
                logger.info("💡 RECOMMENDATIONS:");
                logger.info("-".repeat(18));
                for (var rec : report.recommendations) {
                    String icon = getRecommendationIcon(rec.priority);
                    logger.info("{} {} ({})", icon, rec.title, rec.priority);
                    logger.info("   {}", rec.description);
                    logger.info("");;
                }
            }
        }

        private String getRecommendationIcon(String priority) {
            return switch (priority) {
                case "CRITICAL" -> "🚨";
                case "HIGH" -> "⚠️";
                case "MEDIUM" -> "💡";
                default -> "ℹ️";
            };
        }

        private String formatBytes(double bytes) {
            String[] units = {"B", "KB", "MB", "GB"};
            int unitIndex = 0;
            double size = bytes;

            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }

            return String.format("%.1f %s", size, units[unitIndex]);
        }
    }
}
