#!/usr/bin/env node
// Headless smoke test using Playwright (CommonJS entry for ESM package.json)
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
    const context = await browser.newContext({ serviceWorkers: 'allow' });

    // Preflight: ensure the MSW service worker is registered and activated.

    const preflightPage = await context.newPage();

    // Capture console and page errors during preflight so we can see
    // MSW startup logs and any exceptions thrown while `setupMocks()` runs.
    const preflightLogs = [];
    const preflightErrors = [];
    preflightPage.on('console', (msg) => preflightLogs.push({ type: msg.type(), text: msg.text() }));
    preflightPage.on('pageerror', (err) => preflightErrors.push({ message: err.message, stack: err.stack }));

    try {
        const preflightRequests = [];
        const preflightFailedRequests = [];
        await preflightPage.goto(BASE + '/', { waitUntil: 'domcontentloaded', timeout: 15000 });

        preflightPage.on('request', (req) => preflightRequests.push({ url: req.url(), method: req.method() }));
        preflightPage.on('requestfailed', (req) => preflightFailedRequests.push({ url: req.url(), method: req.method(), failure: req.failure() }));
        const registrationResult = await preflightPage.evaluate(async () => {
            try {
                if (!('serviceWorker' in navigator)) return { ok: false, reason: 'no-service-worker' };

                const reg = await navigator.serviceWorker.register('/mockServiceWorker.js');
                await navigator.serviceWorker.ready;

                const waitForMessage = (timeoutMs = 2000) =>
                    new Promise((resolve) => {
                        let resolved = false;
                        const handler = (ev) => {
                            try {
                                const data = ev.data || {};
                                if (data && data.type === 'INTEGRITY_CHECK_RESPONSE') {
                                    resolved = true;
                                    navigator.serviceWorker.removeEventListener('message', handler);
                                    resolve({ ok: true, payload: data.payload });
                                }
                            } catch (e) {
                                // ignore
                            }
                        };

                        navigator.serviceWorker.addEventListener('message', handler);
                        try {
                            if (reg.active) reg.active.postMessage('INTEGRITY_CHECK_REQUEST');
                        } catch (e) {
                            // ignore
                        }

                        setTimeout(() => {
                            if (!resolved) {
                                navigator.serviceWorker.removeEventListener('message', handler);
                                resolve({ ok: false, reason: 'timeout' });
                            }
                        }, timeoutMs);
                    });

                try {
                    if (reg.active) reg.active.postMessage('MOCK_ACTIVATE');
                } catch (e) {
                    // ignore
                }

                const check = await waitForMessage(1500);
                if (!check.ok) return { ok: true, note: 'registered-no-integrity' };
                return { ok: true, integrity: check.payload };
            } catch (err) {
                return { ok: false, reason: String(err) };
            }
        });

        if (!registrationResult.ok) {
            console.warn('MSW preflight registration failed:', registrationResult.reason);
        }

        // Wait for the app to call `setupMocks()` which sets `window.__MSW_ACTIVE__ = true`.
        // This reduces the race window where the service worker file is registered
        // but the page's MSW client hasn't started yet. Increase timeout for
        // diagnostics and collect any start error the page may have set.
        try {
            const mswReady = await preflightPage.evaluate(() => {
                return new Promise((resolve) => {
                    try {
                        if (window.__MSW_ACTIVE__) return resolve(true);
                    } catch (e) {
                        // ignore
                    }
                    const start = Date.now();
                    const interval = setInterval(() => {
                        try {
                            if (window.__MSW_ACTIVE__) {
                                clearInterval(interval);
                                return resolve(true);
                            }
                        } catch (e) {
                            // ignore
                        }
                        if (Date.now() - start > 5000) {
                            clearInterval(interval);
                            return resolve(false);
                        }
                    }, 100);
                });
            });

            if (!mswReady) {
                console.warn('MSW did not signal ready via window.__MSW_ACTIVE__');
            }

            // Collect any diagnostic flags set by the page / setupMocks
            const diag = await preflightPage.evaluate(() => {
                return {
                    mswActive: !!window.__MSW_ACTIVE__,
                    mswStartError: window.__MSW_START_ERROR__ || null,
                    mswStartTime: window.__MSW_START_TIME__ || null,
                    mswSkipped: window.__MSW_SKIPPED__ || null,
                    runtimeEnv: window.__MSW_RUNTIME_ENV__ || null,
                };
            });
            // Print any preflight console logs we captured so they appear in CI output
            if (preflightLogs.length) {
                console.debug('Preflight console logs:');
                for (const l of preflightLogs.slice(-50)) console.debug(`${l.type}: ${l.text}`);
            }
            if (preflightErrors.length) {
                console.debug('Preflight page errors:');
                for (const e of preflightErrors.slice(-20)) console.debug(e.message, e.stack);
            }
            if (diag.mswSkipped) {
                console.warn('MSW skipped (page):', diag.mswSkipped);
            }
            if (diag.runtimeEnv) {
                console.debug('MSW runtime env (page):', diag.runtimeEnv);
            }
            if (diag.mswStartError) {
                console.warn('MSW start error (page):', diag.mswStartError);
            }
            if (diag.mswStartTime) {
                console.debug('MSW start time (page):', diag.mswStartTime);
            }

            // Perform a small set of contract checks against the registered handlers
            // to validate response shapes that the UI expects. Fail early if shapes
            // are invalid to prevent downstream runtime exceptions.
            try {
                const contractFailures = await preflightPage.evaluate(async () => {
                    const failures = [];

                    async function safeFetch(path) {
                        try {
                            const r = await fetch(path, { credentials: 'same-origin' });
                            const json = await r.json().catch(() => null);
                            return { status: r.status, body: json };
                        } catch (e) {
                            return { status: 0, body: null };
                        }
                    }

                    // Models: expect an array of objects with id, name, status
                    const models = await safeFetch('/api/v1/models');
                    if (!Array.isArray(models.body)) {
                        failures.push({ path: '/api/v1/models', reason: 'expected array' });
                    } else {
                        for (const m of models.body.slice(0, 5)) {
                            if (!m || typeof m.id !== 'string' || typeof m.name !== 'string') {
                                failures.push({ path: '/api/v1/models', reason: 'invalid item shape' });
                                break;
                            }
                        }
                    }

                    // Health/metrics: expect object with tenantId and healthy boolean
                    const health = await safeFetch('/api/v1/tenants/default/metrics/health');
                    if (!health.body || typeof health.body.tenantId !== 'string' || typeof health.body.healthy !== 'boolean') {
                        failures.push({ path: '/api/v1/tenants/default/metrics/health', reason: 'unexpected shape' });
                    }

                    // Workflows: expect array with id, name, createdAt
                    const workflows = await safeFetch('/api/v1/tenants/default/workflows');
                    if (!Array.isArray(workflows.body)) {
                        failures.push({ path: '/api/v1/tenants/default/workflows', reason: 'expected array' });
                    } else {
                        for (const w of workflows.body.slice(0, 5)) {
                            const ok = w && typeof w.id === 'string' && typeof w.name === 'string' && typeof w.createdAt === 'string';
                            if (!ok) { failures.push({ path: '/api/v1/tenants/default/workflows', reason: 'invalid item shape' }); break; }
                        }
                    }

                    return failures;
                });

                if (contractFailures && contractFailures.length) {
                    console.error('Handler contract failures detected:', contractFailures);
                    console.error('Failing contracts will cause UI runtime errors; please fix mocks.');
                    await preflightPage.close();
                    await browser.close();
                    process.exit(3);
                }
            } catch (e) {
                // don't abort preflight on contract-check errors; just log
                console.warn('Contract check failed to run in preflight:', e && e.message ? e.message : e);
            }
        } catch (e) {
            // ignore evaluation errors
        }
    } catch (e) {
        console.warn('MSW preflight navigation failed:', e && e.message ? e.message : e);
    }

    const page = preflightPage;
    const logs = [];
    const errors = [];
    const failedResponses = [];

    // No environment shims: rely on MSW handlers and app fallbacks.

    page.on('console', (msg) => {
        logs.push({ type: msg.type(), text: msg.text() });
    });

    page.on('pageerror', (err) => {
        errors.push({ message: err.message, stack: err.stack });
    });

    page.on('response', (resp) => {
        try {
            const status = resp.status();
            const url = resp.url();
            if (status >= 400 && url.includes('/api/v1')) {
                failedResponses.push({ url, status });
            }
        } catch (e) {
            // ignore
        }
    });

    // No Playwright route fallbacks installed; rely on MSW handlers only.

    const results = [];

    for (const route of ROUTES) {
        const url = BASE + route;
        let status = null;

        const snapshotStart = logs.length;

        try {
            // Use a lighter navigation wait to avoid background/persistent
            // network activity (HMR, websockets) blocking 'networkidle'.
            const resp = await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 25000 });
            status = resp && resp.status ? resp.status() : null;

            // Wait for the app root to be mounted to give the page a chance
            // to render React content. If it doesn't appear within a short
            // timeout, fallback to a small pause so tests can still proceed.
            try {
                await page.waitForSelector('#root', { timeout: 5000 });
            } catch (e) {
                // If '#root' not present, fallback to brief sleep to allow rendering
                await page.waitForTimeout(1200);
            }
        } catch (e) {
            errors.push({ message: e.message, stack: e.stack });
        }

        const recent = logs.slice(snapshotStart);
        const consoleErrors = recent.filter((l) => {
            if (l.type === 'error') return true;
            if (l.type === 'warning') {
                if (typeof l.text === 'string' && l.text.includes('React Router Future Flag Warning')) return false;
                return true;
            }
            return false;
        });

        results.push({ route, url, status, consoleErrors, pageErrors: errors.slice(), failedResponses: failedResponses.slice(), allConsole: recent.slice(-20) });
    }

    await page.close();
    await browser.close();

    const summary = { base: BASE, results };
    console.log(JSON.stringify(summary, null, 2));

    const hasProblems = results.some((r) => (r.pageErrors && r.pageErrors.length) || (r.consoleErrors && r.consoleErrors.length));
    process.exit(hasProblems ? 2 : 0);
})();
