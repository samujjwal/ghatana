#!/usr/bin/env node

import { mkdirSync, writeFileSync, existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

function parseArgs(argv) {
  const args = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index];
    if (!token.startsWith("--")) {
      continue;
    }
    const key = token.slice(2);
    const value = argv[index + 1] && !argv[index + 1].startsWith("--") ? argv[++index] : "true";
    args.set(key, value);
  }
  return args;
}

function requireArg(args, key) {
  const value = args.get(key);
  if (!value || value === "true") {
    throw new Error(`Missing required argument --${key}`);
  }
  return value;
}

function toPascalCase(value) {
  return value
    .split(/[^a-zA-Z0-9]+/)
    .filter(Boolean)
    .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join("");
}

function toJavaPackageSegment(value) {
  return value.replace(/[^a-zA-Z0-9]/g, "").toLowerCase();
}

function ensureDir(path) {
  mkdirSync(path, { recursive: true });
}

function writeFile(path, content) {
  if (existsSync(path)) {
    throw new Error(`Refusing to overwrite existing file: ${path}`);
  }
  writeFileSync(path, content, "utf8");
}

function readText(path) {
  return readFileSync(path, "utf8");
}

function writeText(path, content) {
  writeFileSync(path, content, "utf8");
}

function insertBeforeMarker({ source, marker, content, fileLabel }) {
  if (!source.includes(marker)) {
    throw new Error(`Unable to locate insertion marker ${JSON.stringify(marker)} in ${fileLabel}`);
  }
  return source.replace(marker, `${content}${marker}`);
}

function registerProductShape({
  root,
  id,
  uiEnabled,
  uiMode,
}) {
  const productShapePath = join(root, "config", "product-shape.json");
  const productShape = JSON.parse(readText(productShapePath));

  if (productShape.products[id]) {
    throw new Error(`config/product-shape.json already contains an entry for ${id}`);
  }

  productShape.products[id] = {
    ui: uiEnabled,
    uiMode: uiEnabled ? (uiMode === "none" ? "web" : uiMode) : "backend-only",
    clientPackages: uiEnabled ? [`products/${id}/client/web/package.json`] : [],
  };

  writeText(productShapePath, `${JSON.stringify(productShape, null, 2)}\n`);
}

function registerCanonicalRegistry({
  root,
  id,
  name,
  domain,
  uiEnabled,
}) {
  const registryPath = join(root, "config", "canonical-product-registry.json");
  const registry = JSON.parse(readText(registryPath));

  if (registry.registry[id]) {
    throw new Error(`config/canonical-product-registry.json already contains an entry for ${id}`);
  }

  const surfaces = [
    {
      type: "backend-api",
      implementationStatus: "implemented",
      path: `products/${id}`,
    },
  ];

  if (uiEnabled) {
    surfaces.push({
      type: "web",
      implementationStatus: "implemented",
      path: `products/${id}/client/web`,
      packagePath: `products/${id}/client/web/package.json`,
    });
  }

  registry.registry[id] = {
    id,
    name,
    description: `${name} ${domain} product scaffolded from the Kernel product template`,
    type: "product",
    kind: "business-product",
    manifestPath: `products/${id}/domain-pack-manifest.yaml`,
    manifestFormat: "yaml",
    buildFile: `products/${id}/build.gradle.kts`,
    gradleModules: [`:products:${id}`],
    surfaces,
    pnpmPackages: uiEnabled ? [`products/${id}/client/*`] : [],
    ci: {
      enabled: true,
      gates: ["build", "test", "manifest-validation", "security-scan"],
    },
    conformance: {
      manifest: true,
      observability: true,
      security: true,
      dataAccess: true,
      bridge: false,
      runtimeModule: false,
    },
    metadata: {
      owner: `${name} Team`,
      documentation: `products/${id}/README.md`,
      status: "active",
    },
  };

  writeText(registryPath, `${JSON.stringify(registry, null, 2)}\n`);
}

function registerWorkspace({
  root,
  id,
  name,
  uiEnabled,
}) {
  if (!uiEnabled) {
    return;
  }

  const workspacePath = join(root, "pnpm-workspace.yaml");
  const workspaceSource = readText(workspacePath);
  const registrationLine = `  - "products/${id}/client/*"`;

  if (workspaceSource.includes(registrationLine)) {
    throw new Error(`pnpm-workspace.yaml already contains workspace registration for ${id}`);
  }

  const marker = workspaceSource.includes('  # Shared services')
    ? '  # Shared services'
    : '  # tutorputor product';
  if (!workspaceSource.includes(marker)) {
    throw new Error('Unable to locate product workspace insertion marker in pnpm-workspace.yaml');
  }

  const updated = workspaceSource.replace(
    marker,
    `  # ${name} (${id})\n${registrationLine}\n\n${marker}`,
  );
  writeText(workspacePath, updated);
}

function registerGradleSettings({
  root,
  id,
  name,
}) {
  const settingsPath = join(root, "settings.gradle.kts");
  const settingsSource = readText(settingsPath);
  const includeLine = `include(":products:${id}")`;

  if (settingsSource.includes(includeLine)) {
    throw new Error(`settings.gradle.kts already contains Gradle registration for ${id}`);
  }

  const section = `// =============================================================================
// Product: ${name}
// =============================================================================
${includeLine}

`;

  const marker = `// =============================================================================
// Shared Services
// =============================================================================
`;
  const updated = insertBeforeMarker({
    source: settingsSource,
    marker,
    content: section,
    fileLabel: "settings.gradle.kts",
  });
  writeText(settingsPath, updated);
}

