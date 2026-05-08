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
    buildFile: "products/phr/build.gradle.kts",
  },
  {
    product: "finance",
    file: "products/finance/domain-pack-manifest.yaml",
    format: "yaml",
    buildFile: "products/finance/build.gradle.kts",
  },
  {
    product: "digital-marketing",
    file: "products/digital-marketing/dm-domain-packs/domain-pack.json",
    format: "json",
    buildFile: "products/digital-marketing/dm-domain-packs/build.gradle.kts",
  },
  {
    product: "flashit",
    file: "products/flashit/domain-pack-manifest.yaml",
    format: "yaml",
    buildFile: "products/flashit/build.gradle.kts",
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

  validatePluginOwnership(entry, manifest);
  validateManifestFieldValues(entry, manifest);
  validateShapeManifestAlignment(entry, manifest);
}

// ---------------------------------------------------------------------------
// Cross-validation: product-shape.json uiMode vs manifest uiSurfaces
// ---------------------------------------------------------------------------

/**
 * Validates that the `uiMode` declared in product-shape.json is consistent
 * with the `uiSurfaces` array in the product domain-pack manifest.
 *
 * Alignment rules:
 *  - "backend-only" → uiSurfaces must be empty
 *  - "web"          → uiSurfaces must include "web"
 *  - "multi-surface"→ uiSurfaces must include both "web" and "mobile"
 */
