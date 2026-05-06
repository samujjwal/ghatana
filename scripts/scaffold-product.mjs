#!/usr/bin/env node

import { mkdirSync, writeFileSync, existsSync } from "node:fs";
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

const args = parseArgs(process.argv.slice(2));

if (args.has("help")) {
  console.log(`Usage:
  node scripts/scaffold-product.mjs \\
    --id sample-product \\
    --name "Sample Product" \\
    --product-code SAMPLE \\
    --domain sample-domain \\
    --ui web

Notes:
- Generates a Kernel-aligned product skeleton under products/<id>.
- Does not auto-register the product in settings.gradle.kts, pnpm-workspace.yaml, or CI matrices yet.
- Intended as the foundation for Task 48, not the final fully automated scaffolder.`);
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

if (existsSync(productDir)) {
  throw new Error(`Product directory already exists: ${productDir}`);
}

const dirs = [
  productDir,
  join(productDir, "docs"),
  join(productDir, "policy-packs"),
  join(productDir, "src", "main", "java", "com", "ghatana", packageSegment, "kernel", "policy"),
  join(productDir, "src", "test", "java", "com", "ghatana", packageSegment, "kernel"),
];

if (uiEnabled) {
  dirs.push(
    join(productDir, "client", "web", "src"),
    join(productDir, "client", "web", "src", "routes")
  );
}

dirs.forEach(ensureDir);

writeFile(
  join(productDir, "domain-pack-manifest.yaml"),
  `pack:
  id: ${id}
  version: 0.1.0
  product: ${id}
  domain: ${domain}
  rulePrefix: ${rulePrefix}
  boundaryPolicyStoreClass: com.ghatana.${packageSegment}.kernel.policy.${classPrefix}BoundaryPolicyStore
  pluginBindingsClass: com.ghatana.${packageSegment}.kernel.policy.${classPrefix}PluginBindings
  defaultDenyRuleId: ${rulePrefix}999
  kernelCapabilitiesConsumed:
    - boundary-policy-evaluation
    - audit-trail
    - tenant-context
  policyActions:
    - read
    - write
    - delete
  policyResources:
    - core
  pluginsConsumed:
    - plugin-audit-trail
    - plugin-compliance
  bridgesConsumed: []
  domainPacksProvided:
    - ${id}-boundary-policy
    - ${id}-compliance-rule-pack
  uiSurfaces:
${uiEnabled ? "    - web" : "    []"}
  runtimeServices:
    - launcher
  dataSensitivity: internal
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
            BoundaryPolicyResourceRegistry.ofDeclaredResources(Set.of("core"));
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
  writeFile(
    join(productDir, "client", "web", "package.json"),
    JSON.stringify(
      {
        name: `@ghatana/${id}-web`,
        private: true,
        version: "0.1.0",
        packageManager: "pnpm@10.33.0",
        scripts: {
          lint: "echo \"TODO: wire lint\"",
          "type-check": "echo \"TODO: wire type-check\"",
          test: "echo \"TODO: wire tests\"",
          "test:coverage": "echo \"TODO: wire coverage\"",
          "test:e2e": "echo \"TODO: wire e2e\"",
          "test:e2e:a11y": "echo \"TODO: wire a11y e2e\"",
          build: "echo \"TODO: wire build\""
        },
        dependencies: {
          "@ghatana/product-shell": "workspace:*",
          "react-router-dom": "^7.0.0"
        }
      },
      null,
      2
    ) + "\n"
  );

  writeFile(
    join(productDir, "client", "web", "src", "routeManifest.tsx"),
    `export const routeManifest = [
  {
    id: "${id}.home",
    path: "/",
    label: "${name}",
    roles: ["admin"],
    persona: "operator",
    tier: "standard",
    actions: ["view"],
    cards: ["overview"],
  },
];
`
  );

  writeFile(
    join(productDir, "client", "web", "src", "App.tsx"),
    `export function App() {
  return <div>${name} product shell placeholder</div>;
}
`
  );
}

writeFile(
  join(productDir, "README.md"),
  `# ${name}

This product was scaffolded with \`scripts/scaffold-product.mjs\`.

## What was generated

- domain-pack manifest with Kernel ownership fields
- boundary policy store skeleton and pack contract test
- canonical product docs taxonomy
- local runtime compose override
${uiEnabled ? "- web UI shell placeholder and route manifest\n" : ""}\

## Still required

1. Register the product in \`settings.gradle.kts\` if it has Gradle modules.
2. Register any UI/client packages in \`pnpm-workspace.yaml\`.
3. Add CI matrix entries if the product exposes APIs or UI surfaces.
4. Replace the placeholder rule/resource/action vocabulary with product-owned semantics.
`
);

console.log(`Scaffolded ${name} at ${productDir}`);
console.log("");
console.log("Next steps:");
console.log(`- Review ${join("products", id, "domain-pack-manifest.yaml")}`);
console.log(`- Register Gradle modules in settings.gradle.kts if needed`);
console.log(`- Register UI packages in pnpm-workspace.yaml if needed`);
console.log(`- Add CI matrix entries for coverage, API contracts, and UI flows`);
