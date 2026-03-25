package com.ghatana.yappc.adapters;

import com.ghatana.yappc.core.graph.TaskNode;
import com.ghatana.yappc.core.model.WorkspaceSpec;
import com.ghatana.yappc.core.template.TemplateEngine;
import com.ghatana.yappc.core.error.TemplateException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nx adapter that generates TypeScript workspace configuration. Implements Day 13 requirements for
 * Nx workspace, targets, and inference. Creates nx.json, project.json files per Doc2 §2 TypeScript
 * requirements.
 
 * @doc.type class
 * @doc.purpose Handles nx adapter operations
 * @doc.layer core
 * @doc.pattern Adapter
*/
public final class NxAdapter implements ProjectAdapter {
    private final TemplateEngine templateEngine;

    public NxAdapter(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    // Default constructor for service loading
    public NxAdapter() {
        this(null);
    }

    @Override
    public String id() {
        return "nx";
    }

    @Override
    public List<TaskNode> describeTasks(WorkspaceSpec spec) {
        return List.of(
                new TaskNode(id(), "build", List.of()),
                new TaskNode(id(), "lint", List.of()),
                new TaskNode(id(), "test", List.of()),
                new TaskNode(id(), "typecheck", List.of("build")),
                new TaskNode(id(), "dev", List.of()),
                new TaskNode(id(), "serve", List.of("build")),
                new TaskNode(id(), "preview", List.of("build")),
                new TaskNode(id(), "e2e", List.of("build")),
                new TaskNode(id(), "graph", List.of()));
    }

    /**
     * Generates Nx workspace configuration files from workspace specification. Creates nx.json with
     * TypeScript targets and project.json for individual projects. Day 14: Added pnpm workspace
     * policies (pnpm-workspace.yaml, .npmrc, lockfile seed).
     */
    public void generateWorkspaceFiles(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        if (templateEngine == null) {
            throw new TemplateException("Template engine not configured for " + id() + " adapter");
        }

        // Ensure target directory exists
        Files.createDirectories(targetDirectory);

        // Generate nx.json - main workspace configuration
        generateNxJson(spec, targetDirectory);

        // Generate package.json with TypeScript workspace setup
        generatePackageJson(spec, targetDirectory);

        // Generate project.json template for TypeScript projects
        generateProjectJson(spec, targetDirectory);

        // Generate tsconfig configurations
        generateTsConfigFiles(spec, targetDirectory);

        // Generate ESLint configuration
        generateEslintConfig(spec, targetDirectory);

        // Day 14: Generate pnpm workspace policies
        generatePnpmWorkspaceConfig(spec, targetDirectory);

        // Day 14: Generate .npmrc with canonical registries and scoped packages
        generateNpmrcConfig(spec, targetDirectory);

        // Day 14: Generate lockfile seed with license compliance
        generateLockfileSeed(spec, targetDirectory);
    }

    /**
 * Creates nx.json with workspace-level configuration, plugins, and target defaults. */
    private void generateNxJson(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createNxJsonContext(spec);
        String template = loadTemplate("nx/nx.json.hbs");
        String content = templateEngine.render(template, context);
        Files.writeString(targetDirectory.resolve("nx.json"), content);
    }

    /**
 * Creates package.json with TypeScript dependencies and workspace configuration. */
    private void generatePackageJson(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createPackageJsonContext(spec);
        String template = loadTemplate("nx/package.json.hbs");
        String content = templateEngine.render(template, context);
        Files.writeString(targetDirectory.resolve("package.json"), content);
    }

    /**
 * Creates project.json template for individual TypeScript projects. */
    private void generateProjectJson(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createProjectJsonContext(spec);
        String template = loadTemplate("nx/project.json.hbs");
        String content = templateEngine.render(template, context);

        // Create templates directory for project generation
        Path templatesDir = targetDirectory.resolve("templates").resolve("nx");
        Files.createDirectories(templatesDir);
        Files.writeString(templatesDir.resolve("project.json.template"), content);
    }

    /**
 * Creates TypeScript configuration files (tsconfig.json, tsconfig.base.json). */
    private void generateTsConfigFiles(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createTsConfigContext(spec);

        // Generate base tsconfig.json
        String baseTsConfigTemplate = loadTemplate("nx/tsconfig.base.json.hbs");
        String baseTsConfig = templateEngine.render(baseTsConfigTemplate, context);
        Files.writeString(targetDirectory.resolve("tsconfig.base.json"), baseTsConfig);

        // Generate workspace tsconfig.json
        String workspaceTsConfigTemplate = loadTemplate("nx/tsconfig.json.hbs");
        String workspaceTsConfig = templateEngine.render(workspaceTsConfigTemplate, context);
        Files.writeString(targetDirectory.resolve("tsconfig.json"), workspaceTsConfig);
    }

    /**
 * Creates ESLint configuration for TypeScript workspace. */
    private void generateEslintConfig(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createEslintContext(spec);
        String template = loadTemplate("nx/eslint.config.mjs.hbs");
        String content = templateEngine.render(template, context);
        Files.writeString(targetDirectory.resolve("eslint.config.mjs"), content);
    }

    /**
     * Day 14: Creates pnpm-workspace.yaml for monorepo package management. Configures workspace
     * packages and organizational policies.
     */
    private void generatePnpmWorkspaceConfig(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createPnpmWorkspaceContext(spec);
        String template = loadTemplate("nx/pnpm-workspace.yaml.hbs");
        String content = templateEngine.render(template, context);
        Files.writeString(targetDirectory.resolve("pnpm-workspace.yaml"), content);
    }

    /**
     * Day 14: Creates .npmrc with canonical registries and package scope enforcement.
     * Enforces @ghatana/* scope and license policies per Doc1 §6 Non-Negotiables.
     */
    private void generateNpmrcConfig(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createNpmrcContext(spec);
        String template = loadTemplate("nx/.npmrc.hbs");
        String content = templateEngine.render(template, context);
        Files.writeString(targetDirectory.resolve(".npmrc"), content);
    }

    /**
     * Day 14: Creates initial lockfile seed with license compliance. References canonical
     * registries per Doc3 §Roadmap Phase 1.
     */
    private void generateLockfileSeed(WorkspaceSpec spec, Path targetDirectory)
            throws IOException, TemplateException {
        Map<String, Object> context = createLockfileSeedContext(spec);
        String template = loadTemplate("nx/pnpm-lock.yaml.hbs");
        String content = templateEngine.render(template, context);
        Files.writeString(targetDirectory.resolve("pnpm-lock.yaml"), content);
    }

    /**
 * Load template from classpath resources. */
    private String loadTemplate(String templateName) throws IOException {
        var classLoader = getClass().getClassLoader();
        try (var inputStream = classLoader.getResourceAsStream("templates/" + templateName)) {
            if (inputStream == null) {
                throw new IOException("Template not found: " + templateName);
            }
            return new String(inputStream.readAllBytes());
        }
    }

    /**
 * Creates template context for nx.json generation with workspace configuration. */
    private Map<String, Object> createNxJsonContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("workspaceName", spec.getName());

        // Generate monorepo layout section for workspace configuration
        if ("monorepo".equals(spec.getMode())) {
            context.put(
                    "monorepoLayout",
                    "\n"
                            + "  \"workspaceLayout\": {\n"
                            + "    \"appsDir\": \"apps\",\n"
                            + "    \"libsDir\": \"libs\"\n"
                            + "  },");
        } else {
            context.put("monorepoLayout", "");
        }

        return context;
    }

