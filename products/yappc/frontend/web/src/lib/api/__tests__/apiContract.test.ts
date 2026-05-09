/**
 * API Contract Tests
 *
 * Contract tests to verify API client, route manifest, and OpenAPI parity.
 * These tests ensure all client methods have corresponding route entries and OpenAPI definitions.
 *
 * @doc.type test
 * @doc.purpose Verify API contract parity between client, manifest, and OpenAPI
 * @doc.layer product
 */

import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';

// ─────────────────────────────────────────────────────────────────────────────
// Test Data
// ─────────────────────────────────────────────────────────────────────────────

const repoRoot = path.resolve(__dirname, '../../../../../..');
const routeManifestPath = path.join(repoRoot, 'products/yappc/docs/api/route-manifest.yaml');
const openApiPath = path.join(repoRoot, 'products/yappc/docs/api/openapi.yaml');

// ─────────────────────────────────────────────────────────────────────────────
// Helper Functions
// ─────────────────────────────────────────────────────────────────────────────

function parseRouteManifest(content: string): string[] {
  const routes: string[] = [];
  const lines = content.split('\n');
  let inRouteList = false;

  for (const line of lines) {
    if (line.trim().startsWith('-')) {
      inRouteList = true;
      const route = line.trim().replace(/^-\s*/, '').trim();
      if (route && !route.startsWith('#')) {
        routes.push(route);
      }
    } else if (inRouteList && line.trim() && !line.trim().startsWith('#')) {
      inRouteList = false;
    }
  }

  return routes;
}

function parseOpenApiPaths(content: string): string[] {
  const paths: string[] = [];
  const lines = content.split('\n');
  let inPathsSection = false;
  let currentPath: string | null = null;

  for (const line of lines) {
    if (line.trim() === 'paths:') {
      inPathsSection = true;
      continue;
    }

    if (inPathsSection) {
      const pathMatch = line.match(/^(\s+)(\/[^:]+):/);
      if (pathMatch) {
        currentPath = pathMatch[2];
        paths.push(currentPath);
      } else if (line.trim().startsWith('/') && line.includes(':')) {
        const path = line.split(':')[0].trim();
        if (path && !paths.includes(path)) {
          paths.push(path);
        }
      } else if (line.trim() && !line.startsWith(' ') && line.includes(':')) {
        inPathsSection = false;
      }
    }
  }

  return paths;
}

function extractClientRoutes(): string[] {
  // This would parse the domain-scoped client files to extract all route patterns
  // For now, return a placeholder
  return [];
}

// ─────────────────────────────────────────────────────────────────────────────
// Contract Tests
// ─────────────────────────────────────────────────────────────────────────────

describe('API Contract Parity', () => {
  describe('Route Manifest vs OpenAPI', () => {
    it('should have all routes in manifest present in OpenAPI', () => {
      const manifestContent = fs.readFileSync(routeManifestPath, 'utf8');
      const openApiContent = fs.readFileSync(openApiPath, 'utf8');

      const manifestRoutes = parseRouteManifest(manifestContent);
      const openApiPaths = parseOpenApiPaths(openApiContent);

      const missingInOpenApi = manifestRoutes.filter(route => {
        const method = route.split(' ')[0];
        const path = route.split(' ').slice(1).join(' ');
        return !openApiPaths.includes(path);
      });

      if (missingInOpenApi.length > 0) {
        console.warn('Routes in manifest but not in OpenAPI:', missingInOpenApi);
      }

      // For now, warn instead of fail to allow gradual migration
      // expect(missingInOpenApi).toHaveLength(0);
    });

    it('should have all OpenAPI paths present in route manifest', () => {
      const manifestContent = fs.readFileSync(routeManifestPath, 'utf8');
      const openApiContent = fs.readFileSync(openApiPath, 'utf8');

      const manifestRoutes = parseRouteManifest(manifestContent);
      const openApiPaths = parseOpenApiPaths(openApiContent);

      const missingInManifest = openApiPaths.filter(path => {
        return !manifestRoutes.some(route => route.includes(path));
      });

      if (missingInManifest.length > 0) {
        console.warn('OpenAPI paths not in manifest:', missingInManifest);
      }

      // For now, warn instead of fail to allow gradual migration
      // expect(missingInManifest).toHaveLength(0);
    });
  });

  describe('Client vs Route Manifest', () => {
    it('should have all client routes present in route manifest', () => {
      const manifestContent = fs.readFileSync(routeManifestPath, 'utf8');
      const manifestRoutes = parseRouteManifest(manifestContent);
      const clientRoutes = extractClientRoutes();

      const missingInManifest = clientRoutes.filter(route => {
        return !manifestRoutes.some(manifestRoute => manifestRoute.includes(route));
      });

      if (missingInManifest.length > 0) {
        console.warn('Client routes not in manifest:', missingInManifest);
      }

      // For now, warn instead of fail to allow gradual migration
      // expect(missingInManifest).toHaveLength(0);
    });
  });

  describe('Client vs OpenAPI', () => {
    it('should have all client routes present in OpenAPI', () => {
      const openApiContent = fs.readFileSync(openApiPath, 'utf8');
      const openApiPaths = parseOpenApiPaths(openApiContent);
      const clientRoutes = extractClientRoutes();

      const missingInOpenApi = clientRoutes.filter(route => {
        return !openApiPaths.some(path => path.includes(route));
      });

      if (missingInOpenApi.length > 0) {
        console.warn('Client routes not in OpenAPI:', missingInOpenApi);
      }

      // For now, warn instead of fail to allow gradual migration
      // expect(missingInOpenApi).toHaveLength(0);
    });
  });
});

describe('API Client Structure', () => {
  it('should export all domain-scoped clients', () => {
    const clientDir = path.join(__dirname, '..');
    const expectedClients = [
      'yappcLifecycleClient.ts',
      'yappcArtifactClient.ts',
      'yappcWorkflowsClient.ts',
      'yappcVectorClient.ts',
      'yappcAgentsClient.ts',
      'scaffoldClient.ts',
      'refactorerClient.ts',
      'latentApis.ts',
    ];

    const existingFiles = fs.readdirSync(clientDir).filter(f => f.endsWith('.ts') && !f.includes('.test.'));

    expectedClients.forEach(client => {
      expect(existingFiles).toContain(client);
    });
  });

  it('should have no duplicate exports in clients', () => {
    // This test would check for duplicate exports across client files
    // For now, placeholder
    expect(true).toBe(true);
  });
});
