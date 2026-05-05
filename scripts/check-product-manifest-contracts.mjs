#!/usr/bin/env node

import { existsSync, readFileSync } from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

const productShape = JSON.parse(
  readFileSync(path.join(repoRoot, "config/product-shape.json"), "utf8"),
);

const REQUIRED_FIELDS = [
  "id",
  "version",
  "kernelCapabilitiesConsumed",
  "pluginsConsumed",
  "bridgesConsumed",
  "domainPacksProvided",
  "uiSurfaces",
  "runtimeServices",
  "dataSensitivity",
];

const MANIFESTS = [
  {
    product: "phr",
    file: "products/phr/domain-pack-manifest.yaml",
    format: "yaml",
  },
  {
    product: "finance",
    file: "products/finance/domain-pack-manifest.yaml",
    format: "yaml",
  },
  {
    product: "digital-marketing",
    file: "products/digital-marketing/dm-domain-packs/domain-pack.json",
    format: "json",
  },
  {
    product: "flashit",
    file: "products/flashit/domain-pack-manifest.yaml",
    format: "yaml",
  },
];

const violations = [];
const ALLOW_EMPTY_ARRAY_FIELDS = new Set(["bridgesConsumed", "uiSurfaces"]);

function addViolation(file, message) {
  violations.push({ file, message });
}

function parsePackYaml(text) {
  const result = {};
  const lines = text.split(/\r?\n/);
  let inPack = false;
  let activeListField = null;

  for (const rawLine of lines) {
    const line = rawLine.replace(/\t/g, "    ");
    const trimmed = line.trim();

    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }

    if (!inPack) {
      if (trimmed === "pack:") {
        inPack = true;
      }
      continue;
    }

    if (!line.startsWith("  ")) {
      break;
    }

    const fieldMatch = line.match(/^  ([A-Za-z0-9]+):(?:\s*(.*))?$/);
    if (fieldMatch) {
      const [, field, rawValue = ""] = fieldMatch;
      const value = rawValue.trim();
      activeListField = null;

      if (!value) {
        result[field] = [];
        activeListField = field;
        continue;
      }

      if (value === "[]") {
        result[field] = [];
        continue;
      }

      result[field] = value.replace(/^["']|["']$/g, "");
      continue;
    }

    const listItemMatch = line.match(/^    -\s*(.+)$/);
    if (listItemMatch && activeListField) {
      result[activeListField].push(listItemMatch[1].trim().replace(/^["']|["']$/g, ""));
      continue;
    }

    const nestedListStarterMatch = line.match(/^  ([A-Za-z0-9]+):\s*$/);
    if (nestedListStarterMatch) {
      const [, field] = nestedListStarterMatch;
      if (!Array.isArray(result[field])) {
        result[field] = [];
      }
      activeListField = field;
      continue;
    }

    if (line.startsWith("    ") && activeListField && !Array.isArray(result[activeListField])) {
      result[activeListField] = [];
    }
  }

  return result;
}

function normalizeManifest(entry) {
  const absolutePath = path.join(repoRoot, entry.file);
  if (!existsSync(absolutePath)) {
    addViolation(entry.file, "manifest file is missing");
    return null;
  }

  const content = readFileSync(absolutePath, "utf8");
  if (entry.format === "json") {
    return JSON.parse(content);
  }

  return parsePackYaml(content);
}

function validateManifest(entry, manifest) {
  for (const field of REQUIRED_FIELDS) {
    if (!(field in manifest)) {
      addViolation(entry.file, `missing required field '${field}'`);
      continue;
    }

    const value = manifest[field];
    if (Array.isArray(value) && !ALLOW_EMPTY_ARRAY_FIELDS.has(field) && value.length === 0) {
      addViolation(entry.file, `'${field}' must not be empty`);
    }
    if (!Array.isArray(value) && (value === null || value === undefined || String(value).trim() === "")) {
      addViolation(entry.file, `'${field}' must not be blank`);
    }
  }

  const shape = productShape.products[entry.product];
  if (!shape) {
    addViolation(entry.file, `missing product shape declaration for '${entry.product}'`);
    return;
  }

  if (!Array.isArray(shape.clientPackages)) {
    addViolation(entry.file, "product-shape.json must declare clientPackages[]");
    return;
  }

  if (shape.ui) {
    if (shape.clientPackages.length === 0) {
      addViolation(entry.file, "UI-enabled product must declare at least one client package");
    }

    for (const packageJson of shape.clientPackages) {
      if (!existsSync(path.join(repoRoot, packageJson))) {
        addViolation(entry.file, `declared UI package does not exist: ${packageJson}`);
      }
    }

    if (!Array.isArray(manifest.uiSurfaces) || manifest.uiSurfaces.length === 0) {
      addViolation(entry.file, "UI-enabled product must declare at least one uiSurface");
    }
  } else {
    if (shape.clientPackages.length > 0) {
      addViolation(entry.file, "backend-only product must not declare client packages in product-shape.json");
    }
    if (Array.isArray(manifest.uiSurfaces) && manifest.uiSurfaces.length > 0) {
      addViolation(entry.file, "backend-only product must not declare uiSurfaces");
    }

    const declaration = shape.backendOnlyDeclaration;
    if (!declaration?.file || !declaration?.mustContain) {
      addViolation(entry.file, "backend-only product must declare backendOnlyDeclaration in product-shape.json");
    } else {
      const declarationPath = path.join(repoRoot, declaration.file);
      if (!existsSync(declarationPath)) {
        addViolation(entry.file, `backendOnlyDeclaration file does not exist: ${declaration.file}`);
      } else {
        const declarationText = readFileSync(declarationPath, "utf8").toLowerCase();
        if (!declarationText.includes(String(declaration.mustContain).toLowerCase())) {
          addViolation(
            declaration.file,
            `backend-only declaration must contain '${declaration.mustContain}'`,
          );
        }
      }
    }
  }
}

for (const entry of MANIFESTS) {
  const manifest = normalizeManifest(entry);
  if (!manifest) {
    continue;
  }
  validateManifest(entry, manifest);
}

if (violations.length > 0) {
  console.error(`❌ Product manifest contract check failed with ${violations.length} violation(s):\n`);
  for (const violation of violations) {
    console.error(`- ${violation.file}: ${violation.message}`);
  }
  process.exit(1);
}

console.log("✅ Product manifest contract check passed.");