function registerCiMatrices({
  root,
  id,
  name,
  uiEnabled,
}) {
  const coverageWorkflowPath = join(root, ".github", "workflows", "product-coverage-gates.yml");
  const coverageSource = readText(coverageWorkflowPath);
  const coveragePathLine = `      - 'products/${id}/**'`;
  const coverageMatrixLine = `          - product: ${name}
            taskPrefix: ':products:${id}'
            reportPath: 'products/${id}/**/build/reports/jacoco/test/jacocoTestReport.xml'
`;

  if (coverageSource.includes(coveragePathLine) || coverageSource.includes(`taskPrefix: ':products:${id}'`)) {
    throw new Error(`product-coverage-gates.yml already contains CI coverage registration for ${id}`);
  }

  let updatedCoverage = coverageSource;
  updatedCoverage = insertBeforeMarker({
    source: updatedCoverage,
    marker: `      - '.github/workflows/product-coverage-gates.yml'`,
    content: `${coveragePathLine}\n`,
    fileLabel: ".github/workflows/product-coverage-gates.yml",
  });
  updatedCoverage = insertBeforeMarker({
    source: updatedCoverage,
    marker: `          - product: Security Gateway`,
    content: coverageMatrixLine,
    fileLabel: ".github/workflows/product-coverage-gates.yml",
  });
  writeText(coverageWorkflowPath, updatedCoverage);

  const contractWorkflowPath = join(root, ".github", "workflows", "api-contract-conformance.yml");
  const contractSource = readText(contractWorkflowPath);
  const contractPathLine = `      - 'products/${id}/**'`;
  const contractMatrixEntry = `          - product: ${name}
            command: ./gradlew :products:${id}:checkApiContractConformance --no-daemon --stacktrace
            reportPath: products/${id}/build/reports/tests/test/
`;

  if (contractSource.includes(contractPathLine) || contractSource.includes(`:products:${id}:checkApiContractConformance`)) {
    throw new Error(`api-contract-conformance.yml already contains API contract registration for ${id}`);
  }

  let updatedContract = contractSource;
  updatedContract = insertBeforeMarker({
    source: updatedContract,
    marker: `      - 'platform/java/testing/**'`,
    content: `${contractPathLine}\n`,
    fileLabel: ".github/workflows/api-contract-conformance.yml",
  });
  updatedContract = insertBeforeMarker({
    source: updatedContract,
    marker: `
    steps:
`,
    content: `${contractMatrixEntry}`,
    fileLabel: ".github/workflows/api-contract-conformance.yml",
  });
  writeText(contractWorkflowPath, updatedContract);

  if (!uiEnabled) {
    return;
  }

  const visualWorkflowPath = join(root, ".github", "workflows", "visual-regression.yml");
  const visualSource = readText(visualWorkflowPath);
  const visualEntry = `          - product: ${id}-web
            path: products/${id}/client/web
`;
  if (!visualSource.includes(`product: ${id}-web`)) {
    const updatedVisual = insertBeforeMarker({
      source: visualSource,
      marker: `          - product: data-cloud-ui`,
      content: visualEntry,
      fileLabel: ".github/workflows/visual-regression.yml",
    });
    writeText(visualWorkflowPath, updatedVisual);
  }

  const accessibilityWorkflowPath = join(root, ".github", "workflows", "accessibility.yml");
  const accessibilitySource = readText(accessibilityWorkflowPath);
  const accessibilityPathLine = `      - 'products/${id}/client/web/**'`;
  const accessibilityMatrixEntry = `          - product: ${id}-web
            package: '@ghatana/${id}-web'
`;
  let updatedAccessibility = accessibilitySource;
  if (!accessibilitySource.includes(accessibilityPathLine)) {
    updatedAccessibility = insertBeforeMarker({
      source: updatedAccessibility,
      marker: `      - '.github/workflows/accessibility.yml'`,
      content: `${accessibilityPathLine}\n`,
      fileLabel: ".github/workflows/accessibility.yml",
    });
  }
  if (!updatedAccessibility.includes(`product: ${id}-web`)) {
    updatedAccessibility = insertBeforeMarker({
      source: updatedAccessibility,
      marker: `
    steps:
`,
      content: `${accessibilityMatrixEntry}`,
      fileLabel: ".github/workflows/accessibility.yml",
    });
  }
  writeText(accessibilityWorkflowPath, updatedAccessibility);

  const e2eWorkflowPath = join(root, ".github", "workflows", "e2e-tests.yml");
  const e2eSource = readText(e2eWorkflowPath);
  const e2eEntry = `          - product: ${id}-web
            path: products/${id}/client/web
`;
  if (!e2eSource.includes(`product: ${id}-web`)) {
    const updatedE2e = insertBeforeMarker({
      source: e2eSource,
      marker: `          - product: audio-video-desktop`,
      content: e2eEntry,
      fileLabel: ".github/workflows/e2e-tests.yml",
    });
    writeText(e2eWorkflowPath, updatedE2e);
  }

  const performanceWorkflowPath = join(root, ".github", "workflows", "performance-budgets.yml");
  const performanceSource = readText(performanceWorkflowPath);
  const performancePathLine = `      - 'products/${id}/client/**'`;
  const performanceEntry = `          - name: ${id}-web
            path: products/${id}/client/web
            url: http://localhost:4176
            port: 4176
            budgets:
              performance: 85
              accessibility: 95
              best-practices: 90
              seo: 85
              fcp: 2200
              lcp: 3000
              tti: 4200
              cls: 0.1
`;
  let updatedPerformance = performanceSource;
  if (!performanceSource.includes(performancePathLine)) {
    updatedPerformance = insertBeforeMarker({
      source: updatedPerformance,
      marker: `      - 'products/tutorputor/apps/**'`,
      content: `${performancePathLine}\n`,
      fileLabel: ".github/workflows/performance-budgets.yml",
    });
  }
  if (!updatedPerformance.includes(`name: ${id}-web`)) {
    updatedPerformance = insertBeforeMarker({
      source: updatedPerformance,
      marker: `
    steps:
`,
      content: `${performanceEntry}`,
      fileLabel: ".github/workflows/performance-budgets.yml",
    });
  }
  writeText(performanceWorkflowPath, updatedPerformance);
}

const args = parseArgs(process.argv.slice(2));

if (args.has("help")) {
  console.log(`Usage:
  node scripts/scaffold-product.mjs \\
    --id sample-product \\
    --name "Sample Product" \\
    --product-code SAMPLE \\
    --domain sample-domain \\
    --ui web \\
    [--register-product-shape] \\
    [--register-canonical-registry] \\
    [--register-workspace] \\
    [--register-gradle-settings] \\
    [--register-ci-matrices]

Notes:
- Generates a Kernel-aligned product skeleton under products/<id>.
- Can auto-register the product in the canonical registry, config/product-shape.json,
  pnpm-workspace.yaml, settings.gradle.kts, and audited cross-product CI workflows.
- Does not yet mutate every product-specific launcher/runtime specialization automatically.`);
  process.exit(0);
}

