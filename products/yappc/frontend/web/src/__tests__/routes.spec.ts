import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

describe('route configuration smoke', () => {
  it('uses canonical public route paths', () => {
    const routeConfigPath = path.resolve(process.cwd(), 'src/routes.ts');
    const content = fs.readFileSync(routeConfigPath, 'utf8');

    expect(content).toContain("route('workspaces', 'routes/app/workspaces.tsx')");
    expect(content).toContain("route('projects', 'routes/app/projects.tsx')");
    expect(content).toContain("route('p/:projectId', 'routes/app/project/_shell.tsx'");
    expect(content).not.toContain("route('app'");
  });
});