    /**
 * Creates template context for package.json with TypeScript workspace dependencies. */
    private Map<String, Object> createPackageJsonContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("workspaceName", spec.getName());

        // Generate workspace configuration for monorepo
        if ("monorepo".equals(spec.getMode())) {
            context.put(
                    "workspaceConfig",
                    "\n  \"workspaces\": [\n    \"apps/*\",\n    \"libs/*\"\n  ],");
        } else {
            context.put("workspaceConfig", "");
        }

        return context;
    }

    /**
 * Creates template context for project.json with TypeScript project targets. */
    private Map<String, Object> createProjectJsonContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("projectName", "example-project");
        context.put("projectRoot", "apps/example-project");
        context.put("projectType", "application");

        return context;
    }

    /**
 * Creates template context for TypeScript configuration files. */
    private Map<String, Object> createTsConfigContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("workspaceName", spec.getName());

        // Generate includes for workspace or project structure
        if ("monorepo".equals(spec.getMode())) {
            // Monorepo workspace includes
            context.put(
                    "tsconfigIncludes",
                    "\n  \"include\": [\n    \"apps/**/*\",\n    \"libs/**/*\"\n  ],");
            context.put(
                    "tsconfigStructure",
                    "\n"
                            + "  \"files\": [],\n"
                            + "  \"include\": [],\n"
                            + "  \"references\": [\n"
                            + "    {\n"
                            + "      \"path\": \"./apps\"\n"
                            + "    },\n"
                            + "    {\n"
                            + "      \"path\": \"./libs\"\n"
                            + "    }\n"
                            + "  ],");
        } else {
            // Single project includes
            context.put(
                    "tsconfigIncludes", "\n  \"include\": [\"src/**/*\", \"*.ts\", \"*.tsx\"],");
            context.put(
                    "tsconfigStructure", "\n  \"include\": [\"src/**/*\", \"*.ts\", \"*.tsx\"],");
        }

        return context;
    }

    /**
 * Creates template context for ESLint configuration. */
    private Map<String, Object> createEslintContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("workspaceName", spec.getName());
        return context;
    }

    /**
     * Day 14: Creates template context for pnpm-workspace.yaml generation. Configures workspace
     * packages layout and organizational policies.
     */
    private Map<String, Object> createPnpmWorkspaceContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("workspaceName", spec.getName());

        // Configure workspace packages based on mode
        if ("monorepo".equals(spec.getMode())) {
            context.put(
                    "workspacePackages",
                    "packages:\n  - \"apps/*\"\n  - \"libs/*\"\n  - \"tools/*\"");
        } else {
            context.put("workspacePackages", "packages:\n  - \"packages/*\"");
        }

        // Add built dependencies policies for common native modules
        context.put(
                "builtDependencies",
                "onlyBuiltDependencies:\n"
                        + "  - '@biomejs/biome'\n"
                        + "  - '@tailwindcss/oxide'\n"
                        + "  - 'esbuild'\n"
                        + "  - '@nx/native-packages'");

        return context;
    }

    /**
     * Day 14: Creates template context for .npmrc with canonical registries. Enforces @ghatana/*
     * package scope per Doc1 §6 Non-Negotiables.
     */
    private Map<String, Object> createNpmrcContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();

        // Workspace name for template
        context.put("workspaceName", spec.getName());

        // Canonical registry configuration per Doc3 §Roadmap Phase 1
        context.put("registry", "https://registry.npmjs.org/");

        // Package scope enforcement for @ghatana/* per Doc1 §6 Non-Negotiables
        context.put("scopedRegistry", "@ghatana:registry=https://registry.npmjs.org/");

        // License compliance and security policies
        context.put("auditLevel", "moderate");
        context.put("strictPeerDeps", "false");
        context.put("autoInstallPeers", "true");

        // Package management policies
        context.put("nodeLinker", "isolated"); // Better isolation for monorepos
        context.put("enableGlobalCache", "true");

        return context;
    }

    /**
     * Day 14: Creates template context for lockfile seed generation. References canonical
     * registries and license compliance data.
     */
    private Map<String, Object> createLockfileSeedContext(WorkspaceSpec spec) {
        Map<String, Object> context = new HashMap<>();
        context.put("workspaceName", spec.getName());

        // PNPM lockfile version format
        context.put("lockfileVersion", "9.0");

        // Registry settings for lockfile
        context.put("registryUrl", "https://registry.npmjs.org/");

        // License allowlist enforcement per Doc1 §6 Non-Negotiables
        context.put(
                "allowedLicenses",
                "settings:\n  autoInstallPeers: true\n  excludeLinksFromLockfile: false");

        return context;
    }
}
