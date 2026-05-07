/**
 * Boundary tests for feature-store UI coverage.
 *
 * The launcher exposes canonical feature-store endpoints, while the current UI
 * keeps feature exploration within consolidated experiences instead of a
 * dedicated standalone page.
 *
 * @doc.type test
 * @doc.purpose Assert current route boundary for feature-store UI coverage
 * @doc.layer frontend
 */
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const routesSource = readFileSync(path.resolve(__dirname, '../../routes.tsx'), 'utf8');
const canonicalOpenApi = readFileSync(path.resolve(__dirname, '../../../../../contracts/openapi/data-cloud.yaml'), 'utf8');

describe('FeaturePage — current route boundary', () => {
    it('does not define a standalone feature-store page route in the current consolidated IA', () => {
        expect(routesSource).not.toContain('FeatureStorePage');
        expect(routesSource).not.toContain("path: 'features'");
        expect(routesSource).toContain("path: 'data'");
    });

    it('still exposes the canonical launcher feature-store endpoints for shared contract coverage', () => {
        expect(canonicalOpenApi).toContain('/api/v1/features:');
        expect(canonicalOpenApi).toContain('/api/v1/features/{entityId}:');
    });
});
