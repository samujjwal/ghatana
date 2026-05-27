#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const repoRoot = process.cwd();
const registryPath = path.join(repoRoot, "products/yappc/config/datacloud-collections.json");

function fail(message) {
  console.error(`YAPPC Data Cloud collection check failed: ${message}`);
  process.exit(1);
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (error) {
    fail(`Unable to read ${filePath}: ${error.message}`);
  }
}

const registry = readJson(registryPath);
const requiredNames = new Set(["kernel_lifecycle_truth", "yappc_platform_runs", "agent-executions"]);

if (registry.schemaVersion !== "1.0.0") {
  fail(`Expected schemaVersion 1.0.0, found ${registry.schemaVersion}`);
}

if (registry.product !== "yappc") {
  fail(`Expected product yappc, found ${registry.product}`);
}

if (!Array.isArray(registry.collections)) {
  fail("collections must be an array");
}

const names = new Set();
for (const collection of registry.collections) {
  if (!collection || typeof collection !== "object") {
    fail("collection entries must be objects");
  }

  const { name, owner, purpose, sourceFiles, forbiddenAliases } = collection;
  if (typeof name !== "string" || name.trim() !== name || name.length === 0) {
    fail("collection name must be a non-empty trimmed string");
  }
  if (names.has(name)) {
    fail(`Duplicate collection name: ${name}`);
  }
  names.add(name);

  if (typeof owner !== "string" || owner.length === 0) {
    fail(`${name} must declare an owner`);
  }
  if (typeof purpose !== "string" || purpose.length === 0) {
    fail(`${name} must declare a purpose`);
  }
  if (!Array.isArray(sourceFiles) || sourceFiles.length === 0) {
    fail(`${name} must declare sourceFiles`);
  }
  if (!Array.isArray(forbiddenAliases)) {
    fail(`${name} must declare forbiddenAliases`);
  }

  for (const relativePath of sourceFiles) {
    const absolutePath = path.join(repoRoot, relativePath);
    if (!fs.existsSync(absolutePath)) {
      fail(`${name} references missing source file ${relativePath}`);
    }

    const text = fs.readFileSync(absolutePath, "utf8");
    if (!text.includes(name)) {
      fail(`${relativePath} does not contain canonical collection name ${name}`);
    }

    for (const alias of forbiddenAliases) {
      if (alias && text.includes(alias)) {
        fail(`${relativePath} contains forbidden alias ${alias}; use ${name}`);
      }
    }
  }
}

for (const requiredName of requiredNames) {
  if (!names.has(requiredName)) {
    fail(`Missing required collection ${requiredName}`);
  }
}

console.log(`YAPPC Data Cloud collection names validated: ${Array.from(names).join(", ")}`);
