"use strict";
var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (const p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
let _a;
Object.defineProperty(exports, "__esModule", { value: true });
const test_1 = require("@playwright/test");
// Local Playwright config for running tests against an already-running dev server
exports.default = (0, test_1.defineConfig)({
    testDir: './',
    timeout: 30000,
    expect: { timeout: 5000 },
    reporter: [['list'], ['json', { outputFile: '../test-results/e2e-results-local.json' }]],
    use: {
        baseURL: (_a = process.env.PLAYWRIGHT_BASE_URL) !== null && _a !== void 0 ? _a : 'http://localhost:5173',
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
        actionTimeout: 10000,
        navigationTimeout: 30000,
    },
    projects: [
        { name: 'chromium', use: __assign(__assign({}, test_1.devices['Desktop Chrome']), { viewport: { width: 1280, height: 720 } }) },
    ],
    // Intentionally disable webServer so we don't start a second dev server when one is already running
    webServer: undefined,
    outputDir: 'test-results/e2e-local',
});
