import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  use: {
    baseURL: 'http://127.0.0.1:4180',
    headless: true,
  },
  webServer: {
    command: 'pnpm dev --host 127.0.0.1 --port 4180',
    port: 4180,
    reuseExistingServer: true,
    timeout: 120000,
  },
});