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
exports.cleanTestState = cleanTestState;
exports.resetJotaiAtoms = resetJotaiAtoms;
exports.clearMSWHandlers = clearMSWHandlers;
exports.resetCanvasState = resetCanvasState;
exports.seedCanvasTestData = seedCanvasTestData;
exports.waitForCanvasReady = waitForCanvasReady;
exports.setupTest = setupTest;
exports.teardownTest = teardownTest;
const DEFAULT_OPTIONS = {
    clearStorage: true,
    resetAtoms: true,
    clearMSW: true,
    resetCanvas: true,
    seedData: false,
};
/**
 * Clean test state including browser storage, Jotai atoms, and MSW handlers
 */
function cleanTestState(page) {
    return __awaiter(this, void 0, void 0, function () {
        let url, error_1;
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: 
                // Reset MSW handlers first (need page context)
                return [4 /*yield*/, clearMSWHandlers(page)];
                case 1:
                    // Reset MSW handlers first (need page context)
                    _a.sent();
                    // Clear browser storage (will skip if on blank page)
                    return [4 /*yield*/, clearBrowserStorage(page)];
                case 2:
                    // Clear browser storage (will skip if on blank page)
                    _a.sent();
                    _a.label = 3;
                case 3:
                    _a.trys.push([3, 6, , 7]);
                    url = page.url();
                    if (!(url !== 'about:blank' && !url.startsWith('data:'))) return [3 /*break*/, 5];
                    return [4 /*yield*/, page.evaluate(() => {
                            // Clear any timers that might be running
                            const highestId = setTimeout(() => { }, 0);
                            const highestIdNum = parseInt(String(highestId), 10);
                            if (!isNaN(highestIdNum)) {
                                for (let i = 0; i < highestIdNum; i++) {
                                    clearTimeout(i);
                                    clearInterval(i);
                                }
                            }
                        })];
                case 4:
                    _a.sent();
                    _a.label = 5;
                case 5: return [3 /*break*/, 7];
                case 6:
                    error_1 = _a.sent();
                    console.warn('Failed to clear timers:', error_1);
                    return [3 /*break*/, 7];
                case 7: return [2 /*return*/];
            }
        });
    });
}
/**
 * Clears browser storage (localStorage, sessionStorage, indexedDB)
 * Only clears storage if we're on a page that supports it (not about:blank)
 */
