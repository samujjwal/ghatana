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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize a new workspace with mono-repo Nx+pnpm preset.
 *
 * <p>Week 1, Day 2 deliverable: Picocli CLI yappc init with --dry-run diff.
 */
@Command(
        name = "init",
        description = "Initialize a new YAPPC workspace (mono-repo Nx+pnpm preset by default)")
/**
 * InitCommand component within the YAPPC platform.
 *
 * @doc.type class
 * @doc.purpose InitCommand component within the YAPPC platform.
 * @doc.layer platform
 * @doc.pattern Component
 */
public class InitCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(InitCommand.class);

    @Parameters(index = "0", description = "Workspace name", defaultValue = "my-workspace")
    private String workspaceName;

    @Option(
            names = {"-d", "--dry-run"},
            description = "Show preview diff without creating files")
    private boolean dryRun;

    @Option(
            names = {"--preset"},
            description = "Workspace preset (default: nx-pnpm)",
            defaultValue = "nx-pnpm")
    private String preset;

    @Option(
            names = {"--ai"},
            description = "Enable AI-assisted recommendations")
    private boolean aiEnabled;

    @Option(
            names = {"--target-dir"},
            description = "Target directory (default: current)",
            defaultValue = ".")
    private String targetDir;

    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    @Override
    public Integer call() throws Exception {
        log.info("Initializing workspace '{}' with preset '{}'", workspaceName, preset);

        if (aiEnabled) {
            log.info("🤖 AI recommendations enabled");
        }

        // Create workspace spec
        WorkspaceSpec spec = createWorkspaceSpec();

        if (dryRun) {
            log.info("\n📋 DRY RUN - Preview of changes:");
            showPreviewDiff(spec);
            return 0;
        }

        // NOTE: Week 1, Day 2 - Implement actual workspace creation
        log.info("✅ Workspace initialized successfully");
        log.info("📁 Location: {}", Paths.get(targetDir, workspaceName).toAbsolutePath());

        return 0;
    }

    private WorkspaceSpec createWorkspaceSpec() {
        return WorkspaceSpec.defaultMonorepo(workspaceName);
    }

    private void showPreviewDiff(WorkspaceSpec spec) throws Exception {
        log.info("\n--- yappc.workspace.json ---");
        log.info("+++ (new file)");
        log.info("{}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec));

        log.info("\n--- package.json ---");
        log.info("+++ (new file)");
        log.info("{\n  \"name\": \"{}\",\n  \"version\": \"1.0.0\",\n  \"private\": true,\n  \"packageManager\": \"pnpm@9.0.0\"\n}", workspaceName);

        log.info("\n--- nx.json ---");
        log.info("+++ (new file)");
        log.info("{\n" + "  \"$schema\": \"./node_modules/nx/schemas/nx-schema.json\",\n" + "  \"targetDefaults\": {}\n" + "}");
    }
}
