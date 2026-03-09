#!/usr/bin/env node
/**
 * Dev seed script for local development.
 * Ensures a demo workspace and project exist in the MSW resolvers so
 * the `canvas-demo` route can be opened without needing backend.
 */
import { resolvers } from '../libs/mocks/src/resolvers';

/**
 *
 */
function ensureDemoMocks() {
  try {
    const r = resolvers as unknown as Record<string, unknown>;
    // @ts-ignore
    const mw: unknown[] = (r._internalWorkspaces as unknown[]) || [];
    // @ts-ignore
    const mp: unknown[] = (r._internalProjects as unknown[]) || [];

    const now = new Date().toISOString();
    const ws = { id: 'demo-ws', name: 'Demo Workspace (demo-ws)', description: 'Local demo workspace', ownerId: 'u-local', createdAt: now, updatedAt: now };
    const proj = {
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

    if (r.mockWorkspaces) {
      r.mockWorkspaces = [ ...(r.mockWorkspaces as Array<{ id: string }>).filter((w) => w.id !== ws.id), ws ];
    }
    if (r.mockProjects) {
      r.mockProjects = [ ...(r.mockProjects as Array<{ id: string }>).filter((p) => p.id !== proj.id), proj ];
    }

    // Fallback internal arrays
    r._internalWorkspaces = [ ...(mw as Array<{ id: string }>).filter((w) => w.id !== ws.id), ws ];
    r._internalProjects = [ ...(mp as Array<{ id: string }>).filter((p) => p.id !== proj.id), proj ];

    console.log('[seed-demo] Ensured demo-ws/demo-proj in resolvers');
    return true;
  } catch (e) {
    console.error('[seed-demo] Failed to ensure mocks:', e);
    return false;
  }
}

if (require.main === module) {
  const ok = ensureDemoMocks();
  process.exit(ok ? 0 : 2);
}