const id = requireArg(args, "id");
const name = requireArg(args, "name");
const productCode = requireArg(args, "product-code");
const domain = requireArg(args, "domain");
const uiMode = args.get("ui") ?? "web";

const root = process.cwd();
const productDir = join(root, "products", id);
const packageSegment = toJavaPackageSegment(id);
const classPrefix = toPascalCase(id);
const productScope = `${id}.*`;
const rulePrefix = `${productCode}-BP-`;
const uiEnabled = uiMode !== "none";
const shouldRegisterProductShape = args.has("register-product-shape");
const shouldRegisterCanonicalRegistry = args.has("register-canonical-registry");
const shouldRegisterWorkspace = args.has("register-workspace");
const shouldRegisterGradleSettings = args.has("register-gradle-settings");
const shouldRegisterCiMatrices = args.has("register-ci-matrices");

if (existsSync(productDir)) {
  throw new Error(`Product directory already exists: ${productDir}`);
}

const dirs = [
  productDir,
  join(productDir, "conformance"),
  join(productDir, "docs"),
  join(productDir, "policy-packs"),
  join(productDir, "src", "main", "java", "com", "ghatana", packageSegment, "kernel", "policy"),
  join(productDir, "src", "test", "java", "com", "ghatana", packageSegment, "kernel"),
  join(productDir, "runtime"),
  join(productDir, "deploy"),
];

if (uiEnabled) {
  dirs.push(
    join(productDir, "client", "web", "src"),
    join(productDir, "client", "web", "src", "routes")
  );
}

dirs.forEach(ensureDir);

writeFile(
  join(productDir, "build.gradle.kts"),
  `import com.ghatana.buildlogic.ProductPackValidationExtension

plugins {
    id("java-module")
    id("product-pack-validation")
}

group = "com.ghatana.${packageSegment}"
version = rootProject.version

dependencies {
    api(project(":platform-kernel:kernel-core"))
    implementation(project(":platform-plugins:plugin-compliance"))

    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(libs.bundles.testing.core)
}

tasks.register("checkApiContractConformance") {
    group = "verification"
    description = "Runs scaffolded API contract conformance checks for ${name}."
    dependsOn("productConformanceCheck")
}

configure<ProductPackValidationExtension> {
    productName.set("${name}")
    manifestFile.set(layout.projectDirectory.file("domain-pack-manifest.yaml"))
    policyPackTestPatterns.set(
        listOf("com.ghatana.${packageSegment}.kernel.${classPrefix}PackContractTest")
    )
    complianceSourceFile.set(layout.projectDirectory.file(
        "src/main/java/com/ghatana/${packageSegment}/kernel/policy/${classPrefix}ComplianceRulePack.java"
    ))
    complianceRulePrefix.set("${rulePrefix}")
}
`
);

writeFile(
  join(productDir, "domain-pack-manifest.yaml"),
  `schemaVersion: 1.0.0
id: ${id}
version: 0.1.0
product: ${id}
kind: domain-pack
domain: ${domain}
rulePrefix: ${rulePrefix}
kernelCapabilitiesConsumed:
  - boundary-policy-evaluation
  - audit-trail
  - tenant-context
policyActions:
  - read
  - write
  - delete
policyResources:
  - ${id}:core
policies:
  actions:
    - read
    - write
    - delete
  resources:
    - ${id}:core
pluginsConsumed:
  - plugin-audit-trail
  - plugin-compliance
bridgesConsumed: []
domainPacksProvided:
  - ${id}-boundary-policy
  - ${id}-compliance-rule-pack
uiSurfaces:
${uiEnabled ? "  - web" : "[]"}
runtimeServices:
  - launcher
surfaces:
${uiEnabled ? "  ui:\n    - web" : "  ui: []"}
  runtime:
    - launcher
dataSensitivity: LOW
capabilities:
  - id: ${id}.core
    name: "${name} Core"
    type: BUSINESS_LOGIC
    description: "${name} product-owned core capability"
productExtensions:
  boundaryPolicyStoreClass: com.ghatana.${packageSegment}.kernel.policy.${classPrefix}BoundaryPolicyStore
  pluginBindingsClass: com.ghatana.${packageSegment}.kernel.policy.${classPrefix}PluginBindings
  defaultDenyRuleId: ${rulePrefix}999
`
);

writeFile(
  join(productDir, "conformance", "data-access-context.json"),
  `${JSON.stringify([
    {
      tenantId: `${id}-tenant`,
      principalId: `${id}-principal`,
      correlationId: `${id}-corr-bootstrap`,
      auditClassification: `${id.toUpperCase().replaceAll("-", "_")}_MUTATION`,
      dataOwnerScope: `${id}:core`,
      idempotencyKey: `${id}-bootstrap-idempotency`,
      metadata: {
        product: id,
        source: "scaffolder"
      }
    }
  ], null, 2)}
`
);

writeFile(
  join(productDir, "conformance", "route-entitlements.json"),
  `${JSON.stringify([
    {
      product: id,
      tenantId: `${id}-tenant`,
      principalId: `${id}-principal`,
      role: "admin",
      routes: [
        {
          path: "/",
          label: name
        }
      ],
      actions: [
        {
          id: `${id}:read`,
          label: "Read",
          routePath: "/"
        }
      ],
      cards: [
        {
          id: `${id}-overview`,
          title: `${name} Overview`,
          routePath: "/",
          surface: "dashboard"
        }
      ]
    }
  ], null, 2)}
`
);

writeFile(
  join(productDir, "conformance", "idempotency-observations.json"),
  `${JSON.stringify([
    {
      operation: `${id}:bootstrap-write`,
      key: `${id}-bootstrap-idempotency`,
      fingerprint: `${id}-bootstrap-fingerprint`,
      status: "miss",
      replayed: false,
      expired: false,
      principalId: `${id}-principal`,
      tenantId: `${id}-tenant`,
      correlationId: `${id}-corr-bootstrap`
    },
    {
      operation: `${id}:bootstrap-write`,
      key: `${id}-bootstrap-idempotency`,
      fingerprint: `${id}-bootstrap-fingerprint`,
      status: "completed",
      replayed: true,
      expired: false,
      principalId: `${id}-principal`,
      tenantId: `${id}-tenant`,
      correlationId: `${id}-corr-bootstrap`
    }
  ], null, 2)}
`
);

