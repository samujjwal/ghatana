#!/usr/bin/env node

/**
 * Observability Flow Conformance Check
 *
 * Validates observability flows defined in product-observability-flows.json.
 * This replaces hardcoded product-specific checks with a manifest-driven approach.
 * Flows are generated from product manifests in the canonical registry.
 *
 * Usage: node scripts/check-observability-conformance.mjs
 */

import { existsSync, readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const flowManifestFile = "config/observability/product-observability-flows.json";

const violations = [];

const forbiddenProductStackFiles = [
  "products/flashit/monitoring/prometheus.yml",
  "products/flashit/monitoring/grafana/provisioning/datasources/prometheus.yml",
  "products/flashit/monitoring/grafana/provisioning/dashboards/dashboards.yml",
];

function validateFlowManifest() {
  const manifestPath = path.join(repoRoot, flowManifestFile);
  if (!existsSync(manifestPath)) {
    violations.push(`Missing observability flow manifest ${flowManifestFile}`);
    return;
  }

  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const requiredFacets = manifest.requiredFacets ?? [];
  const flows = manifest.flows ?? [];
  const requiredProducts = ["phr", "finance", "digital-marketing", "flashit"];
  const coveredProducts = new Set();
  let bridgeFlowCount = 0;

  if (!Array.isArray(requiredFacets) || requiredFacets.length === 0) {
    violations.push(`${flowManifestFile}: requiredFacets must list observability facets`);
  }

  if (!Array.isArray(flows) || flows.length === 0) {
    violations.push(`${flowManifestFile}: flows must be a non-empty array`);
    return;
  }

  for (const flow of flows) {
    const flowName = `${flow.product ?? "unknown"}:${flow.flow ?? "unknown"}`;
    if (!flow.product || !flow.flow || !flow.kind) {
      violations.push(`${flowManifestFile}: ${flowName} must declare product, flow, and kind`);
      continue;
    }

    coveredProducts.add(flow.product);
    if (flow.kind === "bridge") {
      bridgeFlowCount += 1;
    }

    const facets = new Set(flow.facets ?? []);
    const missingFacets = requiredFacets.filter((facet) => !facets.has(facet));
    if (missingFacets.length > 0) {
      violations.push(`${flowManifestFile}: ${flowName} is missing facets ${missingFacets.join(", ")}`);
    }

    if (!Array.isArray(flow.evidence) || flow.evidence.length === 0) {
      violations.push(`${flowManifestFile}: ${flowName} must declare evidence files`);
      continue;
    }

    for (const evidence of flow.evidence) {
      const evidenceFile = evidence.file;
      const evidencePath = path.join(repoRoot, evidenceFile ?? "");
      if (!evidenceFile || !existsSync(evidencePath)) {
        violations.push(`${flowManifestFile}: ${flowName} references missing evidence file ${evidenceFile}`);
        continue;
      }

      const content = readFileSync(evidencePath, "utf8");
      const missingTokens = (evidence.tokens ?? []).filter((token) => !content.includes(token));
      if (missingTokens.length > 0) {
        violations.push(
          `${flowManifestFile}: ${flowName} evidence ${evidenceFile} missing tokens ${missingTokens.join(", ")}`,
        );
      }
    }
  }

  for (const product of requiredProducts) {
    if (!coveredProducts.has(product)) {
      violations.push(`${flowManifestFile}: missing observability flow coverage for ${product}`);
    }
  }

  if (bridgeFlowCount === 0) {
    violations.push(`${flowManifestFile}: at least one bridge flow must be covered`);
  }
}

for (const contract of contracts) {
  const filePath = path.join(repoRoot, contract.file);
  if (!existsSync(filePath)) {
    violations.push(`${contract.name}: missing file ${contract.file}`);
    continue;
  }

  const content = readFileSync(filePath, "utf8");
  const missing = contract.required.filter((token) => !content.includes(token));
  if (missing.length > 0) {
    violations.push(
      `${contract.name}: missing observability evidence ${missing.join(", ")} in ${contract.file}`,
    );
  }
}

for (const file of forbiddenProductStackFiles) {
  if (existsSync(path.join(repoRoot, file))) {
    violations.push(
      `FlashIt observability must not own stack config file ${file}; keep only product dashboards and alert overlays under products/flashit/monitoring`,
    );
  }
}

validateFlowManifest();

if (violations.length > 0) {
  console.error(`❌ Observability conformance check failed with ${violations.length} violation(s):\n`);
  for (const violation of violations) {
    console.error(`- ${violation}`);
  }
  process.exit(1);
}

console.log("✅ Observability conformance check passed.");
