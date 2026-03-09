package com.ghatana.refactorer.performance.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates HTML reports from performance test results. 
 * @doc.type class
 * @doc.purpose Handles report generator operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            log.error("Usage: ReportGenerator --input <results.csv> --output <report.html>");
            System.exit(1);
        }

        String inputFile = "";
        String outputFile = "";

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--input") && i + 1 < args.length) {
                inputFile = args[++i];
            } else if (args[i].equals("--output") && i + 1 < args.length) {
                outputFile = args[++i];
            }
        }

        if (inputFile.isEmpty() || outputFile.isEmpty()) {
            log.error("Both --input and --output parameters are required");
            System.exit(1);
        }

        // Generate the report
        new ReportGenerator().generateReport(inputFile, outputFile);
    }

    public void generateReport(String inputFile, String outputFile) throws IOException {
        // Read the CSV file
        List<Map<String, String>> results = readCsv(inputFile);
        if (results.isEmpty()) {
            log.error("No data found in input file: {}", inputFile);
            return;
        }

        // Group by test size
        Map<String, List<Map<String, String>>> resultsBySize =
                results.stream().collect(Collectors.groupingBy(m -> m.get("size")));

        // Generate the HTML report
        String html = generateHtmlReport(resultsBySize);

        // Write the report to file
        Files.createDirectories(Path.of(outputFile).getParent());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(html);
        }

        log.info("Report generated: {}", outputFile);
    }

    private List<Map<String, String>> readCsv(String filePath) throws IOException {
        List<Map<String, String>> results = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line = reader.readLine();
            if (line == null) {
                return results;
            }

            String[] headers = line.split(",");

            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length != headers.length) {
                    continue; // Skip malformed lines
                }

                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i], values[i]);
                }
                results.add(row);
            }
        }

        return results;
    }

    private String generateHtmlReport(Map<String, List<Map<String, String>>> resultsBySize) {
        StringBuilder html = new StringBuilder();

        // HTML header
        html.append(
                """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Polyfix Performance Report</title>
                <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; color: #333; }
                    .container { max-width: 1200px; margin: 0 auto; }
                    h1 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
                    .chart-container { margin: 30px 0; padding: 20px; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    table { width: 100%; border-collapse: collapse; margin: 20px 0; }
                    th, td { padding: 12px 15px; text-align: left; border-bottom: 1px solid #ddd; }
                    th { background-color: #f8f9fa; font-weight: bold; }
                    tr:hover { background-color: #f5f5f5; }
                    .summary { background: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0; }
                    .badge { display: inline-block; padding: 3px 7px; border-radius: 3px; font-size: 12px; font-weight: bold; }
                    .badge-success { background: #d4edda; color: #155724; }
                    .badge-warning { background: #fff3cd; color: #856404; }
                    .badge-danger { background: #f8d7da; color: #721c24; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Polyfix Performance Report</h1>
                    <div class="summary">
                        <p><strong>Generated:</strong> ${generationTime}</p>
                        <p><strong>Tested sizes:</strong> ${testedSizes}</p>
                    </div>
            """
                        .replace("${generationTime}", DATE_FORMAT.format(Instant.now()))
                        .replace("${testedSizes}", String.join(", ", resultsBySize.keySet())));

        // Add charts
        html.append(
                """
            <div class="chart-container">
                <h2>Performance by File Count</h2>
                <canvas id="performanceChart"></canvas>
            </div>

            <div class="chart-container">
                <h2>Memory Usage</h2>
                <canvas id="memoryChart"></canvas>
            </div>

            <h2>Detailed Results</h2>
            <table>
                <thead>
                    <tr>
                        <th>Size</th>
                        <th>Files</th>
                        <th>Duration (ms)</th>
                        <th>Memory (MB)</th>
                        <th>Passes</th>
                        <th>Edits Applied</th>
                    </tr>
                </thead>
                <tbody>
        """);

        // Add table rows
        for (Map.Entry<String, List<Map<String, String>>> entry : resultsBySize.entrySet()) {
            String size = entry.getKey();
            List<Map<String, String>> runs = entry.getValue();

            // Get the most recent run for this size
            Map<String, String> latestRun = runs.get(runs.size() - 1);

            double memoryMB = Long.parseLong(latestRun.get("memoryUsedBytes")) / (1024.0 * 1024.0);

            html.append(
                    String.format(
                            """
                <tr>
                    <td>%s</td>
                    <td>%s</td>
                    <td>%s</td>
                    <td>%.2f</td>
                    <td>%s</td>
                    <td>%s</td>
                </tr>
                """,
                            size,
                            latestRun.get("fileCount"),
                            latestRun.get("durationMs"),
                            memoryMB,
                            latestRun.get("passes"),
                            latestRun.get("editsApplied")));
        }

        // Close table and add JavaScript for charts
        html.append(
                """
                </tbody>
            </table>

            <script>
                // Performance Chart
                const perfCtx = document.getElementById('performanceChart').getContext('2d');
                new Chart(perfCtx, {
                    type: 'line',
                    data: {
                        labels: ${fileCounts},
                        datasets: [{
                            label: 'Duration (ms)',
                            data: ${durationData},
                            borderColor: 'rgb(75, 192, 192)',
                            tension: 0.1,
                            yAxisID: 'y'
                        }]
                    },
                    options: {
                        responsive: true,
                        interaction: {
                            mode: 'index',
                            intersect: false,
                        },
                        scales: {
                            y: {
                                type: 'linear',
                                display: true,
                                position: 'left',
                                title: {
                                    display: true,
                                    text: 'Duration (ms)'
                                }
                            }
                        }
                    }
                });

                // Memory Chart
                const memCtx = document.getElementById('memoryChart').getContext('2d');
                new Chart(memCtx, {
                    type: 'bar',
                    data: {
                        labels: ${fileCounts},
                        datasets: [{
                            label: 'Memory Used (MB)',
                            data: ${memoryData},
                            backgroundColor: 'rgba(54, 162, 235, 0.5)',
                            borderColor: 'rgb(54, 162, 235)',
                            borderWidth: 1
                        }]
                    },
                    options: {
                        responsive: true,
                        scales: {
                            y: {
                                beginAtZero: true,
                                title: {
                                    display: true,
                                    text: 'Memory (MB)'
                                }
                            }
                        }
                    }
                });
            </script>
            </div>
            </body>
            </html>
        """);

        // Prepare data for charts
        JsonArray fileCounts = new JsonArray();
        JsonArray durationData = new JsonArray();
        JsonArray memoryData = new JsonArray();

        resultsBySize.entrySet().stream()
                .sorted(
                        Comparator.comparingInt(
                                e -> Integer.parseInt(e.getValue().get(0).get("fileCount"))))
                .forEach(
                        entry -> {
                            Map<String, String> latestRun =
                                    entry.getValue().get(entry.getValue().size() - 1);
                            fileCounts.add(latestRun.get("fileCount"));
                            durationData.add(latestRun.get("durationMs"));
                            memoryData.add(
                                    String.format(
                                            "%.2f",
                                            Long.parseLong(latestRun.get("memoryUsedBytes"))
                                                    / (1024.0 * 1024.0)));
                        });

        // Replace placeholders with actual data
        return html.toString()
                .replace("'${fileCounts}'", fileCounts.toString())
                .replace("'${durationData}'", durationData.toString())
                .replace("'${memoryData}'", memoryData.toString());
    }
}