writeFile(
  join(productDir, "conformance", "observability-flow.json"),
  `${JSON.stringify({
    schemaVersion: "1.0.0",
    requiredFacets: ["trace", "tenantContext", "metrics", "audit", "safeLogging", "redaction"],
    flows: [
      {
        product: id,
        flow: "bootstrap-write",
        kind: "api",
        facets: ["trace", "tenantContext", "metrics", "audit", "safeLogging", "redaction"],
        evidence: [
          {
            type: "source",
            file: `products/${id}/src/test/java/com/ghatana/${packageSegment}/kernel/${classPrefix}PackContractTest.java`,
            tokens: [
              `${classPrefix}PackContractTest`,
              `${rulePrefix}001`
            ]
          }
        ]
      }
    ]
  }, null, 2)}
`
);

writeFile(
  join(productDir, "policy-packs", `${id}-boundary-policy.yaml`),
  `rules:
  - ruleId: ${rulePrefix}001
    sourceScopePattern: "${productScope}"
    targetScopePattern: "${productScope}"
    resourcePattern: "core/**"
    actions: ["read"]
    effect: "ALLOW"
    requiresAudit: true
  - ruleId: ${rulePrefix}999
    sourceScopePattern: "**"
    targetScopePattern: "${productScope}"
    resourcePattern: "**"
    actions: ["*"]
    effect: "DENY"
    requiresAudit: true
`
);

writeFile(
  join(productDir, "policy-packs", `${id}-compliance-rule-pack.yaml`),
  `ruleSets:
  - id: ${productCode}_AUDIT_TRACEABILITY
    rules:
      - ruleId: "${productCode}-COMP-001"
        description: "Product operations must emit auditable events."
`
);

writeFile(
  join(productDir, "src", "main", "java", "com", "ghatana", packageSegment, "kernel", "policy", `${classPrefix}BoundaryPolicyStore.java`),
  `package com.ghatana.${packageSegment}.kernel.policy;

import com.ghatana.kernel.policy.BoundaryPolicyActionRegistry;
import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import com.ghatana.kernel.policy.BoundaryPolicyResourceRegistry;
import com.ghatana.kernel.policy.BoundaryPolicyRule;
import com.ghatana.kernel.policy.BoundaryPolicyStore;
import com.ghatana.kernel.policy.ProductBoundaryPolicyPackValidator;
import com.ghatana.kernel.policy.ProductBoundaryPolicyValidationProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ${classPrefix}BoundaryPolicyStore implements BoundaryPolicyStore {

    private static final ProductBoundaryPolicyValidationProfile VALIDATION_PROFILE =
            ProductBoundaryPolicyValidationProfile.builder()
                    .productName("${name}")
                    .rulePrefix("${rulePrefix}")
                    .defaultDenyRuleId("${rulePrefix}999")
                    .targetScopePrefix("${id}.")
                    .requiredMetadataKeys(Set.of("packVersion", "ruleCategory"))
                    .build();
    private static final BoundaryPolicyActionRegistry ACTION_REGISTRY =
            BoundaryPolicyActionRegistry.ofDeclaredActions(Set.of("read", "write", "delete"));
    private static final BoundaryPolicyResourceRegistry RESOURCE_REGISTRY =
            BoundaryPolicyResourceRegistry.ofDeclaredResources(Set.of("${id}:core"));
    private static final List<BoundaryPolicyRule> RULES = List.of(
            BoundaryPolicyRule.builder()
                    .ruleId("${rulePrefix}001")
                    .sourceScopePattern("${productScope}")
                    .targetScopePattern("${productScope}")
                    .resourcePattern("core/**")
                    .actions("read")
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.ALLOW)
                    .metadata(Map.of("packVersion", "0.1.0", "ruleCategory", "core"))
                    .build(),
            BoundaryPolicyRule.builder()
                    .ruleId("${rulePrefix}999")
                    .sourceScopePattern("**")
                    .targetScopePattern("${productScope}")
                    .resourcePattern("**")
                    .actions("*")
                    .requiresAudit(true)
                    .effect(BoundaryPolicyRule.Effect.DENY)
                    .metadata(Map.of(
                            "packVersion", "0.1.0",
                            "ruleCategory", "default-deny",
                            "denialReason", "no-matching-allow-rule"))
                    .build()
    );

    @Override
    public List<BoundaryPolicyRule> loadRules(BoundaryPolicyLoadContext context) {
        if (!"default".equals(context.getTenantId()) || !"GLOBAL".equalsIgnoreCase(context.getRegion())) {
            throw new BoundaryPolicyStoreException(
                    "${name} boundary policy overrides are unsupported. "
                            + "Only tenantId=default and region=GLOBAL are allowed; failing closed for "
                            + context);
        }
        return ProductBoundaryPolicyPackValidator.validate(
                RULES,
                VALIDATION_PROFILE,
                ACTION_REGISTRY,
                RESOURCE_REGISTRY);
    }
}
`
);

writeFile(
  join(productDir, "src", "main", "java", "com", "ghatana", packageSegment, "kernel", "policy", `${classPrefix}ComplianceRulePack.java`),
  `package com.ghatana.${packageSegment}.kernel.policy;

import com.ghatana.plugin.compliance.CompliancePlugin;

import java.util.List;

public final class ${classPrefix}ComplianceRulePack {

    public static final String ${productCode}_CORE_GOVERNANCE = "${productCode}_CORE_GOVERNANCE";

    private ${classPrefix}ComplianceRulePack() {
    }

    public static List<CompliancePlugin.ComplianceRule> coreGovernanceRules() {
        return List.of(
                new CompliancePlugin.ComplianceRule(
                        "${productCode}-CR-001",
                        ${productCode}_CORE_GOVERNANCE,
                        "${name} operations must emit auditable events.",
                        CompliancePlugin.ComplianceRule.Severity.HIGH,
                        "$.auditEventEmitted == true"
                )
        );
    }
}
`
);

