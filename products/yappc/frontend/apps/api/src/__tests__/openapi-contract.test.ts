import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

interface OpenApiDocument {
  openapi?: string;
  paths?: Record<string, Partial<Record<HttpMethod, unknown>>>;
  components?: {
    securitySchemes?: Record<string, unknown>;
    responses?: Record<string, unknown>;
    schemas?: Record<string, unknown>;
  };
}

type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete' | 'head' | 'options';

interface ClientEndpoint {
  readonly method: HttpMethod;
  readonly path: string;
}

function readApiIndexSource(): string {
  const thisFile = fileURLToPath(import.meta.url);
  const thisDir = path.dirname(thisFile);
  const indexPath = path.resolve(thisDir, '../index.ts');
  return readFileSync(indexPath, 'utf8');
}

function readWebApiClientSource(): string {
  const thisFile = fileURLToPath(import.meta.url);
  const thisDir = path.dirname(thisFile);
  const clientPath = path.resolve(thisDir, '../../../../web/src/lib/api/client.ts');
  return readFileSync(clientPath, 'utf8');
}

function loadCanonicalOpenApi(): OpenApiDocument {
  const thisFile = fileURLToPath(import.meta.url);
  const thisDir = path.dirname(thisFile);
  const openApiPath = path.resolve(thisDir, '../../../../../docs/api/openapi.yaml');
  const raw = readFileSync(openApiPath, 'utf8');
  return yaml.load(raw) as OpenApiDocument;
}

function wrapperToMethod(wrapperName: string): HttpMethod | null {
  if (wrapperName === 'get') return 'get';
  if (wrapperName === 'patch') return 'patch';
  if (wrapperName === 'del') return 'delete';
  if (wrapperName === 'post' || wrapperName === 'postPossiblyEmpty' || wrapperName === 'postWithHeaders') return 'post';
  return null;
}

function normalizeClientPath(pathLiteral: string): string {
  return pathLiteral
    .replace(/\$\{encodeQuery\([\s\S]*?\)\}/g, '')
    .replace(/\$\{qs\}/g, '')
    .replace(/\?\$\{[^}]+\}/g, '')
    .replace(/\$\{encodeURIComponent\(([^)]+)\)\}/g, (_match: string, expression: string) => {
      const name = expression.match(/([A-Za-z][A-Za-z0-9_]*)$/)?.[1] ?? 'param';
      return `{${name}}`;
    })
    .replace(/\$\{([A-Za-z][A-Za-z0-9_]*)\}/g, (_match: string, name: string) => `{${name}}`);
}

function expandDynamicEndpoint(endpoint: ClientEndpoint): readonly ClientEndpoint[] {
  if (!endpoint.path.includes('{decision}')) {
    return [endpoint];
  }

  return (['apply', 'reject', 'rollback'] as const).map((decision) => ({
    method: endpoint.method,
    path: endpoint.path.replace('{decision}', decision),
  }));
}

