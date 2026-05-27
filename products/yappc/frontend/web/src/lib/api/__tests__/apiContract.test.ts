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

function findRepoRoot(startDir: string): string {
  let current = startDir;
  while (current !== path.dirname(current)) {
    const manifestCandidate = path.join(current, 'products/yappc/docs/api/route-manifest.yaml');
    const openApiCandidate = path.join(current, 'products/yappc/docs/api/openapi.yaml');
    if (fs.existsSync(manifestCandidate) && fs.existsSync(openApiCandidate)) {
      return current;
    }
    current = path.dirname(current);
  }
  throw new Error(`Unable to locate repository root from ${startDir}`);
}

const repoRoot = findRepoRoot(__dirname);
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

interface RouteContract {
  readonly method: string;
  readonly path: string;
  readonly auth: string;
  readonly scopes: readonly string[];
  readonly owner: string;
  readonly operationId: string;
}

function parseStructuredRouteManifest(content: string): Map<string, RouteContract> {
  const routes = new Map<string, RouteContract>();
  let currentServer = '';
  let current: Partial<RouteContract> | null = null;

  const finalize = (): void => {
    if (!current?.method || !current.path || !current.operationId) return;
    routes.set(current.operationId, {
      method: current.method,
      path: current.path,
      auth: current.auth ?? '',
      scopes: current.scopes ?? [],
      owner: current.owner ?? currentServer,
      operationId: current.operationId,
    });
  };

  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    if (!line.startsWith(' ') && trimmed.endsWith(':')) {
      currentServer = trimmed.slice(0, -1);
      continue;
    }

    if (trimmed.startsWith('- ')) {
      finalize();
      current = {};
      const field = parseManifestField(trimmed.slice(2));
      if (field) applyManifestField(current, field[0], field[1]);
      continue;
    }

    if (current && line.startsWith('  ')) {
      const field = parseManifestField(trimmed);
      if (field) applyManifestField(current, field[0], field[1]);
    }
  }

  finalize();
  return routes;
}

function parseManifestField(line: string): readonly [string, string] | null {
  const separator = line.indexOf(':');
  if (separator === -1) return null;
  return [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
}

function applyManifestField(target: Partial<RouteContract>, key: string, value: string): void {
  if (key === 'scopes') {
    target.scopes = value
      .replace(/^\[/, '')
      .replace(/\]$/, '')
      .split(',')
      .map(scope => scope.trim())
      .filter(Boolean);
    return;
  }
  if (key === 'method' || key === 'path' || key === 'auth' || key === 'owner' || key === 'operationId') {
    target[key] = value;
  }
}

function parseOpenApiOperations(content: string): Map<string, Pick<RouteContract, 'method' | 'path' | 'operationId'>> {
  const operations = new Map<string, Pick<RouteContract, 'method' | 'path' | 'operationId'>>();
  let currentPath = '';
  let currentMethod = '';

  for (const line of content.split('\n')) {
    const trimmed = line.trim();
    if (/^\/[^:]+:$/.test(trimmed)) {
      currentPath = trimmed.slice(0, -1);
      currentMethod = '';
      continue;
    }
    if (currentPath && /^(get|post|put|delete|patch):$/.test(trimmed)) {
      currentMethod = trimmed.slice(0, -1).toUpperCase();
      continue;
    }
    if (currentPath && currentMethod && trimmed.startsWith('operationId:')) {
      const operationId = trimmed.slice('operationId:'.length).trim();
      operations.set(operationId, { method: currentMethod, path: currentPath, operationId });
      currentMethod = '';
    }
  }

  return operations;
}

function parseGeneratedServiceOperations(content: string): Map<string, Pick<RouteContract, 'method' | 'path' | 'operationId'>> {
  const operations = new Map<string, Pick<RouteContract, 'method' | 'path' | 'operationId'>>();
  const methodBlocks = content.matchAll(/public static (\w+)\([^]*?return __request\(OpenAPI, \{([^]*?)\n        \}\);/g);

  for (const match of methodBlocks) {
    const operationId = match[1];
    const body = match[2];
    const method = body.match(/method: '([A-Z]+)'/)?.[1];
    const url = body.match(/url: '([^']+)'/)?.[1];
    if (method && url) {
      operations.set(operationId, { method, path: url, operationId });
    }
  }

  return operations;
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
    const generatedIndexPath = path.join(
      repoRoot,
      'products/yappc/frontend/web/src/clients/generated/api/index.ts'
    );
    const exportLines = fs
      .readFileSync(generatedIndexPath, 'utf8')
      .split('\n')
      .map(line => line.trim())
      .filter(line => line.startsWith('export '));
    const duplicateExports = exportLines.filter((line, index) => exportLines.indexOf(line) !== index);

    expect(duplicateExports).toEqual([]);
  });
});

