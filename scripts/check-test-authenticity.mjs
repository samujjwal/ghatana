#!/usr/bin/env node
/**
 * Cross-platform test-authenticity gate.
 *
 * Enforces anti-theater rules without requiring a POSIX shell on Windows.
 */

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { loadCanonicalRegistry, resolveAffectedProducts } from "./resolve-affected-products.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

const args = process.argv.slice(2);
let changedOnly = args.includes("--changed-only");
const baseRefArg = args.find((arg) => arg.startsWith("--base-ref="));
const baseRef = argValue("--base") ?? baseRefArg?.slice("--base-ref=".length) ?? process.env.GITHUB_BASE_SHA ?? "origin/main";
const headRef = argValue("--head") ?? process.env.GITHUB_SHA ?? "HEAD";
const explicitPaths = splitCsv(argValue("--paths"));

const scanRoots = [
  "platform",
  "platform-kernel",
  "platform-plugins",
  "shared-services",
  "products/audio-video",
  "products/data-cloud",
  "products/data-cloud/planes/action",
  "products/yappc",
].filter((candidate) => existsSync(path.join(repoRoot, candidate)));

if (scanRoots.length === 0) {
  console.log("No scan roots found; skipping test-authenticity check.");
  process.exit(0);
}

const changedFiles = changedOnly ? readChangedFiles(baseRef, headRef) : null;
if (changedOnly && changedFiles?.size === 0) {
  console.log(`Changed-only mode enabled, but no changed files were detected against ${baseRef}...${headRef}; treating as no-op.`);
  process.exit(0);
}

if (changedOnly && changedFiles !== null) {
  const registry = loadCanonicalRegistry(repoRoot);
  const affected = resolveAffectedProducts([...changedFiles], registry, {
    businessProductsOnly: false,
    includeDemo: false,
  });
  if (affected.docsOnly) {
    console.log("Changed-only mode resolved a docs-only change set; skipping test-authenticity scan.");
    process.exit(0);
  }
}

const violations = [];

checkPlaceholderAssertions();
checkSkippedTsTests();
checkDisabledJavaTests();
checkInvalidJavaTextBlocks();

if (violations.length === 0) {
  console.log("\nAll test-authenticity checks passed.");
  process.exit(0);
}

console.error("\nTest-authenticity checks failed.");
for (const violation of violations) {
  console.error(violation);
}
process.exit(1);

function argValue(name) {
  const index = args.indexOf(name);
  return index >= 0 ? args[index + 1] : undefined;
}

function splitCsv(value) {
  return String(value ?? "")
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map(normalizePath);
}

function isCi() {
  return process.env.CI === "true" || Boolean(process.env.GITHUB_ACTIONS);
}

function readChangedFiles(ref, head) {
  if (explicitPaths.length > 0) {
    return new Set(explicitPaths);
  }

  try {
    const output = execFileSync("git", ["diff", "--name-only", `${ref}...${head}`], {
      cwd: repoRoot,
      encoding: "utf8",
    });
    return new Set(output.split(/\r?\n/).filter(Boolean).map(normalizePath));
  } catch (error) {
    const message = `Could not resolve changed-only test-authenticity diff ${ref}...${head}.`;
    if (isCi()) {
      console.error(`${message} Fetch the base ref or pass --base/--head explicitly.`);
      process.exit(1);
    }
    console.warn(`${message} Falling back to a full local scan.`);
    changedOnly = false;
    return new Set();
  }
}

function normalizePath(value) {
  return value.replace(/\\/g, "/");
}

function isIgnored(relPath) {
  return relPath.includes("/node_modules/") || relPath.includes("/build/") || relPath.includes("/dist/") || relPath.includes("/bin/");
}

function isInScanScope(relPath) {
  if (!changedOnly) {
    return true;
  }
  return changedFiles !== null && changedFiles.has(relPath);
}

function walkFiles(root) {
  const results = [];
  const stack = [path.join(repoRoot, root)];

  while (stack.length > 0) {
    const current = stack.pop();
    if (current === undefined) {
      continue;
    }

    let entries;
    try {
      entries = readdirSync(current);
    } catch {
      continue;
    }

    for (const entry of entries) {
      const fullPath = path.join(current, entry);
      const relPath = normalizePath(path.relative(repoRoot, fullPath));
      if (isIgnored(relPath)) {
        continue;
      }

      let stats;
      try {
        stats = statSync(fullPath);
      } catch {
        continue;
      }

      if (stats.isDirectory()) {
        stack.push(fullPath);
      } else if (stats.isFile() && isInScanScope(relPath)) {
        results.push(relPath);
      }
    }
  }

  return results;
}

function readLines(relPath) {
  return readFileSync(path.join(repoRoot, relPath), "utf8").split(/\r?\n/);
}

function allFiles() {
  return scanRoots.flatMap(walkFiles);
}

function reportSection(title) {
  console.log(`\n=== ${title} ===`);
}

function checkPlaceholderAssertions() {
  reportSection("Checking placeholder assertions");
  const pattern = /expect\(true\)\.toBe\(true\)|expect\(1\)\.toBe\(1\)/;
  const matches = allFiles()
    .filter((file) => /\.(test|spec)\.tsx?$/.test(file) || /(?:Test|IT)\.java$/.test(file))
    .flatMap((file) =>
      readLines(file)
        .map((line, index) => ({ file, line, lineNumber: index + 1 }))
        .filter((entry) => pattern.test(entry.line)),
    );
  recordMatches(matches, "ERROR: Placeholder assertions detected.");
}

function checkSkippedTsTests() {
  reportSection("Checking skipped TS/JS tests");
  const pattern = /\b(it|describe)\.skip\(|\bxit\(/;
  const matches = allFiles()
    .filter((file) => /\.(test|spec)\.tsx?$/.test(file))
    .flatMap((file) => ticketlessMatches(file, pattern));
  recordMatches(matches, "ERROR: Skipped TS/JS tests without GH ticket reference.");
}

function checkDisabledJavaTests() {
  reportSection("Checking @Disabled Java tests");
  const matches = allFiles()
    .filter((file) => file.endsWith(".java") && file.includes("/src/test/"))
    .flatMap((file) => ticketlessMatches(file, /@Disabled(\(|$)/));
  recordMatches(matches, "ERROR: @Disabled Java tests without GH ticket reference.");
}

function checkInvalidJavaTextBlocks() {
  reportSection("Checking invalid Java text block openings");
  const pattern = /"""\s*\/\/\s*GH-[0-9]+/;
  const matches = allFiles()
    .filter((file) => file.endsWith(".java"))
    .flatMap((file) =>
      readLines(file)
        .map((line, index) => ({ file, line, lineNumber: index + 1 }))
        .filter((entry) => pattern.test(entry.line)),
    );
  recordMatches(matches, "ERROR: Invalid Java text block openings detected (inline comments after \"\"\").");
}

function ticketlessMatches(file, pattern) {
  const lines = readLines(file);
  return lines
    .map((line, index) => ({ file, line, lineNumber: index + 1, nextLine: lines[index + 1] ?? "" }))
    .filter((entry) => pattern.test(entry.line))
    .filter((entry) => !/GH-[0-9]+/.test(entry.line) && !/GH-[0-9]+/.test(entry.nextLine));
}

function recordMatches(matches, errorMessage) {
  if (matches.length === 0) {
    console.log("OK");
    return;
  }

  for (const match of matches) {
    violations.push(`${match.file}:${match.lineNumber}: ${match.line.trim()}`);
  }
  violations.push(errorMessage);
}
