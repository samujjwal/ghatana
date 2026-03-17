#!/usr/bin/env node

/**
 * @file align-dependencies.js
 * @description Automated dependency version alignment script
 * @doc.type script
 * @doc.purpose Align dependency versions across all packages to prevent conflicts
 * @doc.layer tooling
 * @doc.pattern automation
 *
 * Usage:
 *   node scripts/align-dependencies.js [--dry-run] [--check-only]
 *
 * Options:
 *   --dry-run     Show changes without applying them
 *   --check-only  Report issues without fixing them
 *   --verbose     Show detailed output
 */

const fs = require("fs");
const path = require("path");

// Use native fs.readdirSync with recursive option (Node 20+)
function findPackageJsonFiles(dir, files = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);

    if (entry.isDirectory()) {
      // Skip node_modules and other ignored directories
      if (
        [
          "node_modules",
          ".git",
          "dist",
          "build",
          ".turbo",
          "coverage",
        ].includes(entry.name)
      ) {
        continue;
      }
      findPackageJsonFiles(fullPath, files);
    } else if (entry.isFile() && entry.name === "package.json") {
      files.push(fullPath);
    }
  }

  return files;
}

// Target versions for convergence
const TARGET_VERSIONS = {
  // Core framework
  react: "^19.2.4",
  "react-dom": "^19.2.4",

  // Build tools
  typescript: "^5.9.3",
  vite: "^7.3.1",
  vitest: "^4.0.18",

  // State management
  jotai: "^2.17.0",

  // UI/Animation
  "framer-motion": "^12.35.0",
  "lucide-react": "^0.563.0",

  // Testing
  "@testing-library/react": "^16.3.2",
  "@testing-library/jest-dom": "^6.9.1",

  // Styling
  tailwindcss: "^4.1.18",
  "tailwind-merge": "^3.4.0",
  clsx: "^2.1.1",

  // Utilities
  "date-fns": "^4.1.0",
  zod: "^4.3.6",
};

// Packages to exclude from automatic updates
const EXCLUDED_PACKAGES = [
  // Allow these to have different versions intentionally
  "@types/node", // Different packages may need different Node types
  "esbuild", // Build tool with specific version requirements
];

// Logging utilities
const verbose = process.argv.includes("--verbose");
const dryRun = process.argv.includes("--dry-run");
const checkOnly = process.argv.includes("--check-only");

function log(message) {
  console.log(message);
}

function logVerbose(message) {
  if (verbose) {
    console.log(`[VERBOSE] ${message}`);
  }
}

function logError(message) {
  console.error(`[ERROR] ${message}`);
}

function logWarning(message) {
  console.warn(`[WARNING] ${message}`);
}

/**
 * Find all package.json files in the monorepo
 */
async function getPackageJsonFiles() {
  // Find all package.json files
  const files = findPackageJsonFiles(process.cwd());

  // Filter to only include monorepo packages (not node_modules)
  return files.filter((file) => !file.includes("node_modules"));
}

/**
 * Read and parse package.json
 */
function readPackageJson(filePath) {
  try {
    const content = fs.readFileSync(filePath, "utf-8");
    return JSON.parse(content);
  } catch (error) {
    logError(`Failed to read ${filePath}: ${error.message}`);
    return null;
  }
}

/**
 * Write package.json with proper formatting
 */
function writePackageJson(filePath, content) {
  if (dryRun) {
    log(`[DRY-RUN] Would update ${filePath}`);
    return true;
  }

  try {
    const formatted = JSON.stringify(content, null, 2) + "\n";
    fs.writeFileSync(filePath, formatted, "utf-8");
    log(`✅ Updated ${filePath}`);
    return true;
  } catch (error) {
    logError(`Failed to write ${filePath}: ${error.message}`);
    return false;
  }
}

/**
 * Check if a dependency should be aligned
 */
function shouldAlignDependency(depName) {
  // Skip excluded packages
  if (EXCLUDED_PACKAGES.includes(depName)) {
    return false;
  }

  // Only align packages in our target list
  return TARGET_VERSIONS.hasOwnProperty(depName);
}

/**
 * Align dependencies in a package
 */
