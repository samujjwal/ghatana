#!/usr/bin/env node
// Simple AJV-based validator that loads all schemas under contracts/jsonschema and
// validates a sample payload file passed as the first argument.
const fs = require("fs");
const path = require("path");

function findRepoRoot(startDir) {
  let cur = startDir || process.cwd();
  while (true) {
    if (
      fs.existsSync(path.join(cur, "pnpm-workspace.yaml")) ||
      fs.existsSync(path.join(cur, "package.json")) ||
      fs.existsSync(path.join(cur, ".git"))
    ) {
      return cur;
    }
    const parent = path.dirname(cur);
    if (parent === cur) return startDir || process.cwd();
    cur = parent;
  }
}

function tryRequireCandidates() {
  const repoRoot = findRepoRoot(__dirname);
  const candidates = [
    "ajv/dist/2020",
    "ajv",
    path.join(repoRoot, "node_modules", "ajv", "dist", "2020"),
    path.join(repoRoot, "node_modules", "ajv"),
    path.join(
      repoRoot,
      "products",
      "desktop",
      "node_modules",
      "ajv",
      "dist",
      "2020"
    ),
    path.join(repoRoot, "services", "desktop", "node_modules", "ajv"),
  ];
  for (const c of candidates) {
    try {
      const mod = require(c);
      return mod;
    } catch {
      // continue
    }
  }
  return null;
}

let Ajv = null;
let DRAFT2020 = null;
const ajvModule = tryRequireCandidates();
if (!ajvModule) {
  console.error(
    "Unable to find Ajv. Please run the workspace install (pnpm install) or ensure ajv is available in node_modules."
  );
  process.exit(2);
}

try {
  // prefer the Ajv2020 variant when available
  const ajv2020 = ajvModule["default"] || ajvModule;
  Ajv = ajv2020;
} catch {
  Ajv = ajvModule;
}

// try to load the draft 2020 meta-schema from the same module location
try {
  const repoRoot = findRepoRoot(__dirname);
  const candidates = [
    "ajv/dist/refs/json-schema-draft-2020-12.json",
    path.join(
      repoRoot,
      "node_modules",
      "ajv",
      "dist",
      "refs",
      "json-schema-draft-2020-12.json"
    ),
    path.join(
      repoRoot,
      "products",
      "desktop",
      "node_modules",
      "ajv",
      "dist",
      "refs",
      "json-schema-draft-2020-12.json"
    ),
  ];
  for (const c of candidates) {
    try {
      DRAFT2020 = require(c);
      break;
    } catch {
      // ignore
    }
  }
} catch {
  // ignore
}

const repoRoot = findRepoRoot(__dirname);
const schemasDir = path.join(repoRoot, "shared", "test-fixtures", "config-envelope", "schemas");

if (!fs.existsSync(schemasDir)) {
  console.error("Schemas directory not found at", schemasDir);
  process.exit(2);
}

async function main() {
  const sampleFile = process.argv[2];
  if (!sampleFile) {
    console.error("Usage: validate.js <sample-json-file>");
    process.exit(2);
  }
  const ajv = new Ajv({ strict: false });
  if (DRAFT2020)
    ajv.addSchema(
      DRAFT2020,
      DRAFT2020.$id || "https://json-schema.org/draft/2020-12/schema"
    );
  const files = fs.readdirSync(schemasDir).filter((f) => f.endsWith(".json"));
  for (const f of files) {
    const p = path.join(schemasDir, f);
    let schema = JSON.parse(fs.readFileSync(p, "utf8"));
    // Remove $schema to avoid Ajv trying to validate the schema against a meta-schema
    if (schema && schema.$schema) delete schema.$schema;
    const id = schema.$id || schema.title || f;
    ajv.addSchema(schema, id);
    // also register under the filename for convenience (e.g., ConfigEnvelope.json)
    ajv.addSchema(schema, f);
  }

  // Resolve sample file: try exact path, then repo-root relative, then services/desktop relative
  const candidatePaths = [
    path.resolve(sampleFile),
    path.join(repoRoot, sampleFile),
    path.join(repoRoot, "products", "desktop", sampleFile),
    path.join(
      repoRoot,
      "shared",
      "test-fixtures",
      "config-envelope",
      "samples",
      path.basename(sampleFile)
    ),
  ];
  let samplePath = null;
  for (const p of candidatePaths) {
    if (p && fs.existsSync(p)) {
      samplePath = p;
      break;
    }
  }
  if (!samplePath) {
    console.error("Sample file not found. Tried the following paths:");
    for (const p of candidatePaths) console.error(" -", p);
    process.exit(2);
  }
  const sample = JSON.parse(fs.readFileSync(samplePath, "utf8"));

  // If sample has a "$schemaId" property we try to validate against that id
  const targetId = sample.$schemaId || sample.schemaId;
  if (targetId) {
    const validate = ajv.getSchema(targetId);
    if (!validate) {
      console.error(
        "Schema with id",
        targetId,
        "not found in contracts/jsonschema"
      );
      process.exit(3);
    }
    const ok = validate(sample);
    if (!ok) {
      console.error("Validation errors:", validate.errors);
      process.exit(1);
    }
    console.log("Validation passed");
    process.exit(0);
  }

  // Otherwise try all schemas until one passes
  for (const f of files) {
    const s = fs.readFileSync(path.join(schemasDir, f), "utf8");
    const schema = JSON.parse(s);
    const validate = ajv.compile(schema);
    const ok = validate(sample);
    if (ok) {
      console.log("Validation passed against", f);
      process.exit(0);
    }
  }
  console.error("No schema matched sample; validation failed");
  process.exit(1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