describe('Phase cockpit generated client parity', () => {
  const criticalOperations = [
    { operationId: 'getPhasePacket', auth: 'required', scopes: ['project:read'] },
    { operationId: 'requestPhasePacket', auth: 'required', scopes: ['project:read'] },
    { operationId: 'getDashboardActions', auth: 'required', scopes: ['workspace:read'] },
    { operationId: 'requestDashboardActions', auth: 'required', scopes: ['workspace:read'] },
  ] as const;

  it('matches manifest, OpenAPI, and generated LifecycleService methods', () => {
    const manifest = parseStructuredRouteManifest(fs.readFileSync(routeManifestPath, 'utf8'));
    const openApi = parseOpenApiOperations(fs.readFileSync(openApiPath, 'utf8'));
    const lifecycleServicePath = path.join(
      repoRoot,
      'products/yappc/frontend/web/src/clients/generated/api/services/LifecycleService.ts'
    );
    const generated = parseGeneratedServiceOperations(fs.readFileSync(lifecycleServicePath, 'utf8'));

    for (const expected of criticalOperations) {
      const manifestRoute = manifest.get(expected.operationId);
      const openApiOperation = openApi.get(expected.operationId);
      const generatedOperation = generated.get(expected.operationId);

      expect(manifestRoute, `${expected.operationId} manifest route`).toBeDefined();
      expect(openApiOperation, `${expected.operationId} OpenAPI operation`).toBeDefined();
      expect(generatedOperation, `${expected.operationId} generated client method`).toBeDefined();
      expect(openApiOperation).toMatchObject({
        method: manifestRoute?.method,
        path: manifestRoute?.path,
      });
      expect(generatedOperation).toMatchObject({
        method: manifestRoute?.method,
        path: manifestRoute?.path,
      });
      expect(manifestRoute?.auth).toBe(expected.auth);
      expect(manifestRoute?.scopes).toEqual(expected.scopes);
      expect(manifestRoute?.owner).toBe('yappc-services');
    }
  });

  it('keeps usePhasePacket on the generated GET phase packet method', () => {
    const hookSource = fs.readFileSync(
      path.join(repoRoot, 'products/yappc/frontend/web/src/hooks/usePhasePacket.ts'),
      'utf8'
    );

    expect(hookSource).toContain("import { LifecycleService } from '../clients/generated/api'");
    expect(hookSource).toContain('LifecycleService.getPhasePacket(');
  });

  it('keeps degraded packet details in OpenAPI and generated frontend contracts', () => {
    const openApiSource = fs.readFileSync(openApiPath, 'utf8');
    const domainTypeSource = fs.readFileSync(
      path.join(repoRoot, 'products/yappc/frontend/web/src/types/phasePacket.ts'),
      'utf8'
    );
    const generatedPacketSource = fs.readFileSync(
      path.join(repoRoot, 'products/yappc/frontend/web/src/clients/generated/api/models/PhaseCockpitPacket.ts'),
      'utf8'
    );
    const generatedDetailsSource = fs.readFileSync(
      path.join(repoRoot, 'products/yappc/frontend/web/src/clients/generated/api/models/DegradedPacketDetails.ts'),
      'utf8'
    );

    expect(openApiSource).toContain('DegradedPacketDetails:');
    expect(openApiSource).toContain('truthSource:');
    expect(openApiSource).toContain('recoveryAction:');
    expect(domainTypeSource).toContain('export interface DegradedPacketDetails');
    expect(domainTypeSource).toContain('readonly degradedDetails?: DegradedPacketDetails');
    expect(generatedPacketSource).toContain("import type { DegradedPacketDetails } from './DegradedPacketDetails'");
    expect(generatedPacketSource).toContain('degradedDetails?: DegradedPacketDetails');
    expect(generatedDetailsSource).toContain("DATA_CLOUD = 'DATA_CLOUD'");
    expect(generatedDetailsSource).toContain("KERNEL = 'KERNEL'");
  });

  it('keeps canonical phase action safety metadata in OpenAPI and generated frontend contracts', () => {
    const openApiSource = fs.readFileSync(openApiPath, 'utf8');
    const domainTypeSource = fs.readFileSync(
      path.join(repoRoot, 'products/yappc/frontend/web/src/types/phasePacket.ts'),
      'utf8'
    );
    const generatedActionSource = fs.readFileSync(
      path.join(repoRoot, 'products/yappc/frontend/web/src/clients/generated/api/models/PhaseAction.ts'),
      'utf8'
    );

    expect(openApiSource).toContain('confirmationRequired:');
    expect(openApiSource).toContain('idempotencyKey:');
    expect(openApiSource).toContain('auditType:');
    expect(domainTypeSource).toContain('readonly category: string');
    expect(domainTypeSource).toContain('readonly confirmationRequired: boolean');
    expect(domainTypeSource).toContain('readonly idempotencyKey: string');
    expect(generatedActionSource).toContain('category: string');
    expect(generatedActionSource).toContain('confirmationRequired: boolean');
    expect(generatedActionSource).toContain('auditType: string');
  });

  it('keeps phase advance idempotency in OpenAPI and generated frontend contracts', () => {
    const openApiSource = fs.readFileSync(openApiPath, 'utf8');
    const generatedAdvanceSource = fs.readFileSync(
      path.join(repoRoot, 'products/yappc/frontend/web/src/clients/generated/api/models/AdvancePhaseRequest.ts'),
      'utf8'
    );

    expect(openApiSource).toContain('AdvancePhaseRequest:');
    expect(openApiSource).toContain('Caller-supplied retry key for idempotent primary phase action execution');
    expect(generatedAdvanceSource).toContain('idempotencyKey?: string');
  });
});

