import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { useCanvasExport, type CanvasImportAuditEvent } from '../useCanvasExport';

function validCanvasJson(): string {
  return JSON.stringify({
    nodes: [
      {
        id: 'node-1',
        type: 'component',
        position: { x: 10, y: 20 },
        size: { width: 200, height: 120 },
        data: { label: 'Imported node' },
      },
    ],
    connections: [],
    drawings: [],
    viewport: { x: 0, y: 0, zoom: 1 },
    metadata: { version: '1.0.0' },
  });
}

describe('useCanvasExport import validation', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('surfaces invalid import errors without mutating the canvas', () => {
    const importFromJSON = vi.fn();
    const showFeedback = vi.fn();
    const recordImportAudit = vi.fn();
    const canvasRef = { current: document.createElement('div') };

    const { result } = renderHook(() =>
      useCanvasExport({
        canvas: {
          downloadJSON: vi.fn(),
          downloadSVG: vi.fn(),
          exportToPNG: vi.fn(),
          importFromJSON,
        },
        projectId: 'project-1',
        canvasRef,
        setExportMenuAnchor: vi.fn(),
        showFeedback,
        recordImportAudit,
      })
    );

    act(() => {
      result.current.handleValidatedImportJSON('{bad-json', 'bad.json');
    });

    expect(importFromJSON).not.toHaveBeenCalled();
    expect(result.current.importState).toEqual({
      status: 'error',
      message: 'Canvas import failed because the selected file is not valid JSON.',
      reason: 'invalid-json',
    });
    expect(showFeedback).toHaveBeenCalledWith(
      'Canvas import failed because the selected file is not valid JSON.',
      'error'
    );
    expect(recordImportAudit).toHaveBeenCalledWith({
      outcome: 'failed',
      projectId: 'project-1',
      sourceName: 'bad.json',
      failureReason: 'invalid-json',
      message: 'Canvas import failed because the selected file is not valid JSON.',
    } satisfies CanvasImportAuditEvent);
  });

  it('applies migrated canvas JSON and records an auditable success event', () => {
    const importFromJSON = vi.fn();
    const showFeedback = vi.fn();
    const recordImportAudit = vi.fn();
    const canvasRef = { current: document.createElement('div') };

    const { result } = renderHook(() =>
      useCanvasExport({
        canvas: {
          downloadJSON: vi.fn(),
          downloadSVG: vi.fn(),
          exportToPNG: vi.fn(),
          importFromJSON,
        },
        projectId: 'project-1',
        canvasRef,
        setExportMenuAnchor: vi.fn(),
        showFeedback,
        recordImportAudit,
      })
    );

    act(() => {
      result.current.handleValidatedImportJSON(validCanvasJson(), 'canvas.json');
    });

    expect(importFromJSON).toHaveBeenCalledTimes(1);
    const importedJson = importFromJSON.mock.calls[0]?.[0];
    expect(typeof importedJson).toBe('string');
    expect(JSON.parse(importedJson as string)).toMatchObject({
      nodes: [{ id: 'node-1' }],
      metadata: { version: '1.0.0' },
    });
    expect(result.current.importState.status).toBe('success');
    expect(showFeedback).toHaveBeenCalledWith(
      'Canvas import completed: 1 nodes and 0 connections imported.',
      'success'
    );
    expect(recordImportAudit).toHaveBeenCalledWith({
      outcome: 'success',
      projectId: 'project-1',
      sourceName: 'canvas.json',
      nodeCount: 1,
      connectionCount: 0,
      drawingCount: 0,
      message: 'Canvas import completed: 1 nodes and 0 connections imported.',
    } satisfies CanvasImportAuditEvent);
  });
});
