import { defineConfig, devices } from "@playwright/test";
import path from "node:path";
import { fileURLToPath } from "node:url";

const configDir = path.dirname(fileURLToPath(import.meta.url));
const tutorputorRoot = path.resolve(configDir, "..");
const webAppDir = path.resolve(tutorputorRoot, "apps/tutorputor-web");
const adminAppDir = path.resolve(tutorputorRoot, "apps/tutorputor-admin");
const gatewayAppDir = path.resolve(tutorputorRoot, "apps/api-gateway");
const platformServiceDir = path.resolve(
  tutorputorRoot,
  "services/tutorputor-platform",
);
const shouldSkipWebServer = process.env.PLAYWRIGHT_SKIP_WEBSERVER === "true";
const trustedProxyAuthEnv = {
  ...process.env,
  TRUST_PROXY_AUTH_HEADERS: "true",
  TRUST_PROXY_AUTH_SHARED_SECRET: "tutorputor-internal-dev-proxy-secret",
};
const adminBypassEnv = {
  ...process.env,
  VITE_DEV_AUTH_BYPASS: "true",
  VITE_TRUST_PROXY_AUTH_SHARED_SECRET: "tutorputor-internal-dev-proxy-secret",
};

/**
 * Canonical Playwright configuration for TutorPutor product validation.
 *
 * This is the single supported cross-product topology for learner + admin +
 * gateway + platform end-to-end verification.
 */
export default defineConfig({
  testDir: "./",
  testMatch: [
    "LearnerJourney.spec.ts",
    "ContentStudio.spec.ts",
    "StudentOnboarding.spec.ts",
    "EducatorWorkflow.spec.ts",
    "smoke.spec.ts",
  ],
  timeout: 30000,
  expect: {
    timeout: 5000,
  },
  retries: process.env.CI ? 2 : 0,
  reporter: [
    ["html", { outputFolder: "playwright-report", open: "never" }],
    ["json", { outputFile: "test-results.json" }],
    ["junit", { outputFile: "test-results.xml" }],
    ["list"],
  ],

  globalSetup: "./global-setup.ts",
  globalTeardown: "./global-teardown.ts",

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  webServer: shouldSkipWebServer
    ? undefined
    : [
        {
          command: "pnpm dev",
          env: trustedProxyAuthEnv,
          cwd: gatewayAppDir,
          url: "http://127.0.0.1:3200/health",
          reuseExistingServer: !process.env.CI,
          timeout: 120000,
        },
        {
          command: "pnpm dev",
          env: trustedProxyAuthEnv,
          cwd: platformServiceDir,
          url: "http://127.0.0.1:7105/health",
          reuseExistingServer: !process.env.CI,
          timeout: 120000,
        },
        {
          command: "pnpm dev",
          env: process.env,
          cwd: webAppDir,
          url: "http://127.0.0.1:3201/login",
          reuseExistingServer: !process.env.CI,
          timeout: 120000,
        },
        {
          command: "pnpm dev",
          env: adminBypassEnv,
          cwd: adminAppDir,
          url: "http://127.0.0.1:3202/authoring",
          reuseExistingServer: !process.env.CI,
          timeout: 120000,
        },
      ],

  use: {
    baseURL: process.env.BASE_URL || "http://127.0.0.1:3201",
    apiTimeout: 10000,
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    trace: "retain-on-failure",
  },
});