function alignDependencies(packageJson, packagePath) {
  let hasChanges = false;
  const packageName = packageJson.name || path.dirname(packagePath);

  logVerbose(`Checking ${packageName}...`);

  // Check dependencies
  if (packageJson.dependencies) {
    for (const [dep, version] of Object.entries(packageJson.dependencies)) {
      if (shouldAlignDependency(dep) && TARGET_VERSIONS[dep] !== version) {
        logWarning(
          `${packageName}: ${dep} ${version} → ${TARGET_VERSIONS[dep]}`,
        );
        if (!checkOnly) {
          packageJson.dependencies[dep] = TARGET_VERSIONS[dep];
        }
        hasChanges = true;
      }
    }
  }

  // Check devDependencies
  if (packageJson.devDependencies) {
    for (const [dep, version] of Object.entries(packageJson.devDependencies)) {
      if (shouldAlignDependency(dep) && TARGET_VERSIONS[dep] !== version) {
        logWarning(
          `${packageName}: ${dep} ${version} → ${TARGET_VERSIONS[dep]} (dev)`,
        );
        if (!checkOnly) {
          packageJson.devDependencies[dep] = TARGET_VERSIONS[dep];
        }
        hasChanges = true;
      }
    }
  }

  // Check peerDependencies
  if (packageJson.peerDependencies) {
    for (const [dep, version] of Object.entries(packageJson.peerDependencies)) {
      if (shouldAlignDependency(dep)) {
        // For peer deps, use broader ranges if specified
        const targetVersion = TARGET_VERSIONS[dep];
        const needsUpdate =
          version !== targetVersion &&
          !version.includes(targetVersion.replace("^", "").replace("~", ""));

        if (needsUpdate) {
          logWarning(
            `${packageName}: ${dep} ${version} → ${targetVersion} (peer)`,
          );
          if (!checkOnly) {
            packageJson.peerDependencies[dep] = targetVersion;
          }
          hasChanges = true;
        }
      }
    }
  }

  return hasChanges ? packageJson : null;
}

/**
 * Generate convergence report
 */
function generateReport(results) {
  const report = {
    timestamp: new Date().toISOString(),
    mode: dryRun ? "dry-run" : checkOnly ? "check-only" : "apply",
    totalPackages: results.length,
    packagesWithChanges: results.filter((r) => r.hasChanges).length,
    totalChanges: results.reduce((sum, r) => sum + r.changeCount, 0),
    packages: results,
  };

  const reportPath = "dependency-alignment-report.json";
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  log(`\n📊 Report saved to ${reportPath}`);

  return report;
}

/**
 * Main execution
 */
async function main() {
  log("🔧 Dependency Alignment Tool");
  log("============================");
  log(
    `Mode: ${dryRun ? "DRY RUN" : checkOnly ? "CHECK ONLY" : "APPLY CHANGES"}`,
  );
  log("");

  const startTime = Date.now();

  // Find all package.json files
  const packageFiles = await getPackageJsonFiles();
  log(`Found ${packageFiles.length} package.json files`);
  logVerbose(`Files: ${packageFiles.join(", ")}`);

  const results = [];
  let totalChanges = 0;

  // Process each package
  for (const filePath of packageFiles) {
    const fullPath = path.resolve(filePath);
    const packageJson = readPackageJson(fullPath);

    if (!packageJson) {
      continue;
    }

    const aligned = alignDependencies(packageJson, fullPath);

    if (aligned) {
      const changeCount =
        Object.keys(aligned.dependencies || {}).length +
        Object.keys(aligned.devDependencies || {}).length +
        Object.keys(aligned.peerDependencies || {}).length;

      results.push({
        package: aligned.name,
        path: filePath,
        hasChanges: true,
        changeCount,
      });

      totalChanges += changeCount;

      if (!checkOnly) {
        writePackageJson(fullPath, aligned);
      }
    } else {
      results.push({
        package: packageJson.name,
        path: filePath,
        hasChanges: false,
        changeCount: 0,
      });
    }
  }

  // Generate report
  log("\n============================");
  const report = generateReport(results);

  // Summary
  log("\n📋 Summary:");
  log(`   Total packages scanned: ${report.totalPackages}`);
  log(`   Packages needing updates: ${report.packagesWithChanges}`);
  log(`   Total dependency updates: ${report.totalChanges}`);

  if (checkOnly && report.packagesWithChanges > 0) {
    log("\n❌ Check failed - dependency misalignments found");
    process.exit(1);
  }

  if (dryRun && report.packagesWithChanges > 0) {
    log("\n⚠️ Dry run complete - run without --dry-run to apply changes");
  }

  if (!checkOnly && !dryRun && report.packagesWithChanges > 0) {
    log("\n✅ Changes applied successfully");
    log("\nNext steps:");
    log("   1. Run pnpm install to update lockfile");
    log("   2. Run pnpm build to verify builds");
    log("   3. Run pnpm test to verify functionality");
  }

  if (report.packagesWithChanges === 0) {
    log("\n✅ All dependencies are aligned");
  }

  const duration = Date.now() - startTime;
  log(`\n⏱️  Completed in ${duration}ms`);
}

// Error handling
process.on("unhandledRejection", (error) => {
  logError(`Unhandled rejection: ${error.message}`);
  process.exit(1);
});

// Run main
main().catch((error) => {
  logError(`Fatal error: ${error.message}`);
  process.exit(1);
});
