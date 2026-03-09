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

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Main entry point for the YAPPC (Yet Another Project/Package Creator) CLI tool.
 *
 * <p>This tool provides scaffolding capabilities for polyglot projects with built-in AI assistance,
 * alignment with Polyfix codemods, and production-grade defaults.
 *
 * @see <a href="https://github.com/ghatana/yappc">YAPPC Documentation</a>
 */
@Command(
        name = "yappc",
        mixinStandardHelpOptions = true,
        version = "yappc 1.0.0",
        description =
                "Yet Another Project/Package Creator - Polyglot scaffolding with AI assistance",
        subcommands = {
            InitCommand.class,
            CreateCommand.class,
            AddCommand.class,
            UpdateCommand.class,
            PacksCommand.class,
            DoctorCommand.class,
            GraphCommand.class,
            AICommand.class,
            com.ghatana.yappc.cli.commands.DepsUpgradeCommand.class,
            com.ghatana.yappc.cli.commands.DepsCheckCommand.class,
            com.ghatana.yappc.cli.commands.SecurityCommand.class,
            com.ghatana.yappc.cli.commands.PolicyCommand.class,
            com.ghatana.yappc.cli.commands.ReleaseCommand.class,
            com.ghatana.yappc.cli.commands.SbomCommand.class
        })
/**
 * YappcEntryPoint component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose YappcEntryPoint component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class YappcEntryPoint implements Runnable {

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose;

    @Option(
            names = {"--config"},
            description = "Configuration file path (default: .yappc/config.json)")
    private String configPath = ".yappc/config.json";

    public static void main(String[] args) {
        int exitCode = new CommandLine(new YappcEntryPoint()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // When no subcommand is specified, show help
        CommandLine.usage(this, System.out);
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getConfigPath() {
        return configPath;
    }
}
