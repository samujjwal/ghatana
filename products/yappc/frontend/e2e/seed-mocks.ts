/*
  Node-side seed for mocks. This script imports the mocks resolvers and
  ensures a workspace id 'ws-1' and project id 'proj-1' exist so that
  Playwright tests that depend on these canonical ids will find them
  when MSW node server is used.
*/
import { resolvers } from '../libs/mocks/src/resolvers';

export function ensureE2EMocks() {
  try {
    // Replace or augment the internal arrays if present
    // @ts-ignore access internal arrays
    const mw: unknown[] = (resolvers as unknown)._internalWorkspaces || [];
    const mp: unknown[] = (resolvers as unknown)._internalProjects || [];

  // Create explicit entries (plain objects to avoid extra deps)
  const now = new Date().toISOString();
  const ws = { id: 'ws-1', name: 'E2E Workspace (ws-1)', description: 'E2E seeded workspace', ownerId: 'u-e2e', createdAt: now, updatedAt: now };
  const proj = { id: 'proj-1', workspaceId: 'ws-1', name: 'E2E Project (proj-1)', description: 'E2E seeded project', type: 'UI', targets: ['web'], status: 'active', createdAt: now, updatedAt: now };

    // Attach to resolvers for handlers to read
    // Some resolver implementations expose internal arrays; attempt both
    if ((resolvers as unknown).mockWorkspaces) {
      (resolvers as unknown).mockWorkspaces = [ ...(resolvers as unknown).mockWorkspaces.filter((w: unknown)=> w.id !== 'ws-1'), ws ];
    }
    if ((resolvers as unknown).mockProjects) {
      (resolvers as unknown).mockProjects = [ ...(resolvers as unknown).mockProjects.filter((p: unknown)=> p.id !== 'proj-1'), proj ];
    }

    // As a fallback, set internal placeholders the handlers may read
    (resolvers as unknown)._internalWorkspaces = [ ...(mw.filter((w: unknown)=> w.id !== 'ws-1')), ws ];
    (resolvers as unknown)._internalProjects = [ ...(mp.filter((p: unknown)=> p.id !== 'proj-1')), proj ];

    console.log('[seed-mocks] Ensured ws-1/proj-1 in resolvers');
    return true;
  } catch (e) {
    console.error('[seed-mocks] Failed to ensure mocks:', e);
    return false;
  }
}

if (require.main === module) {
  const ok = ensureE2EMocks();
  process.exit(ok ? 0 : 2);
}
