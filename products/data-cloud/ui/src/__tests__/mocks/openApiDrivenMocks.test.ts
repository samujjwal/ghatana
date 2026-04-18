import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import {
  COLLECTION_MOCK_OPENAPI_PATHS,
  DEPRECATED_COLLECTION_ROUTE_REDIRECTS,
} from '../../mocks/deprecatedRoutes';

const canonicalOpenApi = readFileSync(
  path.resolve(__dirname, '../../../../api/openapi.yaml'),
  'utf8'
);

const mswHandlersSource = readFileSync(
  path.resolve(__dirname, '../../mocks/handlers.ts'),
  'utf8'
);

const playwrightMocksSource = readFileSync(
  path.resolve(__dirname, '../../../e2e/helpers/api-mocks.ts'),
  'utf8'
);

describe('OpenAPI-driven collection mocks', () => {
  it('keeps canonical collection mock routes anchored to documented OpenAPI paths', () => {
    COLLECTION_MOCK_OPENAPI_PATHS.forEach((openApiPath) => {
      expect(canonicalOpenApi).toContain(`${openApiPath}:`);
    });
  });

  it('keeps deprecated collection aliases mapped to canonical documented routes', () => {
    DEPRECATED_COLLECTION_ROUTE_REDIRECTS.forEach(({ legacyPath, canonicalPath, openApiPath }) => {
      expect(canonicalOpenApi).toContain(`${openApiPath}:`);
      expect(mswHandlersSource).toContain(legacyPath.replace('{id}', ':id'));
      expect(playwrightMocksSource).toContain(legacyPath.replace('{id}', '*'));
      expect(mswHandlersSource).toContain(
        canonicalPath.replace('/api/v1', '${BASE}').replace('{id}', '${params.id}')
      );
    });
  });

  it('uses explicit deprecated-route warnings in both mock adapters', () => {
    expect(mswHandlersSource).toContain('warnDeprecatedRoute');
    expect(playwrightMocksSource).toContain('warnDeprecatedRoute');
    expect(mswHandlersSource).toContain('buildDeprecatedRouteHeaders');
    expect(playwrightMocksSource).toContain('buildDeprecatedRouteHeaders');
  });
});