import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

interface OpenApiDocument {
  openapi?: string;
  paths?: Record<string, Record<string, unknown>>;
  components?: {
    securitySchemes?: Record<string, unknown>;
    responses?: Record<string, unknown>;
    schemas?: Record<string, unknown>;
  };
}

function readApiIndexSource(): string {
  const thisFile = fileURLToPath(import.meta.url);
  const thisDir = path.dirname(thisFile);
  const indexPath = path.resolve(thisDir, '../index.ts');
  return readFileSync(indexPath, 'utf8');
}

function loadCanonicalOpenApi(): OpenApiDocument {
  const thisFile = fileURLToPath(import.meta.url);
  const thisDir = path.dirname(thisFile);
  const openApiPath = path.resolve(thisDir, '../../../../../docs/api/openapi.yaml');
  const raw = readFileSync(openApiPath, 'utf8');
  return yaml.load(raw) as OpenApiDocument;
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
      '/api/projects/{projectId}': ['get', 'patch', 'delete'],
      '/api/v1/lifecycle/phases': ['get'],
      '/api/v1/lifecycle/advance': ['post'],
      '/api/v1/yappc/intent/capture': ['post'],
      '/api/v1/yappc/shape/derive': ['post'],
      '/api/v1/yappc/generate': ['post'],
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

  it('registers workspace, project, and ai routes under all compatibility prefixes', () => {
    const source = readApiIndexSource();

    expect(source).toContain("app.register(route, { prefix: '/api' });");
    expect(source).toContain("app.register(route, { prefix: '/v1' });");
    expect(source).toContain("app.register(route, { prefix: '/api/v1' });");

    expect(source).toContain('registerApiPrefixes(app, workspaceRoutes);');
    expect(source).toContain('registerApiPrefixes(app, projectRoutes);');
    expect(source).toContain('registerApiPrefixes(app, aiRoutes);');
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
  });
});
