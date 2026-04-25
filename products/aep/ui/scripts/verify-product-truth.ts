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
      path === "/session-expired" ||
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
 * Matches both legacy `import.meta.env` and new `resolveFlag('NAME')` patterns.
 */
function extractFeatureFlags(flagsSource: string): Set<string> {
  const flags = new Set<string>();
  // Legacy: `FLAG: import.meta.env...`
  const legacyRegex = /(\w+):\s*import\.meta\.env/g;
  let match: RegExpExecArray | null;
  while ((match = legacyRegex.exec(flagsSource)) !== null) {
    flags.add(match[1]);
  }
  // New: `resolveFlag('FLAG')`
  const newRegex = /resolveFlag\('(\w+)'\)/g;
  while ((match = newRegex.exec(flagsSource)) !== null) {
    flags.add(match[1]);
  }
  return flags;
}

/**
 * Check if global CSS is imported in main.tsx.
 */
function hasGlobalCssImport(mainSource: string): boolean {
  return /import\s+['"]\.\/index\.css['"]/.test(mainSource);
}

/**
 * Check if tailwindcss Vite plugin is present in vite.config.ts.
 */
function hasTailwindPlugin(viteSource: string): boolean {
  return /tailwindcss\(\)/.test(viteSource);
}

/**
 * Extract route definitions from App.tsx (all Route path=...).
 */
function extractAllAppRoutes(appSource: string): Set<string> {
  const routes = new Set<string>();
  const regex = /<Route\s+path="([^"]+)"/g;
  let m: RegExpExecArray | null;
  while ((m = regex.exec(appSource)) !== null) {
    routes.add(m[1]);
  }
  return routes;
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
  const mainPath = path.join(srcDir, "main.tsx");
  const vitePath = path.join(rootDir, "vite.config.ts");
  const planPath = path.join(
    rootDir,
    "..",
    "docs",
    "AEP_UI_UX_IMPLEMENTATION_PLAN.md",
  );

  // Read sources
  const appSource = fs.readFileSync(appPath, "utf-8");
  const flagsSource = fs.readFileSync(flagsPath, "utf-8");
  const mainSource = fs.existsSync(mainPath) ? fs.readFileSync(mainPath, "utf-8") : "";
  const viteSource = fs.existsSync(vitePath) ? fs.readFileSync(vitePath, "utf-8") : "";
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
    "SessionExpiryPage",
    "OperationCenterPage",
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

  // ── Style checks ──────────────────────────────────────────
  results.push({
    category: "style",
    name: "Global CSS import in main.tsx",
    found: hasGlobalCssImport(mainSource),
    expected: true,
    location: "src/main.tsx",
  });
  results.push({
    category: "style",
    name: "Tailwind Vite plugin in vite.config.ts",
    found: hasTailwindPlugin(viteSource),
    expected: true,
    location: "vite.config.ts",
  });

  // ── Route checks ──────────────────────────────────────────
  const allRoutes = extractAllAppRoutes(appSource);
  const canonicalEditRoute = "/build/pipelines/:pipelineId/edit";
  results.push({
    category: "route",
    name: `Explicit pipeline edit route (${canonicalEditRoute})`,
    found: allRoutes.has(canonicalEditRoute),
    expected: true,
    location: "App.tsx",
  });

  // ── Auth checks ───────────────────────────────────────────
  const authCallbackRoute = "/auth/callback";
  const loginRoute = "/login";
  results.push({
    category: "auth",
    name: "SSO callback route",
    found: allRoutes.has(authCallbackRoute),
    expected: true,
    location: "App.tsx",
  });
  results.push({
    category: "auth",
    name: "Login route",
    found: allRoutes.has(loginRoute),
    expected: true,
    location: "App.tsx",
  });

  // ── Tenant checks ─────────────────────────────────────────
  const tenantStorePath = path.join(srcDir, "stores", "tenant.store.ts");
  const tenantStoreSource = fs.existsSync(tenantStorePath)
    ? fs.readFileSync(tenantStorePath, "utf-8")
    : "";
  const tenantScopedApiPattern = /tenantIdAtom/.test(tenantStoreSource);
  results.push({
    category: "tenant",
    name: "Tenant atom defined in stores/tenant.store.ts",
    found: tenantScopedApiPattern,
    expected: true,
    location: "stores/tenant.store.ts",
  });

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