function clearBrowserStorage(page) {
    return __awaiter(this, void 0, void 0, function () {
        let url, error_2;
        const _this = this;
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    url = page.url();
                    if (url === 'about:blank' || url.startsWith('data:')) {
                        return [2 /*return*/]; // Skip storage clearing for blank/data pages
                    }
                    return [4 /*yield*/, page.evaluate(() => {
                            // Clear localStorage
                            if (typeof window !== 'undefined' && window.localStorage) {
                                try {
                                    window.localStorage.clear();
                                }
                                catch (e) {
                                    // Ignore SecurityError for pages that don't support localStorage
                                }
                            }
                            // Clear sessionStorage
                            if (typeof window !== 'undefined' && window.sessionStorage) {
                                try {
                                    window.sessionStorage.clear();
                                }
                                catch (e) {
                                    // Ignore SecurityError for pages that don't support sessionStorage
                                }
                            }
                            // Clear IndexedDB if available
                            if (typeof window !== 'undefined' && window.indexedDB) {
                                try {
                                    // Clear common IndexedDB databases
                                    const dbNames = ['canvas-app', 'react-flow', 'app-data'];
                                    dbNames.forEach((dbName) => { return __awaiter(_this, void 0, void 0, function () {
                                        return __generator(this, (_a) => {
                                            try {
                                                window.indexedDB.deleteDatabase(dbName);
                                            }
                                            catch (e) {
                                                // Ignore errors for non-existent databases
                                            }
                                            return [2 /*return*/];
                                        });
                                    }); });
                                }
                                catch (e) {
                                    // IndexedDB operations may fail in some contexts
                                }
                            }
                        })];
                case 1:
                    _a.sent();
                    return [3 /*break*/, 3];
                case 2:
                    error_2 = _a.sent();
                    console.warn('Failed to clear browser storage:', error_2);
                    return [3 /*break*/, 3];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/**
 * Reset Jotai atoms to their initial state
 */
function resetJotaiAtoms(page) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: return [4 /*yield*/, page.evaluate(() => {
                        // Reset Jotai store if available
                        if (window.__JOTAI_STORE__) {
                            window.__JOTAI_STORE__.clear();
                        }
                        // Clear any canvas-specific atoms
                        const atomKeys = [
                            'canvasAtom',
                            'viewportAtom',
                            'selectionAtom',
                            'historyAtom',
                            'sketchAtom',
                            'collaborationAtom',
                        ];
                        atomKeys.forEach((key) => {
                            if (window[key]) {
                                delete window[key];
                            }
                        });
                    })];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Clear MSW request handlers and reset mocks
 */
function clearMSWHandlers(page) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: return [4 /*yield*/, page.evaluate(() => {
                        // Reset MSW handlers if available
                        if (window.msw &&
                            window.msw.worker &&
                            'resetHandlers' in window.msw.worker) {
                            window.msw.worker.resetHandlers();
                        }
                        // Clear any mock data
                        if (window.__TEST_MOCKS__) {
                            window.__TEST_MOCKS__.clear();
                        }
                    })];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Reset canvas-specific state
 */
function resetCanvasState(page) {
    return __awaiter(this, void 0, void 0, function () {
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: return [4 /*yield*/, page.evaluate(() => {
                        // Clear canvas-specific localStorage keys
                        const canvasKeys = [
                            'canvas-state',
                            'canvas-history',
                            'canvas-selection',
                            'canvas-viewport',
                            'sketch-state',
                            'canvas-poc-v1.0.0',
                            'canvas-phase1-state',
                            'canvas-phase2-state',
                        ];
                        canvasKeys.forEach((key) => {
                            localStorage.removeItem(key);
                            sessionStorage.removeItem(key);
                        });
                        // Reset React Flow instance if available
                        if (window.__RF_INSTANCE__) {
                            window.__RF_INSTANCE__.setNodes([]);
                            window.__RF_INSTANCE__.setEdges([]);
                            window.__RF_INSTANCE__.fitView();
                        }
                    })];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Seed deterministic test data for canvas tests
 */
function seedCanvasTestData(page_1) {
    return __awaiter(this, arguments, void 0, function (page, scenario) {
        let seedData;
        if (scenario === void 0) { scenario = 'default'; }
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0:
                    seedData = getTestSeedData(scenario);
                    return [4 /*yield*/, page.evaluate((data) => {
                            // Set localStorage with seed data
                            Object.entries(data.localStorage || {}).forEach((_a) => {
                                const key = _a[0], value = _a[1];
                                localStorage.setItem(key, JSON.stringify(value));
                            });
                            // Set sessionStorage with seed data
                            Object.entries(data.sessionStorage || {}).forEach((_a) => {
                                const key = _a[0], value = _a[1];
                                sessionStorage.setItem(key, JSON.stringify(value));
                            });
                            // Set global test data if needed
                            if (data.globals) {
                                Object.assign(window, data.globals);
                            }
                        }, seedData)];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Get seed data for different test scenarios
 */
function getTestSeedData(scenario) {
    const baseData = {
        localStorage: {},
        sessionStorage: {},
        globals: {},
    };
    switch (scenario) {
        case 'canvas-basic':
            return __assign(__assign({}, baseData), { localStorage: {
                    'canvas-state': {
                        elements: [
                            {
                                id: 'test-node-1',
                                kind: 'node',
                                type: 'component',
                                position: { x: 300, y: 200 },
                                size: { width: 150, height: 80 },
                                data: { label: 'Test Frontend' },
                                style: {},
                            },
                        ],
                        connections: [],
                        sketches: [],
                    },
                } });
        case 'canvas-with-connections':
            return __assign(__assign({}, baseData), { localStorage: {
                    'canvas-state': {
                        elements: [
                            {
                                id: 'test-node-1',
                                kind: 'node',
                                type: 'component',
                                position: { x: 300, y: 200 },
                                size: { width: 150, height: 80 },
                                data: { label: 'Frontend' },
                                style: {},
                            },
                            {
                                id: 'test-node-2',
                                kind: 'node',
                                type: 'api',
                                position: { x: 600, y: 200 },
                                size: { width: 150, height: 80 },
                                data: { label: 'Backend' },
                                style: {},
                            },
                        ],
                        connections: [
                            {
                                id: 'test-edge-1',
                                source: 'test-node-1',
                                target: 'test-node-2',
                                sourceHandle: 'right',
                                targetHandle: 'left',
                            },
                        ],
                        sketches: [],
                    },
                } });
        case 'version-control':
            return __assign(__assign({}, baseData), { localStorage: {
                    'canvas-snapshots': [
                        {
                            id: 'snapshot-1',
                            name: 'Initial Layout',
                            timestamp: new Date(Date.now() - 3600000).toISOString(),
                            state: {
                                elements: [],
                                connections: [],
                                sketches: [],
                            },
                        },
                    ],
                    'canvas-state': {
                        elements: [
                            {
                                id: 'test-node-1',
                                kind: 'node',
                                type: 'component',
                                position: { x: 400, y: 300 },
                                size: { width: 150, height: 80 },
                                data: { label: 'Modified Component' },
                                style: {},
                            },
                        ],
                        connections: [],
                        sketches: [],
                    },
                } });
        default:
            return baseData;
    }
}
/**
 * Wait for canvas to be fully loaded and ready
 */
function waitForCanvasReady(page_1) {
    return __awaiter(this, arguments, void 0, function (page, timeout) {
        let e_1, err_1, err_2;
        if (timeout === void 0) { timeout = 10000; }
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    return [4 /*yield*/, page.evaluate(() => {
                            // eslint-disable-next-line no-console
                            console.debug('[E2E] DOM presence', {
                                reactFlowWrapperExists: !!document.querySelector('[data-testid="react-flow-wrapper"]'),
                                rfWrapperExists: !!document.querySelector('[data-testid="rf__wrapper"]'),
                                controlsExists: !!document.querySelector('.react-flow__controls'),
                                dropZoneExists: !!document.querySelector('#canvas-drop-zone'),
                            });
                        })];
                case 1:
                    _a.sent();
                    return [3 /*break*/, 3];
                case 2:
                    e_1 = _a.sent();
                    return [3 /*break*/, 3];
                case 3:
                    _a.trys.push([3, 5, , 7]);
                    return [4 /*yield*/, page.waitForSelector('[data-testid="react-flow-wrapper"]', {
                            timeout,
                        })];
                case 4:
                    _a.sent();
                    return [3 /*break*/, 7];
                case 5:
                    err_1 = _a.sent();
                    // Fallback to alternative selector
                    return [4 /*yield*/, page.waitForSelector('[data-testid="rf__wrapper"]', { timeout })];
                case 6:
                    // Fallback to alternative selector
                    _a.sent();
                    return [3 /*break*/, 7];
                case 7: 
                // Wait for basic React Flow components to be ready
                return [4 /*yield*/, page.waitForSelector('.react-flow__controls', { timeout })];
                case 8:
                    // Wait for basic React Flow components to be ready
                    _a.sent();
                    _a.label = 9;
                case 9:
                    _a.trys.push([9, 11, , 12]);
                    return [4 /*yield*/, page.waitForSelector('#canvas-drop-zone', { timeout: 2000 })];
                case 10:
                    _a.sent();
                    return [3 /*break*/, 12];
                case 11:
                    err_2 = _a.sent();
                    // Canvas drop zone not present, continue anyway
                    console.log('Canvas drop zone not found, continuing...');
                    return [3 /*break*/, 12];
                case 12: 
                // Wait for network to be idle
                return [4 /*yield*/, page.waitForLoadState('networkidle')];
                case 13:
                    // Wait for network to be idle
                    _a.sent();
                    // Wait for initial render to complete
                    return [4 /*yield*/, page.waitForTimeout(500)];
                case 14:
                    // Wait for initial render to complete
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Comprehensive test setup that ensures clean, predictable state
 */
function setupTest(page_1) {
    return __awaiter(this, arguments, void 0, function (page, options) {
        let targetUrl, error_3, error_4, e_2;
        if (options === void 0) { options = {}; }
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: 
                // Clean state first
                return [4 /*yield*/, cleanTestState(page)];
                case 1:
                    // Clean state first
                    _a.sent();
                    targetUrl = options.url || '/canvas';
                    return [4 /*yield*/, page.goto(targetUrl)];
                case 2:
                    _a.sent();
                    _a.label = 3;
                case 3:
                    _a.trys.push([3, 5, , 6]);
                    return [4 /*yield*/, page.evaluate(() => {
                            const provider = window.mockCollaborationProvider;
                            if (provider && typeof provider.resetForTests === 'function') {
                                provider.resetForTests();
                            }
                        })];
                case 4:
                    _a.sent();
                    return [3 /*break*/, 6];
                case 5:
                    error_3 = _a.sent();
                    console.warn('Failed to reset collaboration provider', error_3);
                    return [3 /*break*/, 6];
                case 6:
                    _a.trys.push([6, 8, , 9]);
                    return [4 /*yield*/, page.evaluate(() => {
                            window.__E2E_TEST_MODE = true;
                            window.__E2E_TEST_NO_POINTER_BLOCK = true;
                            try {
                                localStorage.setItem('E2E_DISABLE_OVERLAYS', '1');
                            }
                            catch (err) {
                                // ignore
                            }
                            const neutralizeOverlays = function () {
                                const selectors = [
                                    '.MuiModal-backdrop',
                                    '.MuiBackdrop-root',
                                    '.MuiDrawer-root',
                                ];
                                selectors.forEach((selector) => {
                                    document.querySelectorAll(selector).forEach((el) => {
                                        const element = el;
                                        element.style.pointerEvents = 'none';
                                        element.style.opacity = '0';
                                    });
                                });
                                document.querySelectorAll('[data-testid="page-designer"]').forEach((el) => {
                                    const element = el;
                                    element.style.pointerEvents = 'auto';
                                    element.style.opacity = '';
                                });
                            };
                            neutralizeOverlays();
                            const disablePaletteFocus = function () {
                                const palette = document.querySelector('#component-palette, [data-testid="component-palette"]');
                                if (!palette) {
                                    return;
                                }
                                palette.setAttribute('data-e2e-no-focus', 'true');
                                const focusable = palette.querySelectorAll('button, a, input, select, textarea, [tabindex]');
                                focusable.forEach((el) => {
                                    if (!el.hasAttribute('data-e2e-original-tabindex')) {
                                        const existing = el.getAttribute('tabindex');
                                        el.setAttribute('data-e2e-original-tabindex', existing !== null && existing !== void 0 ? existing : '');
                                    }
                                    el.setAttribute('tabindex', '-1');
                                    el.setAttribute('aria-hidden', 'true');
                                });
                            };
                            disablePaletteFocus();
                            if (!window.__E2E_OVERLAY_OBSERVER) {
                                const observer = new MutationObserver(() => {
                                    neutralizeOverlays();
                                    disablePaletteFocus();
                                });
                                observer.observe(document.documentElement, { childList: true, subtree: true });
                                window.__E2E_OVERLAY_OBSERVER = observer;
                            }
                        })];
                case 7:
                    _a.sent();
                    return [3 /*break*/, 9];
                case 8:
                    error_4 = _a.sent();
                    console.warn('Failed to set E2E test mode flag', error_4);
                    return [3 /*break*/, 9];
                case 9:
                    _a.trys.push([9, 11, , 12]);
                    return [4 /*yield*/, page.evaluate(() => {
                            let _a, _b;
                            try {
                                const keys = Object.keys(localStorage || {});
                                // eslint-disable-next-line no-console
                                console.debug('[E2E] localStorage keys', keys);
                                const persistenceKey = 'yappc-canvas:demo-project:main-canvas';
                                const stored = localStorage.getItem(persistenceKey);
                                if (stored) {
                                    try {
                                        const snapshot = JSON.parse(stored);
                                        // eslint-disable-next-line no-console
                                        console.debug('[E2E] persistence snapshot summary', {
                                            key: persistenceKey,
                                            version: snapshot.version,
                                            elements: Array.isArray((_a = snapshot.data) === null || _a === void 0 ? void 0 : _a.elements)
                                                ? snapshot.data.elements.length
                                                : 0,
                                            connections: Array.isArray((_b = snapshot.data) === null || _b === void 0 ? void 0 : _b.connections)
                                                ? snapshot.data.connections.length
                                                : 0,
                                        });
                                    }
                                    catch (e) {
                                         
                                        console.error('[E2E] failed to parse persistence snapshot', e);
                                    }
                                }
                                else {
                                    // eslint-disable-next-line no-console
                                    console.debug('[E2E] persistence key not found', persistenceKey);
                                }
                            }
                            catch (e) {
                                 
                                console.error('[E2E] localStorage inspect error', e);
                            }
                        })];
                case 10:
                    _a.sent();
                    return [3 /*break*/, 12];
                case 11:
                    e_2 = _a.sent();
                    return [3 /*break*/, 12];
                case 12:
                    if (!(options.seedData && options.seedScenario)) return [3 /*break*/, 14];
                    return [4 /*yield*/, seedCanvasTestData(page, options.seedScenario)];
                case 13:
                    _a.sent();
                    _a.label = 14;
                case 14:
                    if (!(targetUrl.includes('/canvas') || targetUrl === '/')) return [3 /*break*/, 16];
                    return [4 /*yield*/, waitForCanvasReady(page)];
                case 15:
                    _a.sent();
                    _a.label = 16;
                case 16:
                    // Defensive test fix: if the component palette overlays interactive canvas areas
                    // it can intercept pointer events during automated clicks. For E2E stability
                    // temporarily disable pointer-events on the palette container so tests can
                    // interact with canvas controls. This is a test-only change and should be
                    // removed once the underlying UI stacking/z-index issue is fixed.
                    try {
                        // Previously we injected a test-only style to disable palette pointer-events
                        // to avoid click interception. With the new two-column layout the palette
                        // is a dedicated left column and won't overlap the canvas, so no injection
                        // is required. Keep this block here as a no-op for older test runs.
                    }
                    catch (e) {
                        // ignore errors from the diagnostic step
                    }
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Test teardown - clean up after test
 */
function teardownTest(page) {
    return __awaiter(this, void 0, void 0, function () {
        let e_3;
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: return [4 /*yield*/, cleanTestState(page)];
                case 1:
                    _a.sent();
                    _a.label = 2;
                case 2:
                    _a.trys.push([2, 4, , 5]);
                    return [4 /*yield*/, page.evaluate(() => {
                            const style = document.getElementById('e2e-disable-palette-pointer-events');
                            if (style && style.parentNode)
                                style.parentNode.removeChild(style);
                        })];
                case 3:
                    _a.sent();
                    return [3 /*break*/, 5];
                case 4:
                    e_3 = _a.sent();
                    return [3 /*break*/, 5];
                case 5: return [2 /*return*/];
            }
        });
    });
}
