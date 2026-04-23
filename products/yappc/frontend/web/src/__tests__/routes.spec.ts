import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

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
});
