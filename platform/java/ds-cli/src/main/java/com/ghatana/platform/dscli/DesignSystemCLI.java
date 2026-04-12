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
package com.ghatana.platform.dscli;

import com.ghatana.platform.dscli.commands.AuditCommand;
import com.ghatana.platform.dscli.commands.BuildTokensCommand;
import com.ghatana.platform.dscli.commands.ValidateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Entry point for the Ghatana Design System CLI.
 *
 * <p>Provides DTCG-compliant token build, validation, and governance auditing commands
 * for the Ghatana design system platform.
 *
 * @doc.type class
 * @doc.purpose Main entry point for ds-cli, the design system governance toolchain.
 * @doc.layer platform
 * @doc.pattern Command
 */
@Command(
        name = "ds",
        mixinStandardHelpOptions = true,
        version = "ds-cli 0.1.0",
        description = "Ghatana Design System CLI - DTCG token build, validate, and audit",
        subcommands = {
            BuildTokensCommand.class,
            ValidateCommand.class,
            AuditCommand.class,
        })
public class DesignSystemCLI implements Runnable {

    @Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose output")
    private boolean verbose;

    public static void main(final String[] args) {
        final int exitCode = new CommandLine(new DesignSystemCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public boolean isVerbose() {
        return verbose;
    }
}
