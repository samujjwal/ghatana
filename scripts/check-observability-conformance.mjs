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
const flowSchemaFile = "config/observability/product-observability-flows.schema.json";
const registryFile = "config/canonical-product-registry.json";

const violations = [];

const forbiddenProductStackFiles = [
  "products/flashit/monitoring/prometheus.yml",
  "products/flashit/monitoring/grafana/provisioning/datasources/prometheus.yml",
  "products/flashit/monitoring/grafana/provisioning/dashboards/dashboards.yml",
];

function loadJson(relativePath) {
  return JSON.parse(readFileSync(path.join(repoRoot, relativePath), "utf8"));
}

function activeObservabilityProducts() {
  const registry = loadJson(registryFile).registry;
  return Object.values(registry)
    .filter((entry) =>
      entry.kind === "business-product" &&
      entry.metadata?.status === "active" &&
      entry.conformance?.observability === true &&
      entry.conformance?.manifest === true,
    )
    .map((entry) => entry.id)
    .sort();
}

function isNonEmptyString(value) {
  return typeof value === "string" && value.length > 0;
}

function validateStringArray({ owner, field, value, allowEmpty = false }) {
  if (!Array.isArray(value)) {
    violations.push(`${flowManifestFile}: ${owner}.${field} must be an array`);
    return [];
  }
  if (!allowEmpty && value.length === 0) {
    violations.push(`${flowManifestFile}: ${owner}.${field} must not be empty`);
  }
  const seen = new Set();
  for (const item of value) {
    if (!isNonEmptyString(item)) {
      violations.push(`${flowManifestFile}: ${owner}.${field} must contain only non-empty strings`);
      continue;
    }
    if (seen.has(item)) {
      violations.push(`${flowManifestFile}: ${owner}.${field} contains duplicate value ${item}`);
    }
    seen.add(item);
  }
  return value;
}

function validateFlowManifestSchema(manifest, schema) {
  const allowedRootKeys = new Set(Object.keys(schema.properties ?? {}));
  for (const key of Object.keys(manifest)) {
    if (!allowedRootKeys.has(key)) {
      violations.push(`${flowManifestFile}: unrecognized root key ${key}`);
    }
  }

  if (manifest.schemaVersion !== schema.properties?.schemaVersion?.const) {
    violations.push(
      `${flowManifestFile}: schemaVersion must be ${schema.properties?.schemaVersion?.const}`,
    );
  }

  validateStringArray({ owner: "<root>", field: "requiredFacets", value: manifest.requiredFacets });

  if (!Array.isArray(manifest.flows) || manifest.flows.length === 0) {
    violations.push(`${flowManifestFile}: flows must be a non-empty array`);
    return;
  }

  const allowedFlowKeys = new Set(Object.keys(schema.$defs?.flow?.properties ?? {}));
  const allowedKinds = new Set(schema.$defs?.flow?.properties?.kind?.enum ?? []);
  const slugPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

  for (const [index, flow] of manifest.flows.entries()) {
    const owner = `flows[${index}]`;
    if (!flow || typeof flow !== "object" || Array.isArray(flow)) {
      violations.push(`${flowManifestFile}: ${owner} must be an object`);
      continue;
    }
    for (const key of Object.keys(flow)) {
      if (!allowedFlowKeys.has(key)) {
        violations.push(`${flowManifestFile}: ${owner} has unrecognized key ${key}`);
      }
    }
    if (!isNonEmptyString(flow.product) || !slugPattern.test(flow.product)) {
      violations.push(`${flowManifestFile}: ${owner}.product must be a product id`);
    }
    if (!isNonEmptyString(flow.flow) || !slugPattern.test(flow.flow)) {
      violations.push(`${flowManifestFile}: ${owner}.flow must be a slug`);
    }
    if (!allowedKinds.has(flow.kind)) {
      violations.push(`${flowManifestFile}: ${owner}.kind must be one of ${[...allowedKinds].join(", ")}`);
    }
    validateStringArray({ owner, field: "facets", value: flow.facets });

    if (!Array.isArray(flow.evidence) || flow.evidence.length === 0) {
      violations.push(`${flowManifestFile}: ${owner}.evidence must be a non-empty array`);
      continue;
    }
    for (const [evidenceIndex, evidence] of flow.evidence.entries()) {
      const evidenceOwner = `${owner}.evidence[${evidenceIndex}]`;
      if (!evidence || typeof evidence !== "object" || Array.isArray(evidence)) {
        violations.push(`${flowManifestFile}: ${evidenceOwner} must be an object`);
        continue;
      }
      const allowedEvidenceKeys = new Set(Object.keys(schema.$defs?.evidence?.properties ?? {}));
      for (const key of Object.keys(evidence)) {
        if (!allowedEvidenceKeys.has(key)) {
          violations.push(`${flowManifestFile}: ${evidenceOwner} has unrecognized key ${key}`);
        }
      }
      if (!isNonEmptyString(evidence.file)) {
        violations.push(`${flowManifestFile}: ${evidenceOwner}.file must be a non-empty path`);
      }
      validateStringArray({ owner: evidenceOwner, field: "tokens", value: evidence.tokens });
    }
  }
}

function validateFlowManifest() {
  const manifestPath = path.join(repoRoot, flowManifestFile);
  if (!existsSync(manifestPath)) {
    violations.push(`Missing observability flow manifest ${flowManifestFile}`);
    return;
  }

  const schemaPath = path.join(repoRoot, flowSchemaFile);
  if (!existsSync(schemaPath)) {
    violations.push(`Missing observability flow schema ${flowSchemaFile}`);
    return;
  }

  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const schema = JSON.parse(readFileSync(schemaPath, "utf8"));
  validateFlowManifestSchema(manifest, schema);

  const requiredFacets = manifest.requiredFacets ?? [];
  const flows = manifest.flows ?? [];
  const requiredProducts = activeObservabilityProducts();
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
