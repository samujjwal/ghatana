#!/usr/bin/env node
// Headless smoke test using Playwright
// Navigates a set of routes and collects console messages and page errors.

const { chromium } = require('playwright');

const BASE = process.env.BASE_URL || 'http://localhost:3000';
const ROUTES = [
    '/',
    '/dashboard',
    '/departments',
    '/workflows',
    '/hitl',
    '/reports',
    '/security',
    '/models',
    '/settings',
    '/help',
    '/export',
    '/realtime-monitor',
    '/ml-observatory',
    '/automation',
];

(async () => {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext();
    const results = [];

    for (const route of ROUTES) {
        const url = BASE + route;
        const page = await context.newPage();
        const logs = [];
        const errors = [];

        page.on('console', (msg) => {
            logs.push({ type: msg.type(), text: msg.text() });
        });

        page.on('pageerror', (err) => {
            errors.push({ message: err.message, stack: err.stack });
        });

        let status = null;
        try {
            const resp = await page.goto(url, { waitUntil: 'networkidle', timeout: 20000 });
            status = resp && resp.status ? resp.status() : null;
            // allow client code to hydrate and run
            await page.waitForTimeout(1000);
        } catch (e) {
            errors.push({ message: e.message, stack: e.stack });
        }

        // collect console errors only (error and warning)
        const consoleErrors = logs.filter((l) => l.type === 'error' || l.type === 'warning');

        results.push({ route, url, status, consoleErrors, pageErrors: errors, allConsole: logs.slice(-20) });

        await page.close();
    }

    await browser.close();

    // Print summary JSON
    const summary = { base: BASE, results };
    console.log(JSON.stringify(summary, null, 2));

    // Exit with non-zero if any pageErrors or consoleErrors present
    const hasProblems = results.some((r) => (r.pageErrors && r.pageErrors.length) || (r.consoleErrors && r.consoleErrors.length));
    process.exit(hasProblems ? 2 : 0);
})();