writeFile(
  join(productDir, "src", "main", "java", "com", "ghatana", packageSegment, "kernel", "policy", `${classPrefix}PluginBindings.java`),
  `package com.ghatana.${packageSegment}.kernel.policy;

import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;

public final class ${classPrefix}PluginBindings {

    private final CompliancePlugin compliancePlugin;

    public ${classPrefix}PluginBindings(CompliancePlugin compliancePlugin) {
        this.compliancePlugin = compliancePlugin;
    }

    public Promise<Void> registerAll() {
        return compliancePlugin.registerRuleSet(
                ${classPrefix}ComplianceRulePack.${productCode}_CORE_GOVERNANCE,
                ${classPrefix}ComplianceRulePack.coreGovernanceRules());
    }
}
`
);

writeFile(
  join(productDir, "src", "test", "java", "com", "ghatana", packageSegment, "kernel", `${classPrefix}PackContractTest.java`),
  `package com.ghatana.${packageSegment}.kernel;

import com.ghatana.${packageSegment}.kernel.policy.${classPrefix}BoundaryPolicyStore;
import com.ghatana.kernel.policy.BoundaryPolicyLoadContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ${classPrefix}PackContractTest {

    @Test
    void shouldLoadNonEmptyBoundaryPolicyRules() {
        assertThat(new ${classPrefix}BoundaryPolicyStore().loadRules(BoundaryPolicyLoadContext.global()))
                .isNotEmpty();
    }
}
`
);

const docFiles = [
  ["00-VISION.md", `# ${name} Vision\n\n## Domain Boundary\n\n${name} owns ${domain}-specific business logic on top of Kernel-owned platform capabilities.\n`],
  ["01-ARCHITECTURE.md", `# ${name} Architecture\n\n## Kernel Dependencies\n\n- boundary-policy-evaluation\n- audit-trail\n- tenant-context\n`],
  ["02-API_CONTRACTS.md", `# ${name} API Contracts\n\nDocument product-owned APIs, bridge contracts, and route/content entitlement surfaces here.\n`],
  ["03-UX_WORKFLOWS.md", `# ${name} UX Workflows\n\nDocument persona-aware navigation, critical flows, and permission-denied behavior here.\n`],
  ["04-TESTING.md", `# ${name} Testing\n\nTrack pack contract tests, integration tests, UI coverage, and conformance gates here.\n`],
  ["05-OPERATIONS.md", `# ${name} Operations\n\nDocument runtime services, local ports, secrets, observability overrides, and incident procedures here.\n`],
  ["06-IMPLEMENTATION_PLAN.md", `# ${name} Implementation Plan\n\nUse this file to track remaining product rollout work after scaffolding.\n`],
];

for (const [filename, content] of docFiles) {
  writeFile(join(productDir, "docs", filename), content);
}

// Lifecycle configuration files
writeFile(
  join(productDir, "kernel-product.yaml"),
  `lifecycleProfile: standard-web-api-product
surfaces:
  backend-api:
    adapter: gradle-java-service
    module: :products:${id}
${uiEnabled ? `  web:
    adapter: pnpm-vite-react
    module: products/${id}/client/web` : ""}
phases:
  dev:
    mode: parallel
  build:
    mode: sequential
`
);

