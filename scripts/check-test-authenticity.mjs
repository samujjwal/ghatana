#!/usr/bin/env node
/**
 * Cross-platform test-authenticity gate.
 *
 * Enforces the same anti-theater rules as check-test-authenticity.sh without
 * requiring a POSIX shell on Windows.
 */

import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

const args = process.argv.slice(2);
const changedOnly = args.includes("--changed-only");
const baseRefArg = args.find((arg) => arg.startsWith("--base-ref="));
const baseRef = baseRefArg?.slice("--base-ref=".length) ?? "origin/main";

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

const changedFiles = changedOnly ? readChangedFiles(baseRef) : null;
if (changedOnly && changedFiles?.size === 0) {
  console.log(`Changed-only mode enabled, but no changed files were detected against ${baseRef}.`);
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

function readChangedFiles(ref) {
  try {
    const output = execFileSync("git", ["diff", "--name-only", `${ref}...HEAD`], {
      cwd: repoRoot,
      encoding: "utf8",
    });
    return new Set(output.split(/\r?\n/).filter(Boolean).map(normalizePath));
  } catch {
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
