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
exports.ensureE2EMocks = ensureE2EMocks;
/*
  Node-side seed for mocks. This script imports the mocks resolvers and
  ensures a workspace id 'ws-1' and project id 'proj-1' exist so that
  Playwright tests that depend on these canonical ids will find them
  when MSW node server is used.
*/
const resolvers_1 = require("../libs/mocks/src/resolvers");
function ensureE2EMocks() {
    try {
        // Replace or augment the internal arrays if present
        // @ts-ignore access internal arrays
        const mw = resolvers_1.resolvers._internalWorkspaces || [];
        const mp = resolvers_1.resolvers._internalProjects || [];
        // Create explicit entries (plain objects to avoid extra deps)
        const now = new Date().toISOString();
        const ws = { id: 'ws-1', name: 'E2E Workspace (ws-1)', description: 'E2E seeded workspace', ownerId: 'u-e2e', createdAt: now, updatedAt: now };
        const proj = { id: 'proj-1', workspaceId: 'ws-1', name: 'E2E Project (proj-1)', description: 'E2E seeded project', type: 'UI', targets: ['web'], status: 'active', createdAt: now, updatedAt: now };
        // Attach to resolvers for handlers to read
        // Some resolver implementations expose internal arrays; attempt both
        if (resolvers_1.resolvers.mockWorkspaces) {
            resolvers_1.resolvers.mockWorkspaces = __spreadArray(__spreadArray([], resolvers_1.resolvers.mockWorkspaces.filter((w) => { return w.id !== 'ws-1'; }), true), [ws], false);
        }
        if (resolvers_1.resolvers.mockProjects) {
            resolvers_1.resolvers.mockProjects = __spreadArray(__spreadArray([], resolvers_1.resolvers.mockProjects.filter((p) => { return p.id !== 'proj-1'; }), true), [proj], false);
        }
        // As a fallback, set internal placeholders the handlers may read
        resolvers_1.resolvers._internalWorkspaces = __spreadArray(__spreadArray([], (mw.filter((w) => { return w.id !== 'ws-1'; })), true), [ws], false);
        resolvers_1.resolvers._internalProjects = __spreadArray(__spreadArray([], (mp.filter((p) => { return p.id !== 'proj-1'; })), true), [proj], false);
        console.log('[seed-mocks] Ensured ws-1/proj-1 in resolvers');
        return true;
    }
    catch (e) {
        console.error('[seed-mocks] Failed to ensure mocks:', e);
        return false;
    }
}
if (require.main === module) {
    const ok = ensureE2EMocks();
    process.exit(ok ? 0 : 2);
}