writeFile(
  join(productDir, "lifecycle.local.yaml"),
  `environment: local
surfaces:
  backend-api:
    port: 8080
    env:
      PRODUCT_ID: ${id}
${uiEnabled ? `  web:
    port: 3000
    env:
      API_URL: http://localhost:8080` : ""}
deployment:
  adapter: compose-local
  composeFile: deploy/local.compose.yaml
`
);

writeFile(
  join(productDir, "lifecycle.dev.yaml"),
  `environment: dev
surfaces:
  backend-api:
    port: 8080
    env:
      PRODUCT_ID: ${id}
${uiEnabled ? `  web:
    port: 3000
    env:
      API_URL: http://localhost:8080` : ""}
deployment:
  adapter: kubernetes
  namespace: dev
  configPath: deploy/k8s/dev
`
);

writeFile(
  join(productDir, "runtime", "runtime-profile.yaml"),
  `surfaces:
  backend-api:
    jvmOptions:
      -Xmx1g
      -Xms512m
    env:
      LOG_LEVEL: info
${uiEnabled ? `  web:
    env:
      NODE_ENV: production` : ""}
`
);

writeFile(
  join(productDir, "deploy", "local.compose.yaml"),
  `# Shared runtime template reference for ${name}
x-ghatana-template:
  source: ../../../config/docker/templates/product-runtime.compose.yaml

services:
  ${id}-app:
    image: ${id}:local
    environment:
      PRODUCT_ID: ${id}
      PRODUCT_AI_DISABLED: "\${${productCode}_AI_DISABLED:-true}"
    ports:
      - "\${${productCode}_APP_PORT:-3900}:8080"
`
);

writeFile(
  join(productDir, "deploy", "local.env.example"),
  `${productCode}_AI_DISABLED=true
${productCode}_APP_PORT=3900
`
);

writeFile(
  join(productDir, "deploy", "health-checks.json"),
  `{
  "surfaces": {
    "backend-api": {
      "type": "http",
      "path": "/health",
      "port": 8080
${uiEnabled ? `    },
    "web": {
      "type": "http",
      "path": "/",
      "port": 3000` : ""}
    }
  }
}
`
);

writeFile(
  join(productDir, "conformance", "lifecycle-fixtures.json"),
  `{
  "expectedSteps": {
    "dev": ["start-dev-server"],
    "build": ["compile", "test", "package"]
  }
}
`
);

writeFile(
  join(productDir, "conformance", "deployment-fixtures.json"),
  `{
  "expectedSteps": {
    "deploy": ["apply-deployment"]
  }
}
`
);

writeFile(
  join(productDir, "conformance", "artifact-fixtures.json"),
  `{
  "expectedArtifacts": {
    "backend-api": ["jar"]${uiEnabled ? `,
    "web": ["static-web-bundle"]` : ""}
  }
}
`
);

writeFile(
  join(productDir, "docker-compose.local.yml"),
  `# Shared runtime template reference for ${name}
x-ghatana-template:
  source: ../../../config/docker/templates/product-runtime.compose.yaml

services:
  ${id}-app:
    image: ${id}:local
    environment:
      PRODUCT_ID: ${id}
      PRODUCT_AI_DISABLED: "\${${productCode}_AI_DISABLED:-true}"
    ports:
      - "\${${productCode}_APP_PORT:-3900}:8080"
`
);

if (uiEnabled) {
  ensureDir(join(productDir, "client", "web", "e2e"));

  writeFile(
    join(productDir, "client", "web", "package.json"),
    JSON.stringify(
      {
        name: `@ghatana/${id}-web`,
        private: true,
        version: "0.1.0",
        type: "module",
        packageManager: "pnpm@10.33.0",
        scripts: {
          dev: "vite",
          lint: "pnpm exec eslint src --ext .ts,.tsx",
          "type-check": "tsc --noEmit",
          test: "vitest run",
          "test:coverage": "vitest run --coverage",
          "test:e2e": "playwright test --list",
          "test:e2e:a11y": "playwright test --grep @a11y --list",
          build: "tsc --noEmit && vite build"
        },
        devDependencies: {
          "@playwright/test": "^1.59.1",
          "@testing-library/jest-dom": "^6.9.1",
          "@testing-library/react": "^16.3.2",
          "@types/node": "^25.6.0",
          "@types/react": "^19.2.14",
          "@types/react-dom": "^19.2.3",
          "@vitejs/plugin-react": "^6.0.1",
          eslint: "^9.39.2",
          jsdom: "^29.0.2",
          typescript: "^6.0.2",
          vite: "^8.0.8",
          vitest: "^4.1.4"
        },
        dependencies: {
          "react": "^19.2.5",
          "react-dom": "^19.2.5",
          "@ghatana/product-shell": "workspace:*",
          "react-router-dom": "^7.14.0",
          "scheduler": "^0.27.0"
        }
      },
      null,
      2
    ) + "\n"
  );

  writeFile(
    join(productDir, "client", "web", "src", "routeManifest.tsx"),
    `import type { ProductRouteCapability } from "@ghatana/product-shell";

export const routeManifest: readonly ProductRouteCapability[] = [
  {
    path: "/",
    label: "${name}",
    description: "${name} overview workspace",
    group: "Core",
    minimumRole: "admin",
    personas: ["operator"],
    tiers: ["standard"],
    actions: ["view"],
    cards: ["overview"],
  },
];
`
  );

  writeFile(
    join(productDir, "client", "web", "src", "App.tsx"),
    `import React from "react";
    import { ProductShell, type ProductShellConfig } from "@ghatana/product-shell";
    import { routeManifest } from "./routeManifest";

const shellConfig: ProductShellConfig = {
  productName: "${name}",
  routes: routeManifest,
  currentRole: "admin",
  roleOrder: {
    viewer: 0,
    operator: 1,
    admin: 2,
  },
  availableRoles: ["viewer", "operator", "admin"],
  roleLabels: {
    viewer: "Viewer",
    operator: "Operator",
    admin: "Admin",
  },
  roleSelectorDisclosureNote:
    "This shell reflects route disclosure and must still be backed by server-side authorization.",
};

export function App() {
  return (
    <ProductShell config={shellConfig} mainContentId="${id}-main-content" mainContentTabIndex={-1}>
      <section aria-labelledby="${id}-overview-title">
        <h1 id="${id}-overview-title">${name}</h1>
        <p>Replace this scaffold with product-owned domain workflows.</p>
      </section>
    </ProductShell>
  );
}
`
  );

  writeFile(
    join(productDir, "client", "web", "src", "main.tsx"),
    `import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
`
  );

  writeFile(
    join(productDir, "client", "web", "src", "App.test.tsx"),
    `import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { App } from "./App";

describe("App", () => {
  it("renders the product shell with correct configuration", () => {
    render(<App />);
    expect(screen.getByRole("heading", { name: "${name}" })).toBeTruthy();
    expect(screen.getByText("Replace this scaffold with product-owned domain workflows.")).toBeTruthy();
  });
});
`
  );

  writeFile(
    join(productDir, "client", "web", "tsconfig.json"),
    `{
  "extends": "../../../../tsconfig.base.json",
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "moduleResolution": "bundler",
    "jsx": "react-jsx",
    "isolatedModules": true,
    "allowSyntheticDefaultImports": true,
    "esModuleInterop": true,
    "resolveJsonModule": true,
    "noEmit": true,
    "ignoreDeprecations": "6.0",
    "baseUrl": ".",
    "types": ["vite/client", "node", "react", "react-dom", "vitest/globals"],
    "paths": {
      "@/*": ["src/*"],
      "@ghatana/product-shell": ["../../../../platform/typescript/product-shell/src/index.ts"]
    }
  },
  "include": ["src", "vite.config.ts", "vitest.config.ts", "playwright.config.ts"],
  "exclude": ["dist", "node_modules"]
}
`
  );

  writeFile(
    join(productDir, "client", "web", "vite.config.ts"),
    `import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const reactPath = path.dirname(require.resolve("react/package.json"));
const reactDomPath = path.dirname(require.resolve("react-dom/package.json"));
const schedulerPath = path.dirname(require.resolve("scheduler/package.json"));
const reactRouterDomPath = path.dirname(require.resolve("react-router-dom/package.json"));
const reactRouterPath = path.dirname(
  require.resolve("react-router/package.json", { paths: [reactRouterDomPath] })
);
const reactRouterEntryPath = path.join(reactRouterPath, "dist/development/index.mjs");
const reactRouterDomEntryPath = path.join(reactRouterPath, "dist/development/dom-export.mjs");

export default defineConfig({
  plugins: [react()],
  resolve: {
    preserveSymlinks: true,
    dedupe: ["react", "react-dom", "react-router", "react-router-dom", "scheduler"],
    alias: [
      { find: "@", replacement: path.resolve(__dirname, "./src") },
      { find: "@ghatana/product-shell", replacement: path.resolve(__dirname, "../../../../platform/typescript/product-shell/src/index.ts") },
      { find: /^react$/, replacement: reactPath },
      { find: /^react-dom$/, replacement: reactDomPath },
      { find: /^scheduler$/, replacement: schedulerPath },
      { find: /^react-router\\/dom$/, replacement: reactRouterDomEntryPath },
      { find: /^react-router$/, replacement: reactRouterEntryPath },
      { find: /^react-router-dom$/, replacement: reactRouterDomPath }
    ]
  }
});
`
  );

  writeFile(
    join(productDir, "client", "web", "vitest.config.ts"),
    `import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const reactPath = path.dirname(require.resolve("react/package.json"));
