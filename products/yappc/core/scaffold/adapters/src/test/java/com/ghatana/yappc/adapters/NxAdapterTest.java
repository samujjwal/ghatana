package com.ghatana.yappc.adapters;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.yappc.core.graph.TaskNode;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import com.ghatana.yappc.core.template.TemplateEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles nx adapter test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class NxAdapterTest {

    // Use a persistent test output directory so generated files can be inspected after test runs
    private Path tempDir;

    private NxAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        TemplateEngine templateEngine = new SimpleTemplateEngine();
        adapter = new NxAdapter(templateEngine);

        Path out = Path.of("build/test-artifacts/nxadapter");
        // Clean previous artifacts if present
        if (Files.exists(out)) {
            try (var stream = Files.walk(out)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(
                                p -> {
                                    try {
                                        Files.deleteIfExists(p);
                                    } catch (Exception e) {
                                        // ignore
                                    }
                                });
            }
        }
        Files.createDirectories(out);
        tempDir = out;
    }

    private boolean containsRegex(String content, String regex) {
        return Pattern.compile(regex, Pattern.DOTALL).matcher(content).find();
    }

    @Test
    void shouldReturnCorrectId() {
        assertEquals("nx", adapter.id());
    }

    @Test
    void shouldDescribeComprehensiveTypeScriptTasks() {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
        List<TaskNode> tasks = adapter.describeTasks(spec);

        assertEquals(9, tasks.size());

        // Verify task names
        List<String> taskNames = tasks.stream().map(TaskNode::taskId).toList();

        assertTrue(taskNames.contains("build"));
        assertTrue(taskNames.contains("lint"));
        assertTrue(taskNames.contains("test"));
        assertTrue(taskNames.contains("typecheck"));
        assertTrue(taskNames.contains("dev"));
        assertTrue(taskNames.contains("serve"));
        assertTrue(taskNames.contains("preview"));
        assertTrue(taskNames.contains("e2e"));
        assertTrue(taskNames.contains("graph"));

        // Verify dependencies
        TaskNode typecheckTask =
                tasks.stream()
                        .filter(task -> "typecheck".equals(task.taskId()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of("build"), typecheckTask.dependsOn());

        TaskNode serveTask =
                tasks.stream()
                        .filter(task -> "serve".equals(task.taskId()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(List.of("build"), serveTask.dependsOn());
    }

    @Test
    void shouldGenerateNxJsonForMonorepo() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
        adapter.generateWorkspaceFiles(spec, tempDir);

        Path nxJsonPath = tempDir.resolve("nx.json");
        assertTrue(Files.exists(nxJsonPath));

        String content = Files.readString(nxJsonPath);

        // Verify nx.json structure
        assertTrue(content.contains("\"$schema\": \"./node_modules/nx/schemas/nx-schema.json\""));
        assertTrue(content.contains("\"namedInputs\""));
        assertTrue(content.contains("\"targetDefaults\""));
        assertTrue(content.contains("\"plugins\""));

        // Verify workspace layout for monorepo
        assertTrue(content.contains("\"appsDir\": \"apps\""));
        assertTrue(content.contains("\"libsDir\": \"libs\""));

        // Verify TypeScript-specific configurations
        assertTrue(content.contains("@nx/eslint/plugin"));
        assertTrue(content.contains("@nx/vite/plugin"));
        assertTrue(content.contains("@nx/jest/plugin"));
        assertTrue(content.contains("@nx/js/plugin"));

        // Verify target defaults
        assertTrue(content.contains("@nx/js:tsc"));
        assertTrue(content.contains("@nx/eslint:lint"));
        assertTrue(content.contains("@nx/jest:jest"));
    }

    @Test
    void shouldGeneratePackageJsonWithTypeScriptDependencies() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
        adapter.generateWorkspaceFiles(spec, tempDir);

        Path packageJsonPath = tempDir.resolve("package.json");
        assertTrue(Files.exists(packageJsonPath));

        String content = Files.readString(packageJsonPath);

        // DEBUG: print package.json for test diagnostics
        System.out.println("--- package.json generated content START ---");
        System.out.println(content);
        System.out.println("--- package.json generated content END ---");

        // DEBUG: print boolean checks (whitespace tolerant)
        System.out.println(
                "has_name_test-workspace="
                        + containsRegex(content, "\\\"name\\\"\\s*:\\s*\\\"test-workspace\\\""));
        System.out.println(
                "has_private_true=" + containsRegex(content, "\\\"private\\\"\\s*:\\s*true"));
        System.out.println(
                "has_workspaces=" + containsRegex(content, "\\\"workspaces\\\"\\s*:\\s*\\["));
        System.out.println("has_apps_glob=" + containsRegex(content, "\\\"apps/\\*\\\""));
        System.out.println("has_libs_glob=" + containsRegex(content, "\\\"libs/\\*\\\""));
        System.out.println("has_nx_devkit=" + containsRegex(content, "\\\"@nx/devkit\\\""));
        System.out.println("has_typescript=" + containsRegex(content, "\\\"typescript\\\""));
        System.out.println(
                "has_node_engine="
                        + containsRegex(content, "\\\"node\\\"\\s*:\\s*\\\">=18.0.0\\\""));

        // Verify basic package.json structure
        assertTrue(containsRegex(content, "\\\"name\\\"\\s*:\\s*\\\"test-workspace\\\""));
        assertTrue(containsRegex(content, "\\\"private\\\"\\s*:\\s*true"));

        // Verify workspace configuration for monorepo
        assertTrue(containsRegex(content, "\\\"workspaces\\\"\\s*:\\s*\\["));
        assertTrue(containsRegex(content, "\\\"apps/\\*\\\""));
        assertTrue(containsRegex(content, "\\\"libs/\\*\\\""));

        // Verify TypeScript dependencies per Doc2 §2
        assertTrue(containsRegex(content, "\\\"@nx/devkit\\\""));
        assertTrue(containsRegex(content, "\\\"@nx/eslint\\\""));
        assertTrue(containsRegex(content, "\\\"@nx/jest\\\""));
        assertTrue(containsRegex(content, "\\\"@nx/js\\\""));
        assertTrue(containsRegex(content, "\\\"@nx/typescript\\\""));
        assertTrue(containsRegex(content, "\\\"@nx/vite\\\""));
        assertTrue(containsRegex(content, "\\\"typescript\\\""));
        assertTrue(containsRegex(content, "\\\"eslint\\\""));
        assertTrue(containsRegex(content, "\\\"prettier\\\""));
        assertTrue(containsRegex(content, "\\\"vitest\\\""));

        // Verify scripts for workspace management (tolerant of minor formatting/newlines)
        assertTrue(content.contains("nx run-many") && content.contains("--target=build"));
        assertTrue(content.contains("nx run-many") && content.contains("--target=test"));
        assertTrue(content.contains("nx run-many") && content.contains("--target=lint"));
        assertTrue(content.contains("nx run-many") && content.contains("--target=typecheck"));

        // Verify Node.js version requirements
        assertTrue(containsRegex(content, "\\\"node\\\"\\s*:\\s*\\\">=18.0.0\\\""));
        assertTrue(containsRegex(content, "\\\"pnpm\\\"\\s*:\\s*\\\">=8.0.0\\\""));
    }

    @Test
    void shouldGenerateProjectJsonTemplate() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
        adapter.generateWorkspaceFiles(spec, tempDir);

        Path projectTemplatePath = tempDir.resolve("templates/nx/project.json.template");
        assertTrue(Files.exists(projectTemplatePath));

        String content = Files.readString(projectTemplatePath);

        // DEBUG: print project.json.template for diagnostics
        System.out.println("--- project.json.template START ---");
        System.out.println(content);
        System.out.println("--- project.json.template END ---");
        // DEBUG: boolean checks for project.json.template
        System.out.println(
                "proj_has_schema="
                        + (content.contains("$schema") && content.contains("project-schema.json")));
        System.out.println("proj_has_targets=" + content.contains("\"targets\""));

        // Verify project.json template structure (tolerant of line breaks)
        assertTrue(content.contains("$schema") && content.contains("project-schema.json"));
        assertTrue(content.contains("\"targets\""));

        // Verify TypeScript project targets
        assertTrue(content.contains("\"build\""));
        assertTrue(content.contains("\"dev\""));
        assertTrue(content.contains("\"test\""));
        assertTrue(content.contains("\"lint\""));
        assertTrue(content.contains("\"typecheck\""));

        // Verify executors for TypeScript
        assertTrue(content.contains("@nx/vite:build"));
        assertTrue(content.contains("@nx/vite:dev-server"));
        assertTrue(content.contains("@nx/jest:jest"));
        assertTrue(content.contains("@nx/eslint:lint"));
        assertTrue(content.contains("@nx/js:tsc"));
    }

    @Test
    void shouldGenerateTypeScriptConfigurations() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
        adapter.generateWorkspaceFiles(spec, tempDir);

        // Verify tsconfig.base.json
        Path tsconfigBasePath = tempDir.resolve("tsconfig.base.json");
        assertTrue(Files.exists(tsconfigBasePath));

        String baseContent = Files.readString(tsconfigBasePath);
        // DEBUG: print tsconfig.base.json for diagnostics
        System.out.println("--- tsconfig.base.json START ---");
        System.out.println(baseContent);
        System.out.println("--- tsconfig.base.json END ---");
        // DEBUG: boolean checks for tsconfig.base (whitespace tolerant)
        System.out.println(
                "has_compilerOptions=" + containsRegex(baseContent, "\\\"compilerOptions\\\""));
        System.out.println(
                "has_target_ES2022="
                        + containsRegex(baseContent, "\\\"target\\\"\\s*:\\s*\\\"ES2022\\\""));
        System.out.println(
                "has_module_ESNext="
                        + containsRegex(baseContent, "\\\"module\\\"\\s*:\\s*\\\"ESNext\\\""));
        System.out.println(
                "has_moduleResolution_bundler="
                        + containsRegex(
                                baseContent, "\\\"moduleResolution\\\"\\s*:\\s*\\\"bundler\\\""));
        System.out.println(
                "has_strict_true=" + containsRegex(baseContent, "\\\"strict\\\"\\s*:\\s*true"));
        System.out.println(
                "has_declaration_true="
                        + containsRegex(baseContent, "\\\"declaration\\\"\\s*:\\s*true"));
        System.out.println(
                "has_sourceMap_true="
                        + containsRegex(baseContent, "\\\"sourceMap\\\"\\s*:\\s*true"));
        assertTrue(containsRegex(baseContent, "\\\"compilerOptions\\\""));
        assertTrue(containsRegex(baseContent, "\\\"target\\\"\\s*:\\s*\\\"ES2022\\\""));
        assertTrue(containsRegex(baseContent, "\\\"module\\\"\\s*:\\s*\\\"ESNext\\\""));
        assertTrue(containsRegex(baseContent, "\\\"moduleResolution\\\"\\s*:\\s*\\\"bundler\\\""));
        assertTrue(containsRegex(baseContent, "\\\"strict\\\"\\s*:\\s*true"));
        assertTrue(containsRegex(baseContent, "\\\"declaration\\\"\\s*:\\s*true"));
        assertTrue(containsRegex(baseContent, "\\\"sourceMap\\\"\\s*:\\s*true"));

        // Verify workspace tsconfig.json
        Path tsconfigPath = tempDir.resolve("tsconfig.json");
        assertTrue(Files.exists(tsconfigPath));

        String content = Files.readString(tsconfigPath);
        // DEBUG: print tsconfig.json for diagnostics
        System.out.println("--- tsconfig.json START ---");
        System.out.println(content);
        System.out.println("--- tsconfig.json END ---");
        // DEBUG: boolean checks for tsconfig.json
        System.out.println(
                "tsconfig_extends_base="
                        + content.contains("\"extends\": \"./tsconfig.base.json\""));
        System.out.println("tsconfig_has_apps_glob=" + content.contains("\"apps/**/*\""));
        System.out.println("tsconfig_has_libs_glob=" + content.contains("\"libs/**/*\""));
        assertTrue(content.contains("\"extends\": \"./tsconfig.base.json\""));
        assertTrue(content.contains("\"apps/**/*\""));
        assertTrue(content.contains("\"libs/**/*\""));
    }

    @Test
    void shouldGenerateESLintConfiguration() throws Exception {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
        adapter.generateWorkspaceFiles(spec, tempDir);

        Path eslintPath = tempDir.resolve("eslint.config.mjs");
        assertTrue(Files.exists(eslintPath));

        String content = Files.readString(eslintPath);

        // Verify ESLint configuration structure
        assertTrue(content.contains("import nx from '@nx/eslint-plugin'"));
        assertTrue(content.contains("export default"));
        assertTrue(content.contains("nx.configs['flat/base']"));
        assertTrue(content.contains("nx.configs['flat/typescript']"));
        assertTrue(content.contains("@nx/enforce-module-boundaries"));
        assertTrue(content.contains("@typescript-eslint/no-unused-vars"));
        assertTrue(content.contains("@typescript-eslint/consistent-type-imports"));

        // Verify ignored patterns
        assertTrue(content.contains("'**/dist'"));
        assertTrue(content.contains("'**/node_modules'"));
        assertTrue(content.contains("'**/.nx/**'"));
        assertTrue(content.contains("'**/coverage'"));
    }

    @Test
    void shouldHandleSingleProjectMode() throws Exception {
        WorkspaceSpec spec = new WorkspaceSpec("single-app", "single");
        adapter.generateWorkspaceFiles(spec, tempDir);

        // Verify nx.json doesn't include monorepo-specific configuration
        Path nxJsonPath = tempDir.resolve("nx.json");
        String nxContent = Files.readString(nxJsonPath);
        assertFalse(nxContent.contains("\"appsDir\""));
        assertFalse(nxContent.contains("\"libsDir\""));

        // Verify package.json doesn't include workspaces configuration
        Path packageJsonPath = tempDir.resolve("package.json");
        String packageContent = Files.readString(packageJsonPath);
        assertFalse(packageContent.contains("\"workspaces\""));

        // Verify tsconfig includes direct source patterns
        Path tsconfigPath = tempDir.resolve("tsconfig.json");
        String tsconfigContent = Files.readString(tsconfigPath);
        assertFalse(tsconfigContent.contains("\"apps/**/*\""));
        assertFalse(tsconfigContent.contains("\"libs/**/*\""));
    }

    @Test
    void shouldThrowExceptionWhenTemplateEngineNotConfigured() {
        NxAdapter adapterWithoutEngine = new NxAdapter();
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test");

        assertThrows(
                Exception.class,
                () -> {
                    adapterWithoutEngine.generateWorkspaceFiles(spec, tempDir);
                });
    }

    @Test
    void shouldSupportAIReadinessMetadataExposure() {
        WorkspaceSpec spec = WorkspaceSpec.defaultMonorepo("test-workspace");
        List<TaskNode> tasks = adapter.describeTasks(spec);

        // Verify tasks expose metadata for Week 4 AI recommend/generate
        assertFalse(tasks.isEmpty());

        // Each task should have the adapter id for identification
        tasks.forEach(
                task -> {
                    assertEquals("nx", task.adapterId());
                    assertNotNull(task.taskId());
                    assertNotNull(task.dependsOn());
                });

        // Verify comprehensive task coverage for TypeScript ecosystem
        List<String> taskNames = tasks.stream().map(TaskNode::taskId).toList();

        // Essential TypeScript development workflow
        assertTrue(taskNames.contains("build"), "Build task required for TypeScript compilation");
        assertTrue(taskNames.contains("dev"), "Dev task required for development server");
        assertTrue(taskNames.contains("test"), "Test task required for quality assurance");
        assertTrue(taskNames.contains("lint"), "Lint task required for code quality");
        assertTrue(
                taskNames.contains("typecheck"),
                "Typecheck task required for TypeScript validation");

        // Advanced workflow tasks
        assertTrue(taskNames.contains("serve"), "Serve task required for production preview");
        assertTrue(taskNames.contains("e2e"), "E2E task required for integration testing");
        assertTrue(taskNames.contains("graph"), "Graph task required for dependency visualization");
    }
}
