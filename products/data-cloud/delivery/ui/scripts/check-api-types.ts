#!/usr/bin/env tsx
/**
 * Check if Generated API Types are Up-to-Date
 *
 * This script verifies that the generated API types match the current OpenAPI specs.
 * Used in pre-commit hooks and CI to ensure types are regenerated when specs change.
 *
 * Usage:
 *   pnpm check:api-types
 *
 * @doc.type script
 * @doc.purpose Verify generated API types match OpenAPI specs
 * @doc.layer frontend
 * @doc.pattern BuildScript
 */

import { execSync } from "node:child_process";
import { existsSync, readFileSync, unlinkSync } from "node:fs";
import { join } from "node:path";

const GENERATED_DIR = "src/contracts/generated";

function cleanGeneratedFiles(): void {
  const files = ["data-cloud.ts", "index.ts"];
  for (const file of files) {
    const filePath = join(GENERATED_DIR, file);
    if (existsSync(filePath)) {
      unlinkSync(filePath);
      console.log(`Deleted: ${filePath}`);
    }
  }
}

function generateTypes(): void {
  try {
    execSync("pnpm generate:api-types", { stdio: "inherit" });
    // Run prettier on generated files so the comparison is against the same
    // formatted output that lint-staged will produce (lint-staged runs
    // prettier --write on all staged *.ts files before this hook fires).
    execSync(
      "npx prettier --write src/contracts/generated/data-cloud.ts src/contracts/generated/index.ts",
      {
        stdio: "ignore",
      },
    );
  } catch (error) {
    console.error("Failed to generate types:", error);
    process.exit(1);
  }
}

function checkGitDiff(): boolean {
  try {
    const diff = execSync("git diff --name-only", { encoding: "utf-8" });
    const changedFiles = diff.trim().split("\n").filter(Boolean);
    const generatedFilesChanged = changedFiles.some((file) =>
      file.startsWith(GENERATED_DIR),
    );
    return generatedFilesChanged;
  } catch {
    // Not in git or no git available, assume types need checking
    return true;
  }
}

function main(): void {
  console.log("🔍 Checking if generated API types are up-to-date...\n");

  // Store current generated files
  const originalFiles: Record<string, string> = {};
  const files = ["data-cloud.ts", "index.ts"];
  for (const file of files) {
    const filePath = join(GENERATED_DIR, file);
    if (existsSync(filePath)) {
      originalFiles[file] = readFileSync(filePath, "utf-8");
    }
  }

  // Regenerate types
  cleanGeneratedFiles();
  generateTypes();

  // Compare with original
  let hasChanges = false;
  for (const file of files) {
    const filePath = join(GENERATED_DIR, file);
    if (existsSync(filePath)) {
      const current = readFileSync(filePath, "utf-8");
      const original = originalFiles[file];
      if (original !== current) {
        console.log(`❌ ${file} has changed`);
        hasChanges = true;
      } else {
        console.log(`✅ ${file} is up-to-date`);
      }
    }
  }

  if (hasChanges) {
    console.log("\n❌ Generated API types are out of sync with OpenAPI specs");
    console.log("Run: pnpm generate:api-types");
    process.exit(1);
  }

  console.log("\n✅ Generated API types are up-to-date");
}

main();
