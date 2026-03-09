"use strict";
/**
 * Performance Test Utilities
 * Shared utilities for measuring and validating canvas performance
 */
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
exports.DEFAULT_BUDGETS = void 0;
exports.measureRenderPerformance = measureRenderPerformance;
exports.measureFrameRate = measureFrameRate;
exports.generateCanvasData = generateCanvasData;
exports.seedPerformanceData = seedPerformanceData;
exports.waitForCanvasRender = waitForCanvasRender;
exports.validatePerformance = validatePerformance;
exports.DEFAULT_BUDGETS = {
    maxRenderTime: 2000, // 2s
    minFrameRate: 30, // FPS
    maxMemoryIncrease: 100 * 1024 * 1024, // 100MB
    maxInteractionLatency: 100, // 100ms
};
/**
 * Measure canvas rendering performance
 */
function measureRenderPerformance(page, action) {
    return __awaiter(this, void 0, void 0, function () {
        let initialMemory, startTime, renderTime, finalMemory;
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: return [4 /*yield*/, page.evaluate(() => { let _a; return ((_a = performance.memory) === null || _a === void 0 ? void 0 : _a.usedJSHeapSize) || 0; })];
                case 1:
                    initialMemory = _a.sent();
                    startTime = Date.now();
                    return [4 /*yield*/, action()];
                case 2:
                    _a.sent();
                    renderTime = Date.now() - startTime;
                    return [4 /*yield*/, page.evaluate(() => { let _a; return ((_a = performance.memory) === null || _a === void 0 ? void 0 : _a.usedJSHeapSize) || 0; })];
                case 3:
                    finalMemory = _a.sent();
                    return [2 /*return*/, {
                            renderTime,
                            memoryIncrease: finalMemory - initialMemory,
                        }];
            }
        });
    });
}
/**
 * Measure frame rate during canvas interaction
 */
function measureFrameRate(page_1) {
    return __awaiter(this, arguments, void 0, function (page, duration) {
        if (duration === void 0) { duration = 1000; }
        return __generator(this, (_a) => {
            return [2 /*return*/, page.evaluate((ms) => {
                    return new Promise((resolve) => {
                        let frames = 0;
                        const startTime = performance.now();
                        function countFrame() {
                            frames++;
                            if (performance.now() - startTime < ms) {
                                requestAnimationFrame(countFrame);
                            }
                            else {
                                const fps = (frames / ms) * 1000;
                                resolve(fps);
                            }
                        }
                        requestAnimationFrame(countFrame);
                    });
                }, duration)];
        });
    });
}
/**
 * Generate test canvas data with specified complexity
 */
function generateCanvasData(nodeCount, connectionDensity) {
    if (connectionDensity === void 0) { connectionDensity = 0.1; }
    const elements = [];
    const connections = [];
    // Generate nodes in a grid layout
    const cols = Math.ceil(Math.sqrt(nodeCount));
    for (var i = 0; i < nodeCount; i++) {
        elements.push({
            id: "perf-node-".concat(i),
            kind: 'node',
            type: i % 3 === 0 ? 'api' : i % 3 === 1 ? 'component' : 'data',
            position: {
                x: (i % cols) * 180,
                y: Math.floor(i / cols) * 120,
            },
            size: { width: 150, height: 80 },
            data: { label: "Node ".concat(i) },
            style: {},
        });
    }
    // Generate connections based on density
    const maxConnections = Math.floor(nodeCount * connectionDensity);
    for (var i = 0; i < maxConnections; i++) {
        const source = Math.floor(Math.random() * nodeCount);
        const target = Math.floor(Math.random() * nodeCount);
        if (source !== target) {
            connections.push({
                id: "perf-edge-".concat(i),
                source: "perf-node-".concat(source),
                target: "perf-node-".concat(target),
            });
        }
    }
    return { elements, connections, sketches: [] };
}
/**
 * Seed canvas with performance test data
 */
function seedPerformanceData(page_1, nodeCount_1) {
    return __awaiter(this, arguments, void 0, function (page, nodeCount, connectionDensity) {
        let data;
        if (connectionDensity === void 0) { connectionDensity = 0.1; }
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0:
                    data = generateCanvasData(nodeCount, connectionDensity);
                    return [4 /*yield*/, page.evaluate((canvasData) => {
                            localStorage.setItem('canvas-state', JSON.stringify(canvasData));
                        }, data)];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Wait for canvas rendering to complete
 */
function waitForCanvasRender(page_1, expectedElements_1) {
    return __awaiter(this, arguments, void 0, function (page, expectedElements, timeout) {
        if (timeout === void 0) { timeout = 10000; }
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0: return [4 /*yield*/, page.waitForFunction((count) => {
                        const nodes = document.querySelectorAll('.react-flow__node');
                        return nodes.length >= Math.min(count, 50); // Visual viewport limit
                    }, expectedElements, { timeout })];
                case 1:
                    _a.sent();
                    return [2 /*return*/];
            }
        });
    });
}
/**
 * Validate performance against budget
 */
function validatePerformance(metrics, budget) {
    if (budget === void 0) { budget = exports.DEFAULT_BUDGETS; }
    const violations = [];
    if (metrics.renderTime && budget.maxRenderTime && metrics.renderTime > budget.maxRenderTime) {
        violations.push("Render time ".concat(metrics.renderTime, "ms exceeds budget ").concat(budget.maxRenderTime, "ms"));
    }
    if (metrics.frameRate && budget.minFrameRate && metrics.frameRate < budget.minFrameRate) {
        violations.push("Frame rate ".concat(metrics.frameRate, "fps below budget ").concat(budget.minFrameRate, "fps"));
    }
    if (metrics.memoryUsage && budget.maxMemoryIncrease && metrics.memoryUsage > budget.maxMemoryIncrease) {
        violations.push("Memory increase ".concat(Math.round(metrics.memoryUsage / 1024 / 1024), "MB exceeds budget ").concat(Math.round(budget.maxMemoryIncrease / 1024 / 1024), "MB"));
    }
    if (metrics.interactionLatency && budget.maxInteractionLatency && metrics.interactionLatency > budget.maxInteractionLatency) {
        violations.push("Interaction latency ".concat(metrics.interactionLatency, "ms exceeds budget ").concat(budget.maxInteractionLatency, "ms"));
    }
    return {
        passed: violations.length === 0,
        violations,
    };
}
