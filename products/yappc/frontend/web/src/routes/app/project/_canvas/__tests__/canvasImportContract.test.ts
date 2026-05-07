import { describe, expect, it } from 'vitest';

import {
  CANVAS_IMPORT_SCHEMA_VERSION,
  validateAndMigrateCanvasImport,
} from '../canvasImportContract';

function validCanvasJson(overrides: Record<string, unknown> = {}): string {
  return JSON.stringify({
    nodes: [
      {
        id: 'node-1',
        type: 'component',
        position: { x: 10, y: 20 },
        data: { label: 'Imported node' },
      },
    ],
    connections: [],
    metadata: {
      version: '1.0',
      exportDate: '2026-05-07T00:00:00.000Z',
    },
    ...overrides,
  });
}

describe('validateAndMigrateCanvasImport', () => {
  it('rejects invalid JSON with a user-facing error', () => {
    const result = validateAndMigrateCanvasImport('{not-json');

    expect(result).toEqual({
      ok: false,
      reason: 'invalid-json',
      message: 'Canvas import failed because the selected file is not valid JSON.',
    });
  });

  it('rejects files that do not match the canvas schema', () => {
    const result = validateAndMigrateCanvasImport(JSON.stringify({ nodes: 'bad' }));

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.reason).toBe('invalid-schema');
      expect(result.message).toContain('Canvas import failed');
    }
  });

  it('rejects duplicate node ids before applying the import', () => {
    const result = validateAndMigrateCanvasImport(
      validCanvasJson({
        nodes: [
          { id: 'duplicate', type: 'component', position: { x: 0, y: 0 }, data: {} },
          { id: 'duplicate', type: 'component', position: { x: 10, y: 10 }, data: {} },
        ],
      })
    );

    expect(result).toEqual({
      ok: false,
      reason: 'duplicate-node-id',
      message: 'Canvas import failed because node id "duplicate" appears more than once.',
    });
  });

  it('rejects dangling connections that reference missing nodes', () => {
    const result = validateAndMigrateCanvasImport(
      validCanvasJson({
        connections: [
          {
            id: 'edge-1',
            source: 'node-1',
            target: 'missing-node',
          },
        ],
      })
    );

    expect(result).toEqual({
      ok: false,
      reason: 'dangling-connection',
      message: 'Canvas import failed because connection "edge-1" references a missing node.',
    });
  });

  it('migrates valid legacy canvas JSON into the canonical import contract', () => {
    const result = validateAndMigrateCanvasImport(
      JSON.stringify({
        version: 'legacy-wrapper',
        data: JSON.parse(validCanvasJson()),
      })
    );

    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.document.metadata.version).toBe(CANVAS_IMPORT_SCHEMA_VERSION);
      expect(result.document.metadata.migratedFromVersion).toBe('1.0');
      expect(result.document.nodes[0]).toMatchObject({
        id: 'node-1',
        size: { width: 180, height: 100 },
        path: ['node-1'],
      });
      expect(result.document.viewport).toEqual({ x: 0, y: 0, zoom: 0.5 });
    }
  });
});
