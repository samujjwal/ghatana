"use strict";
const __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P((resolve) => { resolve(value); }); }
    return new (P || (P = Promise))((resolve, reject) => {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
const __generator = (this && this.__generator) || function (thisArg, body) {
    let _ = { label: 0, sent() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g = Object.create((typeof Iterator === "function" ? Iterator : Object).prototype);
    return g.next = verb(0), g["throw"] = verb(1), g["return"] = verb(2), typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = globalSetup;
const test_1 = require("@playwright/test");
const fs_1 = require("fs");
// import local node seed helper
const seed_mocks_1 = require("./seed-mocks");
// Global setup for Playwright tests. This script will run once before the test suite
// when PLAYWRIGHT_ENABLE_CANVAS=true. It attempts to seed demo data required by
// heavy canvas/diagram e2e specs by navigating to the app and invoking the UI
// "seed" controls. If those are not available it falls back to injecting seed
// data via a window hook if the app exposes one.
function seedCanvas(baseURL) {
    return __awaiter(this, void 0, void 0, function () {
        // Helper: verify the dev-server/MSW/Apollo stack sees the seeded project via GraphQL
        function verifySeededGraphQL() {
            return __awaiter(this, void 0, void 0, function () {
                let graphqlUrl, query, res, json, data, projects, found, e_5;
                return __generator(this, (_a) => {
                    switch (_a.label) {
                        case 0:
                            _a.trys.push([0, 3, , 4]);
                            graphqlUrl = `${baseURL.replace(/\/$/, '')  }/graphql`;
                            query = "query ListProjects { projects { id workspaceId name } }";
                            return [4 /*yield*/, page.request.post(graphqlUrl, { data: JSON.stringify({ query }), headers: { 'content-type': 'application/json' }, timeout: 5000 })];
                        case 1:
                            res = _a.sent();
                            if (!res)
                                return [2 /*return*/, false];
                            return [4 /*yield*/, res.json().catch(() => { return null; })];
                        case 2:
                            json = _a.sent();
                            if (!json)
                                return [2 /*return*/, false];
                            data = json.data || json;
                            projects = data.projects || (Array.isArray(json) ? json : null);
                            if (Array.isArray(projects)) {
                                found = projects.find((p) => { return p && p.id === 'proj-1'; });
                                if (found) {
                                    console.log('[global-setup] GraphQL verification: found seeded project proj-1');
                                    return [2 /*return*/, true];
                                }
                            }
                            return [2 /*return*/, false];
                        case 3:
                            e_5 = _a.sent();
                            return [2 /*return*/, false];
                        case 4: return [2 /*return*/];
                    }
                });
            });
        }
        // Helper to attempt navigation + selector wait with retries
        function tryWarmRoute(url_1, selector_1) {
            return __awaiter(this, arguments, void 0, function (url, selector, attempts, waitForSelectorTimeout) {
                let lastErr, selectors, i, e_6, _i, selectors_1, s, selErr_1, err_6, errMsg, e_7, screenshotPath, htmlPath, html, e_8, logsPath;
                if (attempts === void 0) { attempts = 5; }
                if (waitForSelectorTimeout === void 0) { waitForSelectorTimeout = 45000; }
                return __generator(this, (_a) => {
                    switch (_a.label) {
                        case 0:
                            lastErr = null;
                            selectors = Array.isArray(selector) ? selector : [selector];
                            i = 0;
                            _a.label = 1;
                        case 1:
                            if (!(i < attempts)) return [3 /*break*/, 21];
                            _a.label = 2;
                        case 2:
                            _a.trys.push([2, 14, , 20]);
                            // Give more time for slow CI/dev machines
                            return [4 /*yield*/, page.goto(url, { waitUntil: 'networkidle', timeout: 120000 })];
                        case 3:
                            // Give more time for slow CI/dev machines
                            _a.sent();
                            _a.label = 4;
                        case 4:
                            _a.trys.push([4, 6, , 7]);
                            return [4 /*yield*/, page.waitForResponse((r) => { return r.url().includes('/graphql') && r.status() === 200; }, { timeout: 15000 })];
                        case 5:
                            _a.sent();
                            console.log('[global-setup] observed graphql response while warming', url);
                            return [3 /*break*/, 7];
                        case 6:
                            e_6 = _a.sent();
                            return [3 /*break*/, 7];
                        case 7:
                            _i = 0, selectors_1 = selectors;
                            _a.label = 8;
                        case 8:
                            if (!(_i < selectors_1.length)) return [3 /*break*/, 13];
                            s = selectors_1[_i];
                            _a.label = 9;
                        case 9:
                            _a.trys.push([9, 11, , 12]);
                            return [4 /*yield*/, page.waitForSelector(s, { timeout: waitForSelectorTimeout })];
                        case 10:
                            _a.sent();
                            console.log('[global-setup] warmed route (selector matched) ', url, s);
                            return [2 /*return*/, true];
                        case 11:
                            selErr_1 = _a.sent();
                            return [3 /*break*/, 12];
                        case 12:
                            _i++;
                            return [3 /*break*/, 8];
                        case 13: throw new Error("no selector matched after navigation to ".concat(url));
                        case 14:
                            err_6 = _a.sent();
                            lastErr = err_6;
                            errMsg = err_6 && err_6.message ? err_6.message : String(err_6);
                            console.warn("[global-setup] attempt ".concat(i + 1, "/").concat(attempts, " failed for ").concat(url, ":"), errMsg);
                            _a.label = 15;
                        case 15:
                            _a.trys.push([15, 17, , 18]);
                            // Try a reload before the next attempt to trigger another hydration
                            return [4 /*yield*/, page.reload({ waitUntil: 'networkidle', timeout: 45000 }).catch(() => { })];
                        case 16:
                            // Try a reload before the next attempt to trigger another hydration
                            _a.sent();
                            return [3 /*break*/, 18];
                        case 17:
                            e_7 = _a.sent();
                            return [3 /*break*/, 18];
                        case 18: 
                        // small backoff
                        return [4 /*yield*/, page.waitForTimeout(1000 + i * 1000)];
                        case 19:
                            // small backoff
                            _a.sent();
                            return [3 /*break*/, 20];
                        case 20:
                            i++;
                            return [3 /*break*/, 1];
                        case 21:
                            console.warn('[global-setup] failed to warm route after retries', url, lastErr);
                            _a.label = 22;
                        case 22:
                            _a.trys.push([22, 25, , 26]);
                            screenshotPath = './e2e/global-setup-failure.png';
                            return [4 /*yield*/, page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => { })];
                        case 23:
                            _a.sent();
                            console.warn('[global-setup] wrote diagnostic screenshot to', screenshotPath);
                            htmlPath = './e2e/global-setup-failure.html';
                            return [4 /*yield*/, page.content()];
                        case 24:
                            html = _a.sent();
                            try {
                                fs_1.default.writeFileSync(htmlPath, html, 'utf8');
                            }
                            catch (e) { }
                            console.warn('[global-setup] wrote diagnostic html to', htmlPath);
                            return [3 /*break*/, 26];
                        case 25:
                            e_8 = _a.sent();
                            return [3 /*break*/, 26];
                        case 26:
                            // also write console and page errors to a JSON file for easier inspection
                            try {
                                logsPath = './e2e/global-setup-logs.json';
                                fs_1.default.writeFileSync(logsPath, JSON.stringify({ console: consoleMessages_1, pageErrors: pageErrors_1 }, null, 2), 'utf8');
                                console.warn('[global-setup] wrote diagnostic logs to', logsPath);
                            }
                            catch (e) {
                                // ignore
                            }
                            return [2 /*return*/, false];
                    }
                });
            });
        }
        var browser, context, page, pocSnapshot, diagramNames, diagramJson, seedPayload, seedUrl, indexUrl, e_1, innerErr_1, e_2, storagePath, e_3, consoleMessages_1, pageErrors_1, e_4, pocUrl, pocSelectors, warmed, graphqlOk, err_1, canvasUrl, canvasSelectors, warmedCanvas, graphqlOk, err_2, seededRoute, err_3, seedJsonUrl, res, text, err_4, err_5;
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: return [4 /*yield*/, test_1.chromium.launch()];
                case 1:
                    browser = _a.sent();
                    return [4 /*yield*/, browser.newContext()];
                case 2:
                    context = _a.sent();
                    return [4 /*yield*/, context.newPage()];
                case 3:
                    page = _a.sent();
                    _a.label = 4;
                case 4:
                    _a.trys.push([4, 47, 48, 51]);
                    console.log('[global-setup] Preparing localStorage seeds for canvas/diagram');
                    pocSnapshot = {
                        version: '1.0.0',
                        updatedAt: new Date().toISOString(),
                        checksum: 'c1',
                        canvas: {
                            elements: [
                                {
                                    id: 'node-1',
                                    kind: 'node',
                                    type: 'component',
                                    position: { x: 300, y: 200 },
                                    size: { width: 150, height: 80 },
                                    data: { label: 'Frontend App' },
                                    style: {},
                                },
                                {
                                    id: 'node-2',
                                    kind: 'node',
                                    type: 'api',
                                    position: { x: 600, y: 200 },
                                    size: { width: 150, height: 80 },
                                    data: { label: 'API Gateway' },
                                    style: {},
                                },
                                {
                                    id: 'node-3',
                                    kind: 'node',
                                    type: 'data',
                                    position: { x: 900, y: 200 },
                                    size: { width: 150, height: 80 },
                                    data: { label: 'Database' },
                                    style: {},
                                },
                            ],
                            connections: [
                                { id: 'conn-1', source: 'node-1', target: 'node-2', type: 'default', data: {} },
                            ],
                            viewport: { x: 0, y: 0, zoom: 1 },
                            metadata: { title: 'GlobalSeed', description: 'Seed from global-setup' },
                        },
                        viewport: { x: 0, y: 0, zoom: 1 },
                    };
                    diagramNames = ['Default', 'Demo', 'demo-project', 'project-demo', 'Main'];
                    diagramJson = JSON.stringify({ nodes: [
                            { id: 'n1', data: { label: 'Frontend App' }, position: { x: 200, y: 200 } },
                            { id: 'n2', data: { label: 'API Gateway' }, position: { x: 400, y: 200 } },
                        ], edges: [{ id: 'e1', source: 'n1', target: 'n2' }] });
                    // Open base URL to ensure same-origin localStorage access
                    return [4 /*yield*/, page.goto(baseURL, { waitUntil: 'domcontentloaded', timeout: 30000 })];
                case 5:
                    // Open base URL to ensure same-origin localStorage access
                    _a.sent();
                    // Set localStorage entries before navigating to canvas pages
                    return [4 /*yield*/, page.evaluate((_a) => {
                            const pocKey = _a.pocKey, pocValue = _a.pocValue, diagNames = _a.diagNames, diagValue = _a.diagValue;
                            try {
                                // Core PoC key
                                localStorage.setItem(pocKey, JSON.stringify(pocValue));
                                // Also write a few diagram name variants that the app may look for
                                diagNames.forEach((name) => {
                                    try {
                                        localStorage.setItem("diagram-".concat(name), diagValue);
                                    }
                                    catch (e) { /* ignore */ }
                                });
                                // Extra canvas/legacy keys
                                try {
                                    localStorage.setItem('yappc:canvas', JSON.stringify(pocValue.canvas));
                                }
                                catch (e) { }
                                try {
                                    localStorage.setItem('canvas-seed', JSON.stringify(pocValue.canvas));
                                }
                                catch (e) { }
                                // Helpful E2E flags used by some routes
                                localStorage.setItem('E2E_SIMPLE_PAGES', '1');
                                localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
                                // Hint to the app that we created demo workspace/project for e2e runs
                                try {
                                    localStorage.setItem('e2e_created_project', JSON.stringify({ workspaceId: 'ws-1', projectId: 'proj-1' }));
                                }
                                catch (e) {
                                    // ignore
                                }
                                // Also write full mock arrays that the browser MSW-resolvers can prefer
                                try {
                                    localStorage.setItem('e2e:mockWorkspaces', JSON.stringify([
                                        { id: 'ws-1', name: 'E2E Workspace (ws-1)', description: 'Seeded workspace', ownerId: 'u-e2e', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
                                    ]));
                                    localStorage.setItem('e2e:mockProjects', JSON.stringify([
                                        { id: 'proj-1', workspaceId: 'ws-1', name: 'E2E Project (proj-1)', description: 'Seeded project', type: 'UI', targets: ['web'], status: 'active', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
                                    ]));
                                }
                                catch (e) {
                                    // ignore
                                }
                                // Enable verbose test logger in the app so CanvasFlow can emit helpful console traces
                                try {
                                    // @ts-ignore - test-only global
                                    window.__TEST_LOGGER_ENABLED__ = true;
                                }
                                catch (e) { }
                                console.log('[global-setup] localStorage seeded (multiple keys)');
                                return true;
                            }
                            catch (e) {
                                console.error('[global-setup] localStorage set failed', e);
                                return false;
                            }
                        }, {
                            pocKey: 'yappc:poc:canvas',
                            pocValue: pocSnapshot,
                            diagNames: diagramNames,
                            diagValue: diagramJson,
                        })];
                case 6:
                    // Set localStorage entries before navigating to canvas pages
                    _a.sent();
                    // Small delay to ensure storage is ready
                    return [4 /*yield*/, page.waitForTimeout(200)];
                case 7:
                    // Small delay to ensure storage is ready
                    _a.sent();
                    _a.label = 8;
                case 8:
                    _a.trys.push([8, 22, , 23]);
                    _a.label = 9;
                case 9:
                    _a.trys.push([9, 19, , 20]);
                    seedPayload = {
                        workspaces: [{ id: 'ws-1', name: 'E2E Workspace (ws-1)' }],
                        projects: [{ id: 'proj-1', workspaceId: 'ws-1', name: 'E2E Project (proj-1)' }],
                    };
                    seedUrl = `${baseURL.replace(/\/$/, '')  }/__e2e__/seed`;
                    _a.label = 10;
                case 10:
                    _a.trys.push([10, 17, , 18]);
                    return [4 /*yield*/, page.request.post(seedUrl, {
                            data: JSON.stringify(seedPayload),
                            headers: { 'content-type': 'application/json' },
                            timeout: 5000,
                        })];
                case 11:
                    _a.sent();
                    console.log('[global-setup] posted seed payload to dev server');
                    _a.label = 12;
                case 12:
                    _a.trys.push([12, 15, , 16]);
                    indexUrl = `${baseURL.replace(/\/$/, '')  }/index.html`;
                    return [4 /*yield*/, page.request.get(indexUrl, { timeout: 3000 }).catch(() => { })];
                case 13:
                    _a.sent();
                    // give the browser a moment to run any injected script
                    return [4 /*yield*/, page.waitForTimeout(150)];
                case 14:
                    // give the browser a moment to run any injected script
                    _a.sent();
                    return [3 /*break*/, 16];
                case 15:
                    e_1 = _a.sent();
                    return [3 /*break*/, 16];
                case 16: return [3 /*break*/, 18];
                case 17:
                    innerErr_1 = _a.sent();
                    console.warn('[global-setup] dev server seed POST failed or not available', innerErr_1);
                    return [3 /*break*/, 18];
                case 18: return [3 /*break*/, 20];
                case 19:
                    e_2 = _a.sent();
                    // ignore if server not available
                    console.warn('[global-setup] dev server seed POST failed or not available', e_2);
                    return [3 /*break*/, 20];
                case 20:
                    storagePath = './e2e/playwright-storage-state.json';
                    return [4 /*yield*/, context.storageState({ path: storagePath })];
                case 21:
                    _a.sent();
                    console.log('[global-setup] persisted storageState to', storagePath);
                    return [3 /*break*/, 23];
                case 22:
                    e_3 = _a.sent();
                    console.warn('[global-setup] failed to persist storage state', e_3);
                    return [3 /*break*/, 23];
                case 23:
                    consoleMessages_1 = [];
                    page.on('console', (msg) => {
                        try {
                            consoleMessages_1.push({ type: msg.type(), text: msg.text() });
                        }
                        catch (e) { }
                    });
                    pageErrors_1 = [];
                    page.on('pageerror', (err) => { try {
                        pageErrors_1.push(String(err));
                    }
                    catch (e) { } });
                    _a.label = 24;
                case 24:
                    _a.trys.push([24, 27, , 28]);
                    return [4 /*yield*/, page.goto(baseURL, { waitUntil: 'networkidle', timeout: 90000 })];
                case 25:
                    _a.sent();
                    return [4 /*yield*/, page.waitForTimeout(300)];
                case 26:
                    _a.sent();
                    return [3 /*break*/, 28];
                case 27:
                    e_4 = _a.sent();
                    return [3 /*break*/, 28];
                case 28:
                    _a.trys.push([28, 32, , 33]);
                    pocUrl = `${baseURL.replace(/\/$/, '')  }/canvas-poc`;
                    pocSelectors = [
                        '[data-testid="rf__wrapper"]',
                        'body [data-testid="canvas-poc-root"]',
                        '.react-flow',
                        '.react-flow__node',
                        'text=Canvas Phase 0 PoC',
                    ];
                    return [4 /*yield*/, tryWarmRoute(pocUrl, pocSelectors, 5, 45000)];
                case 29:
                    warmed = _a.sent();
                    if (warmed) return [3 /*break*/, 31];
                    return [4 /*yield*/, verifySeededGraphQL()];
                case 30:
                    graphqlOk = _a.sent();
                    if (graphqlOk) {
                        console.log('[global-setup] GraphQL indicates seeded project is available; treating /canvas-poc as warmed');
                    }
                    else {
                        console.warn('[global-setup] canvas-poc did not warm after retries and GraphQL check');
                    }
                    _a.label = 31;
                case 31: return [3 /*break*/, 33];
                case 32:
                    err_1 = _a.sent();
                    console.warn('[global-setup] canvas-poc warming errored', err_1);
                    return [3 /*break*/, 33];
                case 33:
                    _a.trys.push([33, 37, , 38]);
                    canvasUrl = `${baseURL.replace(/\/$/, '')  }/canvas`;
                    canvasSelectors = ['[data-testid="react-flow-wrapper"]', '[data-testid="rf__wrapper"]', '.react-flow', '.react-flow__node'];
                    return [4 /*yield*/, tryWarmRoute(canvasUrl, canvasSelectors, 5, 45000)];
                case 34:
                    warmedCanvas = _a.sent();
                    if (warmedCanvas) return [3 /*break*/, 36];
                    return [4 /*yield*/, verifySeededGraphQL()];
                case 35:
                    graphqlOk = _a.sent();
                    if (graphqlOk) {
                        console.log('[global-setup] GraphQL indicates seeded project is available; treating /canvas as warmed');
                    }
                    else {
                        console.warn('[global-setup] canvas did not warm after retries and GraphQL check');
                    }
                    _a.label = 36;
                case 36: return [3 /*break*/, 38];
                case 37:
                    err_2 = _a.sent();
                    console.warn('[global-setup] canvas warming errored', err_2);
                    return [3 /*break*/, 38];
                case 38:
                    _a.trys.push([38, 41, , 42]);
                    seededRoute = `${baseURL.replace(/\/$/, '')  }/w/ws-1/p/proj-1/canvas`;
                    return [4 /*yield*/, page.goto(seededRoute, { waitUntil: 'networkidle', timeout: 60000 })];
                case 39:
                    _a.sent();
                    // Wait a bit longer here; some environments need more time to hydrate MSW and GraphQL
                    return [4 /*yield*/, page.waitForSelector('[data-testid="react-flow-wrapper"]', { timeout: 20000 })];
                case 40:
                    // Wait a bit longer here; some environments need more time to hydrate MSW and GraphQL
                    _a.sent();
                    console.log('[global-setup] seeded project canvas route loaded after seeding', seededRoute);
                    return [3 /*break*/, 42];
                case 41:
                    err_3 = _a.sent();
                    console.warn('[global-setup] seeded project canvas route did not render after seeding', err_3);
                    return [3 /*break*/, 42];
                case 42:
                    _a.trys.push([42, 45, , 46]);
                    seedJsonUrl = `${baseURL.replace(/\/$/, '')  }/__e2e__/mock-data.json`;
                    return [4 /*yield*/, page.request.get(seedJsonUrl, { timeout: 3000 })];
                case 43:
                    res = _a.sent();
                    return [4 /*yield*/, res.text()];
                case 44:
                    text = _a.sent();
                    console.log('[global-setup] dev-server mock-data.json content:', text);
                    return [3 /*break*/, 46];
                case 45:
                    err_4 = _a.sent();
                    console.warn('[global-setup] could not GET dev-server mock-data.json', err_4);
                    return [3 /*break*/, 46];
                case 46: return [3 /*break*/, 51];
                case 47:
                    err_5 = _a.sent();
                    console.error('[global-setup] Error during seeding:', err_5);
                    return [3 /*break*/, 51];
                case 48: return [4 /*yield*/, context.close()];
                case 49:
                    _a.sent();
                    return [4 /*yield*/, browser.close()];
                case 50:
                    _a.sent();
                    return [7 /*endfinally*/];
                case 51: return [2 /*return*/];
            }
        });
    });
}
function globalSetup(config) {
    return __awaiter(this, void 0, void 0, function () {
        let baseURL;
        let _a, _b, _c, _d, _e;
        return __generator(this, (_f) => {
            switch (_f.label) {
                case 0:
                    baseURL = (_e = (_a = process.env.PLAYWRIGHT_BASE_URL) !== null && _a !== void 0 ? _a : (_d = (_c = (_b = config.projects) === null || _b === void 0 ? void 0 : _b[0]) === null || _c === void 0 ? void 0 : _c.use) === null || _d === void 0 ? void 0 : _d.baseURL) !== null && _e !== void 0 ? _e : 'http://localhost:5173';
                    console.log('[global-setup] PLAYWRIGHT_ENABLE_CANVAS=', process.env.PLAYWRIGHT_ENABLE_CANVAS);
                    if (!process.env.PLAYWRIGHT_ENABLE_CANVAS) return [3 /*break*/, 2];
                    try {
                        console.log('[global-setup] Ensuring node-side mocks for e2e ids');
                        // Attempt to ensure ws-1/proj-1 exist in mocks resolvers used by MSW
                        try {
                            (0, seed_mocks_1.ensureE2EMocks)();
                        }
                        catch (e) {
                            console.warn('[global-setup] ensureE2EMocks failed', e);
                        }
                    }
                    catch (e) {
                        console.warn('[global-setup] Node-side seeding skipped or failed', e);
                    }
                    return [4 /*yield*/, seedCanvas(baseURL)];
                case 1:
                    _f.sent();
                    return [3 /*break*/, 3];
                case 2:
                    console.log('[global-setup] Skipping canvas seeding (PLAYWRIGHT_ENABLE_CANVAS not set)');
                    _f.label = 3;
                case 3: return [2 /*return*/];
            }
        });
    });
}