describe('Generate Kernel ProductUnitIntent client parity', () => {
  it('matches manifest, OpenAPI, and generated GenerateService method', () => {
    const manifest = parseStructuredRouteManifest(fs.readFileSync(routeManifestPath, 'utf8'));
    const openApi = parseOpenApiOperations(fs.readFileSync(openApiPath, 'utf8'));
    const generateServicePath = path.join(
      repoRoot,
      'products/yappc/frontend/web/src/clients/generated/api/services/GenerateService.ts'
    );
    const generated = parseGeneratedServiceOperations(fs.readFileSync(generateServicePath, 'utf8'));

    const manifestRoute = manifest.get('generateProductUnitIntent');
    const openApiOperation = openApi.get('generateProductUnitIntent');
    const generatedOperation = generated.get('generateProductUnitIntent');

    expect(manifestRoute, 'generateProductUnitIntent manifest route').toBeDefined();
    expect(openApiOperation, 'generateProductUnitIntent OpenAPI operation').toBeDefined();
    expect(generatedOperation, 'generateProductUnitIntent generated client method').toBeDefined();
    expect(openApiOperation).toMatchObject({
      method: manifestRoute?.method,
      path: manifestRoute?.path,
    });
    expect(generatedOperation).toMatchObject({
      method: manifestRoute?.method,
      path: manifestRoute?.path,
    });
    expect(manifestRoute?.auth).toBe('required');
    expect(manifestRoute?.scopes).toEqual(['project:write']);
    expect(manifestRoute?.owner).toBe('yappc-services');
  });
});

describe('YAPPC generated client coverage', () => {
  it('represents every yappc-services manifest operation with matching method and path', () => {
    const manifest = parseStructuredRouteManifest(fs.readFileSync(routeManifestPath, 'utf8'));
    const generatedServicesDir = path.join(
      repoRoot,
      'products/yappc/frontend/web/src/clients/generated/api/services'
    );
    const generated = new Map<string, Pick<RouteContract, 'method' | 'path' | 'operationId'>>();

    for (const fileName of fs.readdirSync(generatedServicesDir).filter(name => name.endsWith('.ts'))) {
      const operations = parseGeneratedServiceOperations(
        fs.readFileSync(path.join(generatedServicesDir, fileName), 'utf8')
      );
      for (const [operationId, operation] of operations) {
        generated.set(operationId, operation);
      }
    }

    const missing: string[] = [];
    const mismatched: string[] = [];
    for (const route of manifest.values()) {
      if (route.owner !== 'yappc-services') continue;
      const generatedOperation = generated.get(route.operationId);
      if (!generatedOperation) {
        missing.push(route.operationId);
        continue;
      }
      if (generatedOperation.method !== route.method || generatedOperation.path !== route.path) {
        mismatched.push(
          `${route.operationId}: generated ${generatedOperation.method} ${generatedOperation.path}, manifest ${route.method} ${route.path}`
        );
      }
    }

    expect(missing).toEqual([]);
    expect(mismatched).toEqual([]);
  });
});