const reactDomPath = path.dirname(require.resolve("react-dom/package.json"));
const schedulerPath = path.dirname(require.resolve("scheduler/package.json"));
const reactRouterDomPath = path.dirname(require.resolve("react-router-dom/package.json"));
const reactRouterPath = path.dirname(
  require.resolve("react-router/package.json", { paths: [reactRouterDomPath] })
);
const reactRouterEntryPath = path.join(reactRouterPath, "dist/development/index.mjs");
const reactRouterDomEntryPath = path.join(reactRouterPath, "dist/development/dom-export.mjs");

export default defineConfig({
  plugins: [react()],
  resolve: {
    preserveSymlinks: true,
    dedupe: ["react", "react-dom", "react-router", "react-router-dom", "scheduler"],
    alias: [
      { find: "@", replacement: path.resolve(__dirname, "./src") },
      { find: "@ghatana/product-shell", replacement: path.resolve(__dirname, "../../../../platform/typescript/product-shell/src/index.ts") },
      { find: /^react$/, replacement: reactPath },
      { find: /^react-dom$/, replacement: reactDomPath },
      { find: /^scheduler$/, replacement: schedulerPath },
      { find: /^react-router\\/dom$/, replacement: reactRouterDomEntryPath },
      { find: /^react-router$/, replacement: reactRouterEntryPath },
      { find: /^react-router-dom$/, replacement: reactRouterDomPath }
    ]
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./vitest.setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
`
  );

  writeFile(
    join(productDir, "client", "web", "vitest.setup.ts"),
    `import "@testing-library/jest-dom";`
  );

  writeFile(
    join(productDir, "client", "web", "playwright.config.ts"),
    `import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
});
`
  );

  writeFile(
    join(productDir, "client", "web", "e2e", "visual-regression.spec.ts"),
    `import { test } from "@playwright/test";

test("@visual scaffolded product shell baseline", async () => {
  test.skip();
});
`
  );

  writeFile(
    join(productDir, "client", "web", "e2e", "a11y.spec.ts"),
    `import { test } from "@playwright/test";

test("@a11y scaffolded product shell accessibility baseline", async () => {
  test.skip();
});
`
  );

  writeFile(
    join(productDir, "client", "web", "index.html"),
    `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>${name}</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
`
  );
}

writeFile(
  join(productDir, "README.md"),
  `# ${name}

This product was scaffolded with \`scripts/scaffold-product.mjs\`.

## What was generated

- Gradle product module skeleton and pack-validation wiring
- domain-pack manifest with Kernel ownership fields
- boundary policy store, compliance rule pack, plugin bindings, and pack contract test
- canonical product docs taxonomy
- local runtime compose override
- product-specific conformance fixtures and check script
${uiEnabled ? "- web UI shell using @ghatana/product-shell and route manifest\n" : ""}\

## Still required

1. Replace the placeholder rule/resource/action vocabulary with product-owned semantics.
2. Replace the placeholder UI/build/test commands with product-ready implementations.
3. Add any product-specific UI workflow or runtime overrides beyond the shared bootstrap.
`
);