function extractClientWrapperEndpoints(source: string): readonly ClientEndpoint[] {
  const callPattern =
    /\b(get|post|postPossiblyEmpty|postWithHeaders|patch|del)\s*(?:<[^()]*>)?\s*\(\s*(['`])([\s\S]*?)\2/g;
  const endpoints: ClientEndpoint[] = [];

  for (const match of source.matchAll(callPattern)) {
    const method = wrapperToMethod(match[1] ?? '');
    const rawPath = match[3];
    if (!method || !rawPath || !rawPath.startsWith('/api')) {
      continue;
    }

    endpoints.push(...expandDynamicEndpoint({ method, path: normalizeClientPath(rawPath) }));
  }

  return endpoints;
}

function extractDirectFetchEndpoints(source: string): readonly ClientEndpoint[] {
  const fetchPattern = /\bfetch\(\s*(['`])([^'`]+)\1\s*,\s*\{[\s\S]*?method:\s*'([A-Z]+)'/g;
  const endpoints: ClientEndpoint[] = [];

  for (const match of source.matchAll(fetchPattern)) {
    const rawPath = match[2];
    const rawMethod = match[3]?.toLowerCase();
    if (!rawPath?.startsWith('/api') || !rawMethod) {
      continue;
    }

    endpoints.push({
      method: rawMethod as HttpMethod,
      path: normalizeClientPath(rawPath),
    });
  }

  return endpoints;
}

function extractCanonicalClientEndpoints(source: string): readonly ClientEndpoint[] {
  const endpointKeys = new Set<string>();
  const endpoints: ClientEndpoint[] = [];

  for (const endpoint of [...extractClientWrapperEndpoints(source), ...extractDirectFetchEndpoints(source)]) {
    const key = `${endpoint.method.toUpperCase()} ${endpoint.path}`;
    if (!endpointKeys.has(key)) {
      endpointKeys.add(key);
      endpoints.push(endpoint);
    }
  }

  return endpoints.sort((left, right) => {
    const pathComparison = left.path.localeCompare(right.path);
    return pathComparison === 0 ? left.method.localeCompare(right.method) : pathComparison;
  });
}

describe('OpenAPI Contract Compliance', () => {
  it('uses canonical OpenAPI 3.1 spec with required YAPPC paths', () => {
    const spec = loadCanonicalOpenApi();

    expect(spec.openapi).toBe('3.1.0');
    expect(spec.paths).toBeTruthy();

    const requiredPaths = [
      '/api/workspaces',
      '/api/projects',
      '/api/ai/suggest-artifacts',
    ] as const;

    for (const routePath of requiredPaths) {
      expect(spec.paths?.[routePath], `Missing required path in canonical spec: ${routePath}`).toBeTruthy();
    }
  });

  it('covers critical endpoint-method matrix in canonical contract', () => {
    const spec = loadCanonicalOpenApi();
    const pathMethods: Record<string, readonly string[]> = {
      '/health': ['get'],
      '/ready': ['get'],
      '/api/auth/login': ['post'],
      '/api/auth/refresh': ['post'],
      '/api/auth/logout': ['post'],
      '/api/auth/validate': ['get'],
      '/api/auth/me': ['get'],
      '/api/workspaces': ['get', 'post'],
      '/api/workspaces/{workspaceId}': ['get', 'patch', 'delete'],
      '/api/projects': ['get', 'post'],
      '/api/projects/dashboard-actions': ['get'],
      '/api/projects/{projectId}/dashboard-actions/execute': ['post'],
      '/api/projects/{projectId}': ['get', 'patch', 'delete'],
      '/api/v1/lifecycle/phases': ['get'],
      '/api/v1/lifecycle/advance': ['post'],
      '/api/v1/yappc/intent/capture': ['post'],
      '/api/v1/yappc/shape/derive': ['post'],
      '/api/v1/yappc/generate': ['post'],
      '/api/v1/yappc/generate/runs/{runId}/apply': ['post'],
      '/api/v1/yappc/generate/runs/{runId}/reject': ['post'],
      '/api/v1/yappc/generate/runs/{runId}/rollback': ['post'],
      '/api/v1/yappc/artifact/import-source': ['post'],
      '/api/v1/yappc/artifact/import-source/{jobId}': ['get'],
      '/api/v1/yappc/artifacts/{artifactId}/import-review-decisions': ['post'],
      '/api/v1/yappc/artifacts/{artifactId}/residual-islands/{residualIslandId}/review': ['post'],
      '/api/v1/yappc/artifacts/{artifactId}/residual-islands/{residualIslandId}/registry-candidates': ['post'],
      '/api/v1/yappc/run/rollback': ['post'],
      '/api/v1/yappc/run/promote': ['post'],
      '/api/v1/yappc/validate': ['post'],
      '/api/v1/approvals/pending': ['get'],
      '/api/v1/projects/{projectId}/suggestions': ['post'],
      '/api/v1/projects/{projectId}/suggestions/{suggestionId}': ['delete'],
      '/api/v1/workflows/templates': ['get'],
      '/api/v1/workflows/{templateId}/start': ['post'],
      '/api/v1/workflows/{runId}/status': ['get'],
      '/api/ai/suggest-artifacts': ['post'],
    };

    for (const [routePath, methods] of Object.entries(pathMethods)) {
      const entry = spec.paths?.[routePath];
      expect(entry, `Missing required endpoint in canonical contract: ${routePath}`).toBeTruthy();
      for (const method of methods) {
        expect(
          entry?.[method],
          `Missing required method in canonical contract: ${method.toUpperCase()} ${routePath}`
        ).toBeTruthy();
      }
    }
  });

  it('keeps the canonical frontend REST client covered by OpenAPI method/path contracts', () => {
    const spec = loadCanonicalOpenApi();
    const clientSource = readWebApiClientSource();
    const clientEndpoints = extractCanonicalClientEndpoints(clientSource);

    expect(clientEndpoints.length).toBeGreaterThan(0);

    const missingEndpoints = clientEndpoints.filter((endpoint) => !spec.paths?.[endpoint.path]?.[endpoint.method]);

    expect(
      missingEndpoints.map((endpoint) => `${endpoint.method.toUpperCase()} ${endpoint.path}`),
      'Every yappcApi REST method/path used by the frontend client must be documented in products/yappc/docs/api/openapi.yaml'
    ).toEqual([]);
  });

  it('enforces generated-client auth adapter usage in canonical client.ts', () => {
    const clientSource = readWebApiClientSource();

    expect(clientSource).toContain('GeneratedAuthService.login(');
    expect(clientSource).not.toContain('GeneratedAuthService.loginSession(');
    expect(clientSource).toContain('return adaptLoginResponse(response);');
  });

  it('enforces explicit workspace scope for projects.list generated call', () => {
    const clientSource = readWebApiClientSource();

    expect(clientSource).not.toContain('ProjectsService.listProjects(workspaceId || \'\')');
    expect(clientSource).toContain('workspaceId is required for projects.list');
  });

  it('keeps frontend telemetry and audit schemas aligned with the active REST client', () => {
    const spec = loadCanonicalOpenApi();
    const schemas = spec.components?.schemas as Record<string, { required?: string[]; properties?: Record<string, unknown> }>;
    const telemetryRoute = spec.paths?.['/api/telemetry/frontend-errors']?.post as {
      responses?: Record<string, unknown>;
    } | undefined;

    expect(schemas.FrontendErrorReport?.required).toEqual(['message', 'url', 'userAgent']);
    expect(schemas.FrontendErrorReport?.properties).toHaveProperty('componentName');
    expect(schemas.FrontendErrorReport?.properties).toHaveProperty('dataClassification');
    expect(schemas.FrontendErrorReport?.properties).toHaveProperty('tenantId');
    expect(schemas.FrontendErrorReport?.properties).toHaveProperty('userId');
    expect(schemas.FrontendErrorReport?.properties).not.toHaveProperty('level');
    expect(telemetryRoute?.responses).toHaveProperty('202');

    expect(spec.paths?.['/api/audit/events']?.post).toBeTruthy();
    expect(schemas.AuditEventRequest?.required).toEqual(['type', 'userId', 'projectId', 'flowStage', 'phase', 'description']);
    expect(schemas.AuditEventResponse?.properties).toHaveProperty('id');
    expect(schemas.AuditEventResponse?.properties).toHaveProperty('timestamp');
  });

  it('registers workspace, project, and ai routes under all compatibility prefixes', () => {
    const source = readApiIndexSource();

    expect(source).toContain("app.register(route, { prefix: '/api/v1' });");
    expect(source).toContain("{ prefix: '/api' }");
    expect(source).toContain("{ prefix: '/v1' }");

    expect(source).toContain('registerApiPrefixes(app, workspaceRoutes);');
    expect(source).toContain('registerApiPrefixes(app, projectRoutes);');
    expect(source).toContain('registerApiPrefixes(app, aiRoutes);');
    expect(source).toContain('registerApiPrefixes(app, importReviewRoutes);');
    expect(source).toContain('registerApiPrefixes(app, registryCandidateRoutes);');
  });

  it('defines suggest-artifacts schema shape in canonical OpenAPI contract', () => {
    const spec = loadCanonicalOpenApi();
    const route = spec.paths?.['/api/ai/suggest-artifacts'];
    expect(route).toBeTruthy();

    const operation = route?.post as {
      requestBody?: {
        content?: {
          'application/json'?: {
            schema?: {
              properties?: {
                context?: { required?: string[] };
              };
            };
          };
        };
      };
      responses?: {
        ['200']?: {
          content?: {
            'application/json'?: {
              schema?: {
                properties?: {
                  suggestions?: {
                    items?: {
                      properties?: Record<string, unknown>;
                    };
                  };
                };
              };
            };
          };
        };
      };
    };

    const contextRequired =
      operation.requestBody?.content?.['application/json']?.schema?.properties?.context?.required ?? [];
    expect(contextRequired).toContain('projectId');
    expect(contextRequired).toContain('currentPhase');
    expect(contextRequired).toContain('existingArtifacts');

    const suggestionProps =
      operation.responses?.['200']?.content?.['application/json']?.schema?.properties?.suggestions?.items?.properties;

    expect(suggestionProps).toBeTruthy();
    expect(suggestionProps).toHaveProperty('id');
    expect(suggestionProps).toHaveProperty('kind');
    expect(suggestionProps).toHaveProperty('title');
    expect(suggestionProps).toHaveProperty('summary');
    expect(suggestionProps).toHaveProperty('reasoning');
    expect(suggestionProps).toHaveProperty('confidence');
    expect(suggestionProps).toHaveProperty('confidenceType');
    expect(suggestionProps).toHaveProperty('confidenceReason');
    expect(suggestionProps).toHaveProperty('suggestedPayload');
  });

  it('defines security schemes and shared response/schema components', () => {
    const spec = loadCanonicalOpenApi();

    expect(spec.components).toBeTruthy();
    expect(spec.components?.securitySchemes).toHaveProperty('ApiKeyAuth');
    expect(spec.components?.securitySchemes).toHaveProperty('BearerAuth');

    expect(spec.components?.responses).toHaveProperty('BadRequest');
    expect(spec.components?.responses).toHaveProperty('Unauthorized');
    expect(spec.components?.responses).toHaveProperty('NotFound');

    expect(spec.components?.schemas).toHaveProperty('ErrorResponse');
    expect(spec.components?.schemas).toHaveProperty('Workspace');
    expect(spec.components?.schemas).toHaveProperty('Project');
    expect(spec.components?.schemas).toHaveProperty('ProjectDashboardAction');
    expect(spec.components?.schemas).toHaveProperty('ProjectDashboardActionsResponse');
    expect(spec.components?.schemas).toHaveProperty('ExecuteProjectDashboardActionRequest');
    expect(spec.components?.schemas).toHaveProperty('ExecuteProjectDashboardActionResponse');
  });

  it('documents generated artifact and diff provenance for generation responses', () => {
    const spec = loadCanonicalOpenApi();
    const schemas = spec.components?.schemas as Record<string, {
      required?: string[];
      properties?: Record<string, unknown>;
    }>;
    const generateResponse = schemas.GenerateArtifactsResponse;
    const regenerateResponse = schemas.RegenerateDiffResponse;
    const provenance = schemas.GenerateArtifactProvenance;
    const generatedFile = schemas.GeneratedFileDiff;
    const diffRegion = schemas.GenerateDiffRegion;

    expect(generateResponse?.properties).toHaveProperty('diff');
    expect(regenerateResponse?.properties).toHaveProperty('diff');
    expect(provenance?.required).toEqual([
      'requirementId',
      'phase',
      'canvasNodeId',
      'sourceArtifactId',
      'confidence',
      'approvingActorId',
    ]);
    expect(generatedFile?.required).toContain('provenance');
    expect(generatedFile?.required).toContain('diffRegions');
    expect(diffRegion?.required).toContain('provenance');
  });

  it('documents the mounted lifecycle enum with legacy compatibility aliases', () => {
    const spec = loadCanonicalOpenApi();
    const schemas = spec.components?.schemas as Record<string, { enum?: string[]; properties?: Record<string, unknown> }>;
    const lifecycleSchema = schemas.ProjectLifecyclePhase;

    expect(lifecycleSchema?.enum).toEqual([
      'INTENT',
      'SHAPE',
      'CONTEXT',
      'VALIDATE',
      'PLAN',
      'GENERATE',
      'EXECUTE',
      'RUN',
      'VERIFY',
      'OBSERVE',
      'LEARN',
      'IMPROVE',
      'EVOLVE',
      'INSTITUTIONALIZE',
    ]);
    expect(schemas.UpdateProjectRequest?.properties?.lifecyclePhase).toMatchObject({
      $ref: '#/components/schemas/ProjectLifecyclePhase',
    });
  });

  it('uses outcome-oriented wording for public recommendation operations', () => {
    const spec = loadCanonicalOpenApi();
    const paths = spec.paths as Record<string, Record<string, { summary?: string; responses?: Record<string, { description?: string }> }>>;
    const tags = (spec as { tags?: Array<{ name: string; description: string }> }).tags ?? [];

    expect(tags.find((tag) => tag.name === 'suggestions')?.description).toBe('Guided lifecycle recommendations');
    expect(paths['/api/v1/projects/{projectId}/suggestions']?.post?.summary).toBe('Generate lifecycle recommendations');
    expect(paths['/api/v1/projects/{projectId}/suggestions']?.post?.responses?.['200']?.description).toBe(
      'Lifecycle recommendations'
    );
    expect(paths['/api/v1/yappc/intent/analyze']?.post?.summary).toBe('Analyze captured intent');
    expect(paths['/api/devsecops/ai-insights']?.get?.summary).toBe('Get DevSecOps recommended insights');
    expect(paths['/api/projects/{projectId}/refresh-ai']?.post?.summary).toBe('Refresh project recommendations');
    expect(paths['/api/workspaces/{workspaceId}/refresh-ai']?.post?.summary).toBe('Refresh workspace recommendations');
  });
});
