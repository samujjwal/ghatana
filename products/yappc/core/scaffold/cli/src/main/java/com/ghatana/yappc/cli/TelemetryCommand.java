/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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
package com.ghatana.yappc.cli;

import com.ghatana.yappc.core.telemetry.LocalTelemetryCollector;
import com.ghatana.yappc.core.telemetry.model.LocalTelemetryConfiguration;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Day 19: Telemetry opt-in CLI command honoring privacy requirements.
 * Implements Doc1 §5 Observability compliance with user consent management.
 */
@Command(
        name = "telemetry",
        description = "Manage telemetry collection for YAPPC usage improvement",
        subcommands = {
            TelemetryCommand.OptInCommand.class,
            TelemetryCommand.OptOutCommand.class,
            TelemetryCommand.StatusCommand.class
        })
/**
 * TelemetryCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose TelemetryCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class TelemetryCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(TelemetryCommand.class);

    private final LocalTelemetryCollector telemetryCollector = new LocalTelemetryCollector();

    @Override
    public Integer call() throws Exception {
        log.info("📊 YAPPC Telemetry Management");
        log.info("");;
        log.info("Telemetry helps improve YAPPC by collecting anonymized usage data.");
        log.info("Your privacy is our priority - all data is anonymized and stored locally.");
        log.info("");;
        log.info("Available commands:");
        log.info("  • yappc telemetry opt-in     - Enable telemetry collection");
        log.info("  • yappc telemetry opt-out    - Disable telemetry collection");
        log.info("  • yappc telemetry status     - Show current telemetry status");
        log.info("");;
        log.info("💡 Use 'yappc telemetry <command> --help' for detailed usage.");
        return 0;
    }

    /**
     * Day 19: Opt-in command enabling telemetry with user consent.
     */
    @Command(
            name = "opt-in",
            description = "Enable telemetry collection for YAPPC usage improvement")
    static class OptInCommand implements Callable<Integer> {

        @Parameters(description = "Confirmation (type 'yes' to confirm)", arity = "0..1")
        private String confirmation;

        private final LocalTelemetryCollector telemetryCollector = new LocalTelemetryCollector();

        @Override
        public Integer call() throws Exception {
            log.info("📊 Enable YAPPC Telemetry");
            log.info("");;

            if (telemetryCollector.isOptedIn()) {
                log.info("✅ Telemetry is already enabled.");
                log.info("💡 Use 'yappc telemetry status' to see current configuration.");
                return 0;
            }

            log.info("🔒 Privacy-First Telemetry Collection");
            log.info("YAPPC collects anonymized usage data to improve the tool:");
            log.info("");;
            log.info("✅ What we collect:");
            log.info("  • Command usage patterns (init, create, ai commands)");
            log.info("  • Project types and languages (java, typescript, rust)");
            log.info("  • Success/failure rates and performance metrics");
            log.info("  • Pack usage statistics for recommendation improvement");
            log.info("");;
            log.info("❌ What we DON'T collect:");
            log.info("  • Personal information or user identifiers");
            log.info("  • Project names, file paths, or source code");
            log.info("  • Credentials, secrets, or sensitive data");
            log.info("  • Network requests or external service calls");
            log.info("");;
            log.info("📁 Data Storage:");
            log.info("  • Stored locally in .yappc/telemetry/ directory");
            log.info("  • No data transmitted to external servers");
            log.info("  • You can opt-out anytime with 'yappc telemetry opt-out'");
            log.info("");;

            if (!"yes".equalsIgnoreCase(confirmation)) {
                log.info("To enable telemetry, run:");
                log.info("  yappc telemetry opt-in yes");
                log.info("");;
                log.info("Or use 'yappc telemetry opt-out' to explicitly disable.");
                return 1;
            }

            try {
                telemetryCollector.optIn();
                log.info("✅ Telemetry enabled successfully!");
                log.info("");;
                log.info("Thank you for helping improve YAPPC! 🙏");
                log.info("💡 Use 'yappc telemetry status' to verify your settings.");
                return 0;

            } catch (Exception e) {
                log.error("❌ Failed to enable telemetry: {}", e.getMessage());
                return 1;
            }
        }
    }

    /**
     * Day 19: Opt-out command disabling telemetry and clearing data.
     */
    @Command(name = "opt-out", description = "Disable telemetry collection and clear stored data")
    static class OptOutCommand implements Callable<Integer> {

        private final LocalTelemetryCollector telemetryCollector = new LocalTelemetryCollector();

        @Override
        public Integer call() throws Exception {
            log.info("📊 Disable YAPPC Telemetry");
            log.info("");;

            if (!telemetryCollector.isOptedIn()) {
                log.info("ℹ️  Telemetry is already disabled.");
                return 0;
            }

            try {
                telemetryCollector.optOut();
                log.info("✅ Telemetry disabled successfully!");
                log.info("🗑️  All stored telemetry data has been cleared.");
                log.info("");;
                log.info("You can re-enable telemetry anytime with:");
                log.info("  yappc telemetry opt-in yes");
                return 0;

            } catch (Exception e) {
                log.error("❌ Failed to disable telemetry: {}", e.getMessage());
                return 1;
            }
        }
    }

    /**
     * Day 19: Status command showing current telemetry configuration.
     */
    @Command(name = "status", description = "Show current telemetry collection status")
    static class StatusCommand implements Callable<Integer> {

        private final LocalTelemetryCollector telemetryCollector = new LocalTelemetryCollector();

        @Override
        public Integer call() throws Exception {
            log.info("📊 YAPPC Telemetry Status");
            log.info("");;

            LocalTelemetryConfiguration config = telemetryCollector.getConfiguration();

            log.info("Status: {}", config.isOptedIn() ? "✅ ENABLED" : "❌ DISABLED");
            log.info("Configuration Version: {}", config.getVersion() != null ? config.getVersion() : "1.0.0");
            log.info("Created: {}", config.getCreatedAt() != null ? config.getCreatedAt().toString() : "Unknown");

            if (config.getUpdatedAt() != null) {
                log.info("Last Updated: {}", config.getUpdatedAt().toString());
            }

            log.info("");;

            if (config.isOptedIn()) {
                log.info("📁 Data Storage: .yappc/telemetry/");
                log.info("🔒 Privacy: All data is anonymized and stored locally");
                log.info("");;
                log.info("💡 To disable: yappc telemetry opt-out");
            } else {
                log.info("💡 To enable: yappc telemetry opt-in yes");
                log.info("📖 Learn more: yappc telemetry opt-in");
            }

            return 0;
        }
    }
}
