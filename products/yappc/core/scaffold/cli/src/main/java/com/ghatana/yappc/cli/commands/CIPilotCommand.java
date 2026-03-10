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

import com.ghatana.yappc.core.pilot.CIPilotTestRunner;
import io.activej.eventloop.Eventloop;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command to run CI pilot tests for validation.
 *
 * <p>Week 8 Day 40: Real repository pilot testing command.
 */
@CommandLine.Command(
        name = "pilot",
        description = "Run CI pipeline pilot tests against real repositories",
        mixinStandardHelpOptions = true)
/**
 * CIPilotCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose CIPilotCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CIPilotCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CIPilotCommand.class);

    @CommandLine.Option(
            names = {"-r", "--repository"},
            description = "Specific repository to test (optional)")
    private String repository;

    @CommandLine.Option(
            names = {"-p", "--platform"},
            description = "Specific platform to test (github-actions, gitlab-ci, azure-devops)")
    private String platform;

    @CommandLine.Option(
            names = {"--timeout"},
            description = "Test timeout in minutes",
            defaultValue = "15")
    private int timeoutMinutes;

    @CommandLine.Option(
            names = {"--report-only"},
            description = "Generate report from existing results without running new tests")
    private boolean reportOnly;

    @Override
    public Integer call() throws Exception {
        log.info("🧪 CI Pilot Test Runner");
        log.info("Testing generated CI pipelines against real repository structures");
        log.info("");;

        if (reportOnly) {
            log.info("📊 Report-only mode - showing previous results");
            // In a full implementation, would load and display previous results
            log.info("⚠️  Previous results not found. Run without --report-only to execute tests.");
            return 0;
        }

        CIPilotTestRunner runner = new CIPilotTestRunner();
        try {
            if (repository != null && platform != null) {
                log.info("🔍 Running single test: {} on {}", repository, platform);
                return runSingleTest(runner);
            } else {
                log.info("🚀 Running full pilot test suite...");
                return runFullSuite(runner);
            }
        } catch (Exception e) {
            log.error("❌ Pilot test execution failed: {}", e.getMessage());
            e.printStackTrace();
            return 1;
        } finally {
            runner.shutdown();
        }
    }

    private int runSingleTest(CIPilotTestRunner runner) {
        // For simplicity, this would need the repository configuration to be available
        log.info("ℹ️  Single test execution not yet implemented.");
        log.info("Use full suite mode to run all configured repository tests.");
        return 0;
    }

    private int runFullSuite(CIPilotTestRunner runner) {
        try {
            AtomicReference<CIPilotTestRunner.PilotTestSuite> ref = new AtomicReference<>();
            Eventloop eventloop = Eventloop.create();
            eventloop.post(() -> runner.runPilotTests().whenResult(ref::set));
            eventloop.run();
            var suiteResult = ref.get();

            log.info("");;
            log.info("📋 Pilot Test Results:");
            log.info("  Total Tests: {}", suiteResult.summary().totalTests());
            log.info("  Passed: {}", suiteResult.summary().passedTests());
            log.info("  Failed: {}", suiteResult.summary().failedTests());
            log.info(String.format("  Success Rate: %.1f%%", suiteResult.summary().successRate() * 100));
            log.info(String.format("  Average Duration: %.1f ms", suiteResult.summary().averageDurationMs()));

            log.info("");;
            log.info("🏆 Platform Performance:");
            suiteResult
                    .summary()
                    .platformResults()
                    .forEach(
                            (platform, successes) -> {
                                long total =
                                        suiteResult.results().stream()
                                                .filter(r -> r.platform().equals(platform))
                                                .count();
                                double rate = total > 0 ? (double) successes / total * 100 : 0;
                                log.info(String.format("  %s: %d/%d (%.1f%%)", platform, successes, total, rate));
                            });

            if (suiteResult.summary().failedTests() > 0) {
                log.info("");;
                log.info("⚠️  Failed Tests:");
                suiteResult.results().stream()
                        .filter(r -> !r.success())
                        .forEach(
                                r ->
                                        log.info("  ❌ {} ({}): {}", r.repository().name(),
                                                r.platform(),
                                                r.errorMessage()));
            }

            log.info("");;
            log.info("📊 Detailed report available in: reports/pilot-tests/{}-report.md", suiteResult.suiteId());

            return suiteResult.summary().failedTests() == 0 ? 0 : 1;

        } catch (Exception e) {
            log.error("❌ Failed to execute pilot test suite: {}", e.getMessage());
            return 1;
        }
    }
}
