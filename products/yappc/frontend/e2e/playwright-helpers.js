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
exports.OUT_DIR = void 0;
exports.getStoryUrl = getStoryUrl;
exports.checkStorybookAvailable = checkStorybookAvailable;
exports.getPreviewFrame = getPreviewFrame;
exports.waitForCanvasReady = waitForCanvasReady;
const path_1 = require("path");
/** Resolve the Storybook URL for tests. Prefer STORYBOOK_URL, fallback to STORYBOOK_PORT. */
function getStoryUrl(pathname) {
    if (pathname === void 0) { pathname = '/?path=/story/canvas-toolbar--all-actions'; }
    if (process.env.STORYBOOK_URL)
        return process.env.STORYBOOK_URL;
    const port = process.env.STORYBOOK_PORT || '6006';
    return "http://localhost:".concat(port).concat(pathname);
}
/** Check if Storybook is running and accessible */
function checkStorybookAvailable(page) {
    return __awaiter(this, void 0, void 0, function () {
        let e_1;
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0:
                    _a.trys.push([0, 2, , 3]);
                    return [4 /*yield*/, page.goto(getStoryUrl(), { timeout: 5000 })];
                case 1:
                    _a.sent();
                    return [2 /*return*/, true];
                case 2:
                    e_1 = _a.sent();
                    console.warn('Storybook may not be running:', e_1);
                    return [2 /*return*/, false];
                case 3: return [2 /*return*/];
            }
        });
    });
}
/** Return a frameLocator for the Storybook preview iframe. */
function getPreviewFrame(page) {
    return page.frameLocator('iframe[id="storybook-preview-iframe"]');
}
/** Wait for the canvas to be ready inside the preview iframe. */
function waitForCanvasReady(frame_1) {
    return __awaiter(this, arguments, void 0, function (frame, timeout) {
        let selectors, _i, selectors_1, sel, e_2, e_3;
        if (timeout === void 0) { timeout = 15000; }
        return __generator(this, (_a) => {
            switch (_a.label) {
                case 0:
                    selectors = [
                        '[data-testid="rf__wrapper"]',
                        '[data-testid="react-flow-wrapper"]',
                        '#canvas-drop-zone',
                        '[data-testid="canvas-drop-zone"]',
                        '[data-testid="canvas-flow"]',
                        '[data-testid="canvas-toolbar"]',
                    ];
                    // Log what we're looking for to help debug
                    console.log('Waiting for canvas ready, checking selectors:', selectors.join(', '));
                    _i = 0, selectors_1 = selectors;
                    _a.label = 1;
                case 1:
                    if (!(_i < selectors_1.length)) return [3 /*break*/, 6];
                    sel = selectors_1[_i];
                    _a.label = 2;
                case 2:
                    _a.trys.push([2, 4, , 5]);
                    return [4 /*yield*/, frame.locator(sel).waitFor({ state: 'visible', timeout: timeout / selectors.length })];
                case 3:
                    _a.sent();
                    console.log("Found canvas element: ".concat(sel));
                    return [2 /*return*/, sel];
                case 4:
                    e_2 = _a.sent();
                    return [3 /*break*/, 5];
                case 5:
                    _i++;
                    return [3 /*break*/, 1];
                case 6:
                    _a.trys.push([6, 8, , 9]);
                    return [4 /*yield*/, frame
                            .locator('#component-palette, [data-testid="component-palette"]')
                            .waitFor({ state: 'visible', timeout: 5000 })];
                case 7:
                    _a.sent();
                    console.log('Found component palette');
                    return [2 /*return*/, '#component-palette'];
                case 8:
                    e_3 = _a.sent();
                    console.warn('Could not find any canvas elements or palette');
                    throw new Error('Canvas elements not found in preview frame');
                case 9: return [2 /*return*/];
            }
        });
    });
}
exports.OUT_DIR = path_1.default.resolve(process.cwd(), 'test-results');
