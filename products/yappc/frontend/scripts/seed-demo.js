#!/usr/bin/env node
"use strict";
const __spreadArray = (this && this.__spreadArray) || function (to, from, pack) {
    if (pack || arguments.length === 2) for (var i = 0, l = from.length, ar; i < l; i++) {
        if (ar || !(i in from)) {
            if (!ar) ar = Array.prototype.slice.call(from, 0, i);
            ar[i] = from[i];
        }
    }
    return to.concat(ar || Array.prototype.slice.call(from));
};
Object.defineProperty(exports, "__esModule", { value: true });
/**
 * Dev seed script for local development.
 * Ensures a demo workspace and project exist in the MSW resolvers so
 * the `canvas-demo` route can be opened without needing backend.
 */
const resolvers_1 = require("../libs/mocks/src/resolvers");
function ensureDemoMocks() {
    try {
        // @ts-ignore
        const mw = resolvers_1.resolvers._internalWorkspaces || [];
        // @ts-ignore
        const mp = resolvers_1.resolvers._internalProjects || [];
        const now = new Date().toISOString();
        const ws_1 = { id: 'demo-ws', name: 'Demo Workspace (demo-ws)', description: 'Local demo workspace', ownerId: 'u-local', createdAt: now, updatedAt: now };
        const proj_1 = {
            id: 'demo-proj',
            workspaceId: 'demo-ws',
            name: 'Demo Project (demo-proj)',
            description: 'Local demo project for canvas-demo',
            type: 'UI',
            targets: ['web'],
            status: 'active',
            // ensure onboarding prerequisite 'getting-started' is satisfied
            onboarding: { gettingStarted: true },
            createdAt: now,
            updatedAt: now,
        };
        if (resolvers_1.resolvers.mockWorkspaces) {
            resolvers_1.resolvers.mockWorkspaces = __spreadArray(__spreadArray([], resolvers_1.resolvers.mockWorkspaces.filter((w) => { return w.id !== ws_1.id; }), true), [ws_1], false);
        }
        if (resolvers_1.resolvers.mockProjects) {
            resolvers_1.resolvers.mockProjects = __spreadArray(__spreadArray([], resolvers_1.resolvers.mockProjects.filter((p) => { return p.id !== proj_1.id; }), true), [proj_1], false);
        }
        // Fallback internal arrays
        resolvers_1.resolvers._internalWorkspaces = __spreadArray(__spreadArray([], (mw.filter((w) => { return w.id !== ws_1.id; })), true), [ws_1], false);
        resolvers_1.resolvers._internalProjects = __spreadArray(__spreadArray([], (mp.filter((p) => { return p.id !== proj_1.id; })), true), [proj_1], false);
        console.log('[seed-demo] Ensured demo-ws/demo-proj in resolvers');
        return true;
    }
    catch (e) {
        console.error('[seed-demo] Failed to ensure mocks:', e);
        return false;
    }
}
if (require.main === module) {
    const ok = ensureDemoMocks();
    process.exit(ok ? 0 : 2);
}
