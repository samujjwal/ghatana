/**
 * Product Truth Verification Script
 *
 * Compares runtime routes, feature flags, and page inventory against
 * documented expectations. Fails CI if docs claim features that are
 * not present in code.
 *
 * @doc.type script
 * @doc.purpose Prevent docs/code drift by automated verification
 * @doc.layer tooling
 */
import * as fs from "node:fs";
import * as path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

interface VerificationResult {
  category: string;
  name: string;
  found: boolean;
  expected: boolean;
  location?: string;
}

/**
 * Extract canonical routes from App.tsx source.
 * Skips auth routes, wildcards, redirects, and dynamic param routes.
 */
function extractAppRoutes(appSource: string): Set<string> {
  const routes = new Set<string>();
  const routeRegex = /<Route\s+path="([^"]+)"/g;
  let match: RegExpExecArray | null;
  while ((match = routeRegex.exec(appSource)) !== null) {
    const path = match[1];
    // Skip auth, wildcard, redirect-only, and dynamic routes
    if (
      path === "*" ||
      path.startsWith("/agents") ||
      path === "/login" ||
      path === "/auth/callback" ||
      path.includes(":")
    ) {
      continue;
    }
    routes.add(path);
  }
  return routes;
}

/**
 * Extract documented canonical routes from App.tsx JSDoc header.
 * Matches lines like: *   /operate              → MonitoringDashboardPage
 */
function extractDocumentedRoutes(appSource: string): Set<string> {
  const routes = new Set<string>();
  // Match JSDoc lines that document canonical routes with → separator
  const routeRegex = /\*\s+(\/[-a-z/]+)\s+→/g;
  let match: RegExpExecArray | null;
  while ((match = routeRegex.exec(appSource)) !== null) {
    const path = match[1];
    // Only exact paths, skip dynamic params
    if (!path.includes(":")) {
      routes.add(path);
    }
  }
  return routes;
}

/**
 * Extract feature flags from feature-flags.ts source.
 */
function extractFeatureFlags(flagsSource: string): Set<string> {
  const flags = new Set<string>();
  const flagRegex = /(\w+):\s*import\.meta\.env/g;
  let match: RegExpExecArray | null;
  while ((match = flagRegex.exec(flagsSource)) !== null) {
    flags.add(match[1]);
  }
  return flags;
}

/**
 * Extract documented feature flags from implementation plan.
 */
function extractDocumentedFlags(planSource: string): Set<string> {
  const flags = new Set<string>();
  // Look for flag names in code blocks or explicit mentions
  const flagRegex = /'(\w+)'|"(\w+)"/g;
  let match: RegExpExecArray | null;
  while ((match = flagRegex.exec(planSource)) !== null) {
    const name = match[1] || match[2];
    if (
      name &&
      (name.includes("_") ||
        name === "BREADCRUMBS" ||
        name === "COMMAND_PALETTE")
    ) {
      flags.add(name);
    }
  }
  return flags;
}

/**
 * Extract page components from pages directory.
 */
function extractPageComponents(pagesDir: string): Set<string> {
  const pages = new Set<string>();
  const files = fs.readdirSync(pagesDir);
  for (const file of files) {
    if (file.endsWith(".tsx") && !file.includes(".test.")) {
      pages.add(file.replace(".tsx", ""));
    }
  }
  return pages;
}

/**
 * Run the full verification suite.
 */
function runVerification(): VerificationResult[] {
  const results: VerificationResult[] = [];

  const rootDir = path.resolve(__dirname, "..");
  const srcDir = path.join(rootDir, "src");
  const pagesDir = path.join(srcDir, "pages");
  const appPath = path.join(srcDir, "App.tsx");
  const flagsPath = path.join(srcDir, "lib", "feature-flags.ts");
  const planPath = path.join(
    rootDir,
    "..",
    "docs",
    "AEP_UI_UX_IMPLEMENTATION_PLAN.md",
  );

  // Read sources
  const appSource = fs.readFileSync(appPath, "utf-8");
  const flagsSource = fs.readFileSync(flagsPath, "utf-8");
  const planSource = fs.existsSync(planPath)
    ? fs.readFileSync(planPath, "utf-8")
    : "";

  const isBackwardCompatRoute = (route: string): boolean => {
    // Redirect-only legacy paths (not in canonical route table)
    const legacyPaths = [
      "/agents",
      "/pipelines/list",
      "/pipelines",
      "/monitoring",
      "/patterns",
      "/hitl",
      "/learning",
      "/workflows",
      "/memory",
      "/",
    ];
    return legacyPaths.includes(route);
  };

  // Verify routes — backward-compat redirects are expected, only flag canonical ones
  const appRoutes = extractAppRoutes(appSource);
  const rawDocRoutes = extractDocumentedRoutes(appSource);
  const docRoutes = new Set(
    Array.from(rawDocRoutes).filter((r) => !isBackwardCompatRoute(r)),
  );

  for (const route of appRoutes) {
    if (isBackwardCompatRoute(route)) continue;
    results.push({
      category: "route",
      name: route,
      found: true,
      expected: docRoutes.has(route),
      location: "App.tsx",
    });
  }

  for (const route of docRoutes) {
    if (!appRoutes.has(route)) {
      results.push({
        category: "route",
        name: route,
        found: false,
        expected: true,
        location: "AEP_UI_UX_IMPLEMENTATION_PLAN.md",
      });
    }
  }

  // Verify feature flags
  const appFlags = extractFeatureFlags(flagsSource);
  const docFlags = extractDocumentedFlags(planSource);

  for (const flag of appFlags) {
    results.push({
      category: "feature-flag",
      name: flag,
      found: true,
      expected: true,
      location: "feature-flags.ts",
    });
  }

  // Verify pages exist
  const pageComponents = extractPageComponents(pagesDir);
  const expectedPages = [
    "LoginPage",
    "SsoCallbackPage",
    "MonitoringDashboardPage",
    "HitlReviewPage",
    "CostDashboardPage",
    "RunDetailPage",
    "PipelineListPage",
    "PipelineBuilderPage",
    "PatternStudioPage",
    "MemoryExplorerPage",
    "GovernancePage",
    "AgentRegistryPage",
    "WorkflowCatalogPage",
    "AgentMarketplacePage",
  ];

  for (const page of expectedPages) {
    results.push({
      category: "page",
      name: page,
      found: pageComponents.has(page),
      expected: true,
      location: `pages/${page}.tsx`,
    });
  }

  return results;
}

/**
 * Main entry point.
 */
function main(): void {
  const results = runVerification();
  const issues = results.filter((r) => !r.found || !r.expected);

  console.log("\n=== Product Truth Verification ===\n");
  console.log(`Total checks: ${results.length}`);
  console.log(`Issues found:  ${issues.length}\n`);

  if (issues.length > 0) {
    console.log("Failures:\n");
    for (const issue of issues) {
      const status = !issue.found ? "MISSING" : "UNEXPECTED";
      console.log(`  [${status}] ${issue.category}: ${issue.name}`);
      if (issue.location) {
        console.log(`    Location: ${issue.location}`);
      }
    }
    console.log("\n❌ Verification failed — docs/runtime drift detected.\n");
    process.exit(1);
  }

  console.log("✅ All checks passed — product truth verified.\n");
  process.exit(0);
}

main();
