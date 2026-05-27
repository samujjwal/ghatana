import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

import { LEGACY_PROJECT_ROUTE_POLICIES } from '../routes/app/project/legacyProjectRoutePolicy';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function findRouteConfig(): string | null {
  const candidates = [
    path.resolve(__dirname, '../routes.ts'),
    path.resolve(process.cwd(), 'src/routes.ts'),
    path.resolve(process.cwd(), 'web/src/routes.ts'),
    path.resolve(process.cwd(), 'frontend/web/src/routes.ts'),
    path.resolve(process.cwd(), 'products/yappc/frontend/web/src/routes.ts'),
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return null;
}

describe('route configuration smoke', () => {
  it('uses canonical public route paths', () => {
    const routeConfigPath = findRouteConfig();
    expect(routeConfigPath).not.toBeNull();
    const content = fs.readFileSync(routeConfigPath!, 'utf8');

    expect(content).toContain("route('workspaces', 'routes/app/workspaces.tsx')");
    expect(content).toContain("route('projects', 'routes/app/projects.tsx')");
    expect(content).toContain("route('p/:projectId', 'routes/app/project/_shell.tsx'");
    expect(content).not.toContain("route('app'");
    expect(content).not.toContain("canvas-workspace");
  });

  it('keeps legacy project routes as explicit compatibility redirects', () => {
    const routeConfigPath = findRouteConfig();
    expect(routeConfigPath).not.toBeNull();
    const content = fs.readFileSync(routeConfigPath!, 'utf8');

    expect(content).toContain('Legacy compatibility deep links: intentionally redirect to canonical phase tabs.');
    expect(content).toContain("route('canvas', 'routes/app/project/canvas.tsx')");
    expect(content).toContain("route('preview', 'routes/app/project/preview.tsx')");
    expect(content).toContain("route('deploy', 'routes/app/project/deploy.tsx')");
    expect(content).toContain("route('lifecycle', 'routes/app/project/lifecycle.tsx')");
    expect(LEGACY_PROJECT_ROUTE_POLICIES.canvas.canonicalPhase).toBe('shape');
    expect(LEGACY_PROJECT_ROUTE_POLICIES.preview.canonicalPhase).toBe('observe');
    expect(LEGACY_PROJECT_ROUTE_POLICIES.deploy.canonicalPhase).toBe('run');
    expect(LEGACY_PROJECT_ROUTE_POLICIES.lifecycle.canonicalPhase).toBe('intent');
  });

  it('keeps authenticated YAPPC routes under the product and project shells', () => {
    const routeConfigPath = findRouteConfig();
    expect(routeConfigPath).not.toBeNull();
    const content = fs.readFileSync(routeConfigPath!, 'utf8');

    const productShellIndex = content.indexOf("layout('routes/_shell.tsx'");
    const projectShellIndex = content.indexOf("route('p/:projectId', 'routes/app/project/_shell.tsx'");
    const previewBuilderIndex = content.indexOf("route('preview/builder', 'routes/preview-builder.tsx')");
    const catchAllIndex = content.indexOf("route('*', 'routes/not-found.tsx')");

    expect(previewBuilderIndex).toBeGreaterThanOrEqual(0);
    expect(productShellIndex).toBeGreaterThan(previewBuilderIndex);
    expect(projectShellIndex).toBeGreaterThan(productShellIndex);
    expect(catchAllIndex).toBeGreaterThan(productShellIndex);

    [
      "route('workspaces', 'routes/app/workspaces.tsx')",
      "route('projects', 'routes/app/projects.tsx')",
      "route('kernel-health', 'routes/app/kernel-health.tsx')",
      "route('product-family', 'routes/app/product-family.tsx')",
      "route('admin/observability', 'routes/app/admin/observability.tsx')",
    ].forEach((routeEntry) => {
      const routeIndex = content.indexOf(routeEntry);
      expect(routeIndex).toBeGreaterThan(productShellIndex);
      expect(routeIndex).toBeLessThan(catchAllIndex);
    });

    [
      "route('intent', 'routes/app/project/intent.tsx')",
      "route('shape', 'routes/app/project/shape.tsx')",
      "route('validate', 'routes/app/project/validate.tsx')",
      "route('generate', 'routes/app/project/generate.tsx')",
      "route('run', 'routes/app/project/run.tsx')",
      "route('observe', 'routes/app/project/observe.tsx')",
      "route('learn', 'routes/app/project/learn.tsx')",
      "route('evolve', 'routes/app/project/evolve.tsx')",
    ].forEach((routeEntry) => {
      const routeIndex = content.indexOf(routeEntry);
      expect(routeIndex).toBeGreaterThan(projectShellIndex);
      expect(routeIndex).toBeLessThan(catchAllIndex);
    });
  });
});
