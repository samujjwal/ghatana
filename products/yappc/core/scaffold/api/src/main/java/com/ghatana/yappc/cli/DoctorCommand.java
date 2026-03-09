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

import com.ghatana.yappc.core.doctor.DoctorRunner;
import com.ghatana.yappc.core.doctor.ToolCheckResult;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Doctor command - validates environment and tooling. Week 1, Day 3 deliverable: doctor checks
 * (JDK, Docker, Buf/protoc, Node/pnpm, Rust).
 */
@Command(name = "doctor", description = "Check environment and tooling requirements")
/**
 * DoctorCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose DoctorCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class DoctorCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(DoctorCommand.class);

    @Option(
            names = {"--json"},
            description = "Output results in JSON format")
    private boolean jsonOutput;

    @Option(
            names = {"--fix"},
            description = "Attempt to fix issues where possible")
    private boolean autoFix;

    // Optional injection point used by tests to provide a fake/stub DoctorRunner
    private com.ghatana.yappc.core.doctor.DoctorRunner doctorRunner;

    public DoctorCommand() {}

    // Constructor for tests and injection
    public DoctorCommand(com.ghatana.yappc.core.doctor.DoctorRunner doctorRunner) {
        this.doctorRunner = doctorRunner;
    }

    @Override
    public Integer call() throws Exception {
        log.info("🔍 Running YAPPC Doctor...");

        com.ghatana.yappc.core.doctor.DoctorRunner doctor =
                this.doctorRunner != null ? this.doctorRunner : new DoctorRunner();
        List<ToolCheckResult> results = doctor.runAllChecks();

        if (jsonOutput) {
            doctor.outputJson(results);
        } else {
            // Keep a human-readable header so tests (and users) see a stable label
            log.info("System Requirements Check");
            doctor.outputConsole(results);
        }

        long errorCount = results.stream().filter(r -> !r.available()).count();

        if (errorCount > 0) {
            log.info("%n❌ Found {} issues", errorCount);
            return 1;
        } else {
            log.info("\n✅ All checks passed");
            return 0;
        }
    }
}
