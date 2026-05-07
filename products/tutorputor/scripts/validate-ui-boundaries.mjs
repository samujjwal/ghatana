#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const productRoot = path.resolve("products/tutorputor");
const baselinePath = path.join(productRoot, "config/ui-boundary-baseline.json");
const baseline = JSON.parse(fs.readFileSync(baselinePath, "utf8"));
const visualRegressionSpecs = [
  "apps/tutorputor-web/e2e/ui-visual-regression.spec.ts",
  "apps/tutorputor-admin/e2e/admin-visual-regression.spec.ts",
];
const appRoots = [
  path.join(productRoot, "apps/tutorputor-web/src"),
  path.join(productRoot, "apps/tutorputor-admin/src"),
];
const ignoredSegments = new Set(["node_modules", "dist", "build", "coverage"]);
const sourceExtensions = new Set([".ts", ".tsx", ".css"]);
const sharedPrimitiveNames = ["Button.tsx", "Input.tsx", "Badge.tsx", "Spinner.tsx"];
const forbiddenDirectImports = [
  /from\s+["']@ghatana\/tokens\/[^"']+["']/,
  /from\s+["']@ghatana\/theme\/[^"']+["']/,
  /from\s+["']@ghatana\/ui\/[^"']+["']/,
  /from\s+["']@tutorputor\/ui\/src\//,
];

function walk(dir, files = []) {
  if (!fs.existsSync(dir)) return files;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (ignoredSegments.has(entry.name)) continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      walk(fullPath, files);
    } else if (entry.isFile() && sourceExtensions.has(path.extname(entry.name))) {
      files.push(fullPath);
    }
  }
  return files;
}

function relative(filePath) {
  return path.relative(productRoot, filePath).replaceAll("\\", "/");
}

const appFiles = appRoots.flatMap((root) => walk(root));
let hexColorOccurrences = 0;
const filesWithHexColors = new Set();
const importViolations = [];

for (const filePath of appFiles) {
  const content = fs.readFileSync(filePath, "utf8");
  const hexMatches = content.match(/#[0-9a-fA-F]{3,8}\b/g) ?? [];
  if (hexMatches.length > 0) {
    hexColorOccurrences += hexMatches.length;
    filesWithHexColors.add(relative(filePath));
  }
  const lines = content.split(/\r?\n/);
  lines.forEach((line, index) => {
    if (forbiddenDirectImports.some((pattern) => pattern.test(line))) {
      importViolations.push(`${relative(filePath)}:${index + 1} ${line.trim()}`);
    }
  });
}

const localSharedPrimitiveFilePaths = [];
for (const appUiDir of [
  path.join(productRoot, "apps/tutorputor-web/src/components/ui"),
  path.join(productRoot, "apps/tutorputor-admin/src/components/ui"),
]) {
  for (const primitiveName of sharedPrimitiveNames) {
    const primitivePath = path.join(appUiDir, primitiveName);
    if (fs.existsSync(primitivePath)) {
      localSharedPrimitiveFilePaths.push(relative(primitivePath));
    }
  }
}

const failures = [];
for (const relativeSpecPath of visualRegressionSpecs) {
  const fullSpecPath = path.join(productRoot, relativeSpecPath);
  if (!fs.existsSync(fullSpecPath)) {
    failures.push(`Missing visual regression spec: ${relativeSpecPath}`);
    continue;
  }
  const spec = fs.readFileSync(fullSpecPath, "utf8");
  if (!spec.includes("toHaveScreenshot")) {
    failures.push(`${relativeSpecPath} must use Playwright toHaveScreenshot`);
  }
}
if (importViolations.length > 0) {
  failures.push(`Forbidden direct design-system subpath imports:\n${importViolations.join("\n")}`);
}
if (hexColorOccurrences > baseline.hexColorOccurrences) {
  failures.push(
    `Hardcoded hex colors increased from ${baseline.hexColorOccurrences} to ${hexColorOccurrences}. Move colors to shared tokens or reduce the baseline.`,
  );
}
if (filesWithHexColors.size > baseline.filesWithHexColors) {
  failures.push(
    `Files with hardcoded hex colors increased from ${baseline.filesWithHexColors} to ${filesWithHexColors.size}.`,
  );
}
if (localSharedPrimitiveFilePaths.length > baseline.localSharedPrimitiveFiles) {
  failures.push(
    `App-local shared primitives increased from ${baseline.localSharedPrimitiveFiles} to ${localSharedPrimitiveFilePaths.length}. Move shared primitives to @tutorputor/ui.`,
  );
}

for (const expectedPath of baseline.localSharedPrimitiveFilePaths) {
  if (!localSharedPrimitiveFilePaths.includes(expectedPath)) {
    continue;
  }
}

if (failures.length > 0) {
  console.error("UI boundary validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log(
  `UI boundary validation passed (${hexColorOccurrences} hex colors, ${localSharedPrimitiveFilePaths.length} app-local shared primitive files).`,
);