// Generate product-specific conformance check script
ensureDir(join(productDir, "scripts"));
writeFile(
  join(productDir, "scripts", `check-${id}-conformance.mjs`),
  `#!/usr/bin/env node

/**
 * ${name} Product Conformance Check
 * 
 * Validates ${name} product conformance against Kernel requirements.
 * This script is auto-generated by the product scaffolder.
 * 
 * Usage: node scripts/check-${id}-conformance.mjs
 */

import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const productRoot = path.resolve(__dirname, '..');

function checkFileExists(relativePath, description) {
  const fullPath = path.join(productRoot, relativePath);
  if (!existsSync(fullPath)) {
    console.error(\`✗ Missing \${description}: \${relativePath}\`);
    return false;
  }
  console.log(\`✓ \${description} exists\`);
  return true;
}

function loadJson(relativePath, description) {
  const fullPath = path.join(productRoot, relativePath);
  try {
    return JSON.parse(readFileSync(fullPath, 'utf8'));
  } catch (error) {
    console.error(\`✗ Invalid \${description}: \${relativePath} (\${error.message})\`);
    return null;
  }
}

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function checkDataAccessFixtures() {
  const fixtures = loadJson('conformance/data-access-context.json', 'data-access context fixtures');
  if (!Array.isArray(fixtures) || fixtures.length === 0) {
    console.error('✗ data-access context fixtures must be a non-empty array');
    return false;
  }
  const required = ['tenantId', 'principalId', 'correlationId', 'auditClassification', 'dataOwnerScope', 'idempotencyKey'];
  const valid = fixtures.every((fixture, index) => {
    const missing = required.filter((field) => !isNonEmptyString(fixture?.[field]));
    if (missing.length > 0) {
      console.error(\`✗ data-access fixture[\${index}] missing \${missing.join(', ')}\`);
      return false;
    }
    return true;
  });
  if (valid) {
    console.log('✓ data-access context fixtures are valid');
  }
  return valid;
}

function checkRouteEntitlementFixtures() {
  const fixtures = loadJson('conformance/route-entitlements.json', 'route entitlement fixtures');
  if (!Array.isArray(fixtures) || fixtures.length === 0) {
    console.error('✗ route entitlement fixtures must be a non-empty array');
    return false;
  }
  const valid = fixtures.every((fixture, index) => {
    const hasIdentity = fixture?.product === '${id}' &&
      isNonEmptyString(fixture?.tenantId) &&
      isNonEmptyString(fixture?.principalId) &&
      isNonEmptyString(fixture?.role);
    const hasRoutes = Array.isArray(fixture?.routes) && fixture.routes.length > 0;
    if (!hasIdentity || !hasRoutes) {
      console.error(\`✗ route entitlement fixture[\${index}] must declare product identity and at least one route\`);
      return false;
    }
    return true;
  });
  if (valid) {
    console.log('✓ route entitlement fixtures are valid');
  }
  return valid;
}

function checkIdempotencyFixtures() {
  const fixtures = loadJson('conformance/idempotency-observations.json', 'idempotency observation fixtures');
  if (!Array.isArray(fixtures) || fixtures.length === 0) {
    console.error('✗ idempotency observation fixtures must be a non-empty array');
    return false;
  }
  const statuses = new Set(['miss', 'completed', 'expired', 'conflict']);
  const valid = fixtures.every((fixture, index) => {
    const required = ['operation', 'key', 'fingerprint', 'principalId', 'tenantId'];
    const missing = required.filter((field) => !isNonEmptyString(fixture?.[field]));
    if (missing.length > 0 || !statuses.has(fixture?.status)) {
      console.error(\`✗ idempotency observation[\${index}] has invalid shape\`);
      return false;
    }
    if (fixture.status === 'completed' && fixture.replayed !== true) {
      console.error(\`✗ idempotency observation[\${index}] completed replay must set replayed=true\`);
      return false;
    }
    return true;
  });
  if (valid) {
    console.log('✓ idempotency observation fixtures are valid');
  }
  return valid;
}

function checkObservabilityFlowFixture() {
  const manifest = loadJson('conformance/observability-flow.json', 'observability flow fixture');
  if (!manifest || manifest.schemaVersion !== '1.0.0' || !Array.isArray(manifest.flows)) {
    console.error('✗ observability flow fixture must declare schemaVersion and flows');
    return false;
  }
  const productFlow = manifest.flows.find((flow) => flow?.product === '${id}');
  if (!productFlow || !Array.isArray(productFlow.facets) || !Array.isArray(productFlow.evidence)) {
    console.error('✗ observability flow fixture must include a ${id} flow with facets and evidence');
    return false;
  }
  console.log('✓ observability flow fixture is valid');
  return true;
}

function main() {
  console.log(\`=== ${name} Product Conformance Check ===\\n\`);
  
  let allPassed = true;
  
  // Check required files
  allPassed &= checkFileExists('build.gradle.kts', 'Gradle build file');
  allPassed &= checkFileExists('domain-pack-manifest.yaml', 'Domain pack manifest');
  allPassed &= checkFileExists('policy-packs/${id}-boundary-policy.yaml', 'Boundary policy pack');
  allPassed &= checkFileExists('policy-packs/${id}-compliance-rule-pack.yaml', 'Compliance rule pack');
  allPassed &= checkFileExists('conformance/data-access-context.json', 'Data-access conformance fixture');
  allPassed &= checkFileExists('conformance/route-entitlements.json', 'Route entitlement conformance fixture');
  allPassed &= checkFileExists('conformance/idempotency-observations.json', 'Idempotency conformance fixture');
  allPassed &= checkFileExists('conformance/observability-flow.json', 'Observability flow conformance fixture');
  allPassed &= checkFileExists('src/main/java/com/ghatana/${packageSegment}/kernel/policy/${classPrefix}BoundaryPolicyStore.java', 'Boundary policy store');
  allPassed &= checkFileExists('src/main/java/com/ghatana/${packageSegment}/kernel/policy/${classPrefix}ComplianceRulePack.java', 'Compliance rule pack');
  allPassed &= checkFileExists('src/main/java/com/ghatana/${packageSegment}/kernel/policy/${classPrefix}PluginBindings.java', 'Plugin bindings');
  allPassed &= checkFileExists('src/test/java/com/ghatana/${packageSegment}/kernel/${classPrefix}PackContractTest.java', 'Pack contract test');
  
  // Check documentation
  allPassed &= checkFileExists('docs/00-VISION.md', 'Vision document');
  allPassed &= checkFileExists('docs/01-ARCHITECTURE.md', 'Architecture document');
  allPassed &= checkFileExists('docs/06-IMPLEMENTATION_PLAN.md', 'Implementation plan');
  
  ${uiEnabled ? `// Check UI conformance if enabled
  allPassed &= checkFileExists('client/web/package.json', 'UI package.json');
  allPassed &= checkFileExists('client/web/src/routeManifest.tsx', 'Route manifest');
  allPassed &= checkFileExists('client/web/src/App.tsx', 'App component');
  ` : ''}

  allPassed &= checkDataAccessFixtures();
  allPassed &= checkRouteEntitlementFixtures();
  allPassed &= checkIdempotencyFixtures();
  allPassed &= checkObservabilityFlowFixture();
  
  console.log();
  if (allPassed) {
    console.log(\`✓ ${name} product conformance check passed\`);
    process.exit(0);
  } else {
    console.log(\`✗ ${name} product conformance check failed\`);
    process.exit(1);
  }
}

main();
`
);

if (shouldRegisterProductShape) {
  registerProductShape({ root, id, uiEnabled, uiMode });
}

if (shouldRegisterCanonicalRegistry) {
  registerCanonicalRegistry({ root, id, name, domain, uiEnabled });
}

if (shouldRegisterWorkspace) {
  registerWorkspace({ root, id, name, uiEnabled });
}

if (shouldRegisterGradleSettings) {
  registerGradleSettings({ root, id, name });
}

if (shouldRegisterCiMatrices) {
  registerCiMatrices({ root, id, name, uiEnabled });
}

console.log(`Scaffolded ${name} at ${productDir}`);
console.log("");
console.log("Next steps:");
console.log(`- Review ${join("products", id, "domain-pack-manifest.yaml")}`);
if (!shouldRegisterProductShape) {
  console.log(`- Register product shape metadata in config/product-shape.json if needed`);
}
if (!shouldRegisterCanonicalRegistry) {
  console.log(`- Register canonical product metadata in config/canonical-product-registry.json if needed`);
}
if (uiEnabled && !shouldRegisterWorkspace) {
  console.log(`- Register UI packages in pnpm-workspace.yaml if needed`);
}
if (!shouldRegisterGradleSettings) {
  console.log(`- Register Gradle modules in settings.gradle.kts if needed`);
}
if (!shouldRegisterCiMatrices) {
  console.log(`- Add CI matrix entries for coverage, API contracts, and UI flows`);
}
