import { defineConfig, devices } from '@playwright/test';
import { fileURLToPath } from 'url';
import path from 'path';
import { readFile } from 'fs/promises';

// Get the current module's directory name in ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Paths to the extensions for each browser
const extensionPath = {
  chrome: path.join(__dirname, 'dist/chrome'),
  firefox: path.join(__dirname, 'dist/firefox'),
  edge: path.join(__dirname, 'dist/edge'),
};

// Use PW_ALLOW_EXTENSION_HEADLESS to opt into headless mode for extension tests.
// By default run with headless=false so extensions and service workers load reliably.
const isHeadless = process.env.PW_ALLOW_EXTENSION_HEADLESS === 'true';
const isCoverageEnabled = process.env.PW_ENABLE_COVERAGE === 'true';

const commonLaunchOptions = {
  headless: isHeadless,
  devtools: !isHeadless,
  slowMo: process.env.CI ? 0 : 50,
  args: [],
};

// Browser-specific launch arguments
const browserArgs = {
  chrome: [
    `--disable-extensions-except=${extensionPath.chrome}`,
    `--load-extension=${extensionPath.chrome}`,
  ],
  firefox: [],
  edge: [
    `--disable-extensions-except=${extensionPath.edge}`,
    `--load-extension=${extensionPath.edge}`,
    '--no-sandbox',
    '--disable-setuid-sandbox',
  ],
};

// Create the config asynchronously
async function createConfig() {
  // Read the extension manifest to get the extension ID
  const chromeManifestPath = path.join(extensionPath.chrome, 'manifest.json');
  const firefoxManifestPath = path.join(extensionPath.firefox, 'manifest.json');
  const chromeManifest = JSON.parse(await readFile(chromeManifestPath, 'utf-8'));
  const firefoxManifest = JSON.parse(await readFile(firefoxManifestPath, 'utf-8'));

  const extensionId = {
    chrome: chromeManifest.key || 'dcmaar-extension@example.com',
    firefox: firefoxManifest.browser_specific_settings?.gecko?.id || 'dcmaar-extension@example.com',
  };

  const baseContextOptions = {
    ...commonLaunchOptions,
    viewport: { width: 1280, height: 800 },
    bypassCSP: true,
    ignoreHTTPSErrors: true,
    permissions: [] as string[],
  } as const;

  return defineConfig({
    // Run Playwright E2E tests from the organized e2e directory. We place
    // wrapper files in `tests/e2e` so Playwright discovers E2E suites.
    testDir: './tests/e2e',
    // Match common spec/test file patterns
    testMatch: ['**/*.spec.ts', '**/*.test.ts'],
    fullyParallel: false,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    // Use a single worker to make extension tests deterministic
    workers: 1,
    reporter: [
      ['list'],
      // Disable auto-opening the HTML report to avoid interrupting developer flow
      ['html', { outputFolder: 'playwright-report', open: 'never' }],
      ['junit', { outputFile: 'test-results/junit/results.xml' }],
    ],
    coverage: {
      enabled: isCoverageEnabled,
      provider: 'v8',
      outputDir: 'coverage/playwright',
      include: ['dist/**/*.js'],
      exclude: ['**/*.spec.*', '**/*.test.*', 'tests/**', 'playwright-report/**'],
    },
    use: {
      baseURL: 'http://localhost:3000',
      trace: 'on-first-retry',
      screenshot: 'only-on-failure',
      video: 'on-first-retry',
      // Configure the extension to be loaded
      launchOptions: {
        ...commonLaunchOptions,
        args: [...(commonLaunchOptions.args || [])],
      },
      // Configure the browser context to load the extension
      contextOptions: baseContextOptions,
    },
    projects: [
      {
        name: 'chromium',
        use: {
          ...devices['Desktop Chrome'],
          launchOptions: {
            ...commonLaunchOptions,
            args: [...commonLaunchOptions.args, ...browserArgs.chrome],
          },
          contextOptions: {
            ...baseContextOptions,
            permissions: ['clipboard-read', 'clipboard-write'],
          },
        },
      },
      {
        name: 'firefox',
        use: {
          ...devices['Desktop Firefox'],
          launchOptions: {
            ...commonLaunchOptions,
            firefoxUserPrefs: {
              'extensions.webextensions.uuids': JSON.stringify({
                [extensionId.firefox]: '{dcmaar-extension@example.com}',
              }),
              'extensions.experiments.enabled': true,
              'xpinstall.signatures.required': false,
              'extensions.allowPrivateBrowsingByDefault': true,
            },
            args: [...commonLaunchOptions.args, ...browserArgs.firefox],
          },
          contextOptions: {
            ...baseContextOptions,
            permissions: [],
          },
        },
      },
      {
        name: 'edge',
        use: {
          ...devices['Desktop Edge'],
          launchOptions: {
            ...commonLaunchOptions,
            channel: 'msedge',
            args: [...commonLaunchOptions.args, ...browserArgs.edge],
          },
          contextOptions: {
            ...baseContextOptions,
            permissions: ['clipboard-read', 'clipboard-write'],
          },
        },
      },
    ],
  });
}

// Export the config as a promise
export default createConfig();