function validateShapeManifestAlignment(entry, manifest) {
  const shape = productShape.products[entry.product];
  if (!shape) {
    return; // already reported by validateManifest
  }

  const uiMode = shape.uiMode;
  const uiSurfaces = Array.isArray(manifest.uiSurfaces) ? manifest.uiSurfaces : [];

  if (uiMode === 'backend-only') {
    if (uiSurfaces.length > 0) {
      addViolation(
        entry.file,
        `product-shape.json declares uiMode "backend-only" but manifest declares uiSurfaces: [${uiSurfaces.join(', ')}]`,
      );
    }
  } else if (uiMode === 'web') {
    if (!uiSurfaces.includes('web')) {
      addViolation(
        entry.file,
        `product-shape.json declares uiMode "web" but manifest uiSurfaces does not include "web" — got: [${uiSurfaces.join(', ')}]`,
      );
    }
  } else if (uiMode === 'multi-surface') {
    const missing = ['web', 'mobile'].filter((s) => !uiSurfaces.includes(s));
    if (missing.length > 0) {
      addViolation(
        entry.file,
        `product-shape.json declares uiMode "multi-surface" but manifest uiSurfaces is missing: [${missing.join(', ')}]`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// Schema-aware field value validation (type, enum, pattern constraints)
// ---------------------------------------------------------------------------

const KNOWN_DATA_SENSITIVITY = new Set([
  // Generic classification levels
  'HIGH', 'MEDIUM', 'LOW', 'RESTRICTED', 'PUBLIC',
  // Domain-specific regulatory labels used in product manifests
  'regulated-health',
  'regulated-finance',
  'marketing-consent',
  'personal-journal',
]);

/** Semver pattern: x.y.z with optional pre-release / build metadata */
const SEMVER_PATTERN = /^\d+\.\d+\.\d+(?:[-+].+)?$/;

/** Kebab-case slug pattern for manifest ids */
const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/**
 * Validates schema-level constraints on manifest field values — not just
 * presence.  Reports violations for:
 *  - `id`           must be a non-empty kebab-case slug
 *  - `version`      must match semver x.y.z
 *  - `dataSensitivity` must be one of the canonical enum values
 *  - All array fields must contain only string items (not nested objects)
 *  - `kernelCapabilitiesConsumed` items must be non-empty strings
 */
function validateManifestFieldValues(entry, manifest) {
  // id — must be a non-empty slug
  const id = manifest.id;
  if (id !== undefined && id !== null && typeof id === 'string') {
    if (!SLUG_PATTERN.test(id)) {
      addViolation(entry.file, `'id' must be a kebab-case slug, got: "${id}"`);
    }
  }

  // version — must match semver
  const version = manifest.version;
  if (version !== undefined && version !== null && typeof version === 'string') {
    if (!SEMVER_PATTERN.test(version)) {
      addViolation(entry.file, `'version' must match semver x.y.z, got: "${version}"`);
    }
  }

  // dataSensitivity — must be one of the canonical enum values
  const dataSensitivity = manifest.dataSensitivity;
  if (dataSensitivity !== undefined && dataSensitivity !== null) {
    const val = String(dataSensitivity).trim();
    // Accept exact match OR uppercase match (both casing conventions present in repo)
    if (!KNOWN_DATA_SENSITIVITY.has(val) && !KNOWN_DATA_SENSITIVITY.has(val.toUpperCase())) {
      addViolation(
        entry.file,
        `'dataSensitivity' must be one of ${[...KNOWN_DATA_SENSITIVITY].join(', ')}, got: "${dataSensitivity}"`,
      );
    }
  }

  // All array fields must contain only string items
  for (const field of REQUIRED_FIELDS) {
    const value = manifest[field];
    if (!Array.isArray(value)) {
      continue;
    }
    for (const item of value) {
      if (typeof item !== 'string') {
        addViolation(
          entry.file,
          `'${field}' array must contain only strings, found item of type '${typeof item}'`,
        );
        break; // one violation per field is enough
      }
      if (item.trim() === '') {
        addViolation(entry.file, `'${field}' array must not contain blank string items`);
        break;
      }
    }
  }

  // kernelCapabilitiesConsumed items must be non-empty, non-whitespace strings
  const caps = manifest.kernelCapabilitiesConsumed;
  if (Array.isArray(caps)) {
    for (const cap of caps) {
      if (typeof cap !== 'string' || cap.trim() === '') {
        addViolation(entry.file, `'kernelCapabilitiesConsumed' must contain non-empty capability name strings`);
        break;
      }
    }
  }
}

function validatePluginOwnership(entry, manifest) {
  const buildPath = path.join(repoRoot, entry.buildFile);
  if (!existsSync(buildPath)) {
    addViolation(entry.file, `declared build file does not exist: ${entry.buildFile}`);
    return;
  }

  const buildText = readFileSync(buildPath, "utf8");
  const pluginDependencyPattern =
    /^\s*(?:api|implementation)\(project\(":platform-plugins:(plugin-[^"]+)"\)\)/gm;

  const declaredPlugins = new Set((manifest.pluginsConsumed ?? []).map(String));
  const buildPlugins = new Set();

  for (const match of buildText.matchAll(pluginDependencyPattern)) {
    buildPlugins.add(match[1]);
  }

  for (const plugin of buildPlugins) {
    if (!declaredPlugins.has(plugin)) {
      addViolation(
        entry.buildFile,
        `build declares platform plugin '${plugin}' but manifest pluginsConsumed does not`,
      );
    }
  }

  for (const plugin of declaredPlugins) {
    if (!buildPlugins.has(plugin)) {
      addViolation(
        entry.buildFile,
        `manifest declares platform plugin '${plugin}' but build does not depend on it`,
      );
    }
  }

  if (entry.product === "finance") {
    validateFinanceDomainDependencyScopes(entry.buildFile, buildText);
  }
}

function validateFinanceDomainDependencyScopes(buildFile, buildText) {
  const compileScopedDomainPattern =
    /^\s*(?:api|implementation)\(project\(":(products:finance:domains:[^"]+)"\)\)/gm;
  const runtimeScopedDomainPattern =
    /^\s*runtimeOnly\(project\(":(products:finance:domains:[^"]+)"\)\)/gm;

  const compileScopedDomains = [...buildText.matchAll(compileScopedDomainPattern)].map((match) => match[1]);
  const runtimeScopedDomains = [...buildText.matchAll(runtimeScopedDomainPattern)].map((match) => match[1]);

  if (compileScopedDomains.length > 0) {
    addViolation(
      buildFile,
      `finance root must not compile-link domain modules; found compile-scoped dependencies: ${compileScopedDomains.join(", ")}`,
    );
  }

  if (runtimeScopedDomains.length === 0) {
    addViolation(
      buildFile,
      "finance root must compose domain modules via runtimeOnly dependencies at the composition boundary",
    );
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
