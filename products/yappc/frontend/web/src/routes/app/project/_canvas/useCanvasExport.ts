/**
 * useCanvasExport Hook
 *
 * Manages export/import handlers for the canvas including
 * JSON, SVG, PNG export and JSON import.
 *
 * @doc.type hook
 * @doc.purpose Canvas export and import handlers
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useCallback, useState } from 'react';
import type { RefObject } from 'react';

import {
  validateAndMigrateCanvasImport,
  type CanvasImportFailureReason,
} from './canvasImportContract';

type CanvasExportFeedbackSeverity = 'success' | 'info' | 'warning' | 'error';

export type CanvasImportAuditOutcome = 'success' | 'failed';

export interface CanvasImportAuditEvent {
  readonly outcome: CanvasImportAuditOutcome;
  readonly projectId?: string;
  readonly sourceName?: string;
  readonly nodeCount?: number;
  readonly connectionCount?: number;
  readonly drawingCount?: number;
  readonly migratedFromVersion?: string;
  readonly failureReason?: CanvasImportFailureReason | 'import-apply-failed' | 'file-read-failed';
  readonly message: string;
}

export interface CanvasImportState {
  readonly status: 'idle' | 'success' | 'error';
  readonly message: string | null;
  readonly reason?: CanvasImportAuditEvent['failureReason'];
}

interface UseCanvasExportOptions {
  canvas: {
    downloadJSON: (filename: string) => void;
    downloadSVG: (filename: string) => void;
    exportToPNG: (element: HTMLElement) => Promise<string>;
    importFromJSON: (json: string) => void;
  };
  projectId: string | undefined;
  canvasRef: RefObject<HTMLDivElement | null>;
  setExportMenuAnchor: (v: null) => void;
  showFeedback?: (message: string, severity?: CanvasExportFeedbackSeverity) => void;
  recordImportAudit?: (event: CanvasImportAuditEvent) => void | Promise<void>;
}

export function useCanvasExport({
  canvas,
  projectId,
  canvasRef,
  setExportMenuAnchor,
  showFeedback,
  recordImportAudit,
}: UseCanvasExportOptions) {
  const [importState, setImportState] = useState<CanvasImportState>({
    status: 'idle',
    message: null,
  });

  const recordAudit = useCallback(
    (event: CanvasImportAuditEvent): void => {
      if (!recordImportAudit) {
        return;
      }

      void Promise.resolve(recordImportAudit(event)).catch((error: unknown) => {
        const detail = error instanceof Error ? error.message : 'Unknown audit failure.';
        showFeedback?.(
          `Canvas import ${event.outcome === 'success' ? 'completed' : 'failed'}, but the audit record could not be persisted: ${detail}`,
          'warning'
        );
        console.error('Canvas import audit failed:', error);
      });
    },
    [recordImportAudit, showFeedback]
  );

  const handleExportJSON = useCallback(() => {
    canvas.downloadJSON(`canvas-${projectId || 'export'}-${Date.now()}.json`);
    setExportMenuAnchor(null);
  }, [canvas, projectId, setExportMenuAnchor]);

  const handleExportSVG = useCallback(() => {
    canvas.downloadSVG(`canvas-${projectId || 'export'}-${Date.now()}.svg`);
    setExportMenuAnchor(null);
  }, [canvas, projectId, setExportMenuAnchor]);

  const handleExportPNG = useCallback(async () => {
    if (canvasRef.current) {
      try {
        const dataURL = await canvas.exportToPNG(canvasRef.current);
        const link = document.createElement('a');
        link.download = `canvas-${projectId || 'export'}-${Date.now()}.png`;
        link.href = dataURL;
        link.click();
      } catch (error) {
        const message =
          error instanceof Error
            ? `PNG export failed: ${error.message}`
            : 'PNG export failed.';
        showFeedback?.(message, 'error');
        console.error('PNG export failed:', error);
      }
    }
    setExportMenuAnchor(null);
  }, [canvas, projectId, canvasRef, setExportMenuAnchor, showFeedback]);

  const handleValidatedImportJSON = useCallback(
    (json: string, sourceName?: string): CanvasImportState => {
      const validation = validateAndMigrateCanvasImport(json);

      if (!validation.ok) {
        const nextState: CanvasImportState = {
          status: 'error',
          message: validation.message,
          reason: validation.reason,
        };
        setImportState(nextState);
        showFeedback?.(validation.message, 'error');
        recordAudit({
          outcome: 'failed',
          projectId,
          sourceName,
          failureReason: validation.reason,
          message: validation.message,
        });
        return nextState;
      }

      try {
        canvas.importFromJSON(JSON.stringify(validation.document));
      } catch (error) {
        const message =
          error instanceof Error
            ? `Canvas import failed while applying the validated document: ${error.message}`
            : 'Canvas import failed while applying the validated document.';
        const nextState: CanvasImportState = {
          status: 'error',
          message,
          reason: 'import-apply-failed',
        };
        setImportState(nextState);
        showFeedback?.(message, 'error');
        recordAudit({
          outcome: 'failed',
          projectId,
          sourceName,
          failureReason: 'import-apply-failed',
          message,
        });
        return nextState;
      }

      const message = `Canvas import completed: ${validation.document.nodes.length} nodes and ${validation.document.connections.length} connections imported.`;
      const nextState: CanvasImportState = {
        status: 'success',
        message,
      };
      setImportState(nextState);
      showFeedback?.(message, 'success');
      recordAudit({
        outcome: 'success',
        projectId,
        sourceName,
        nodeCount: validation.document.nodes.length,
        connectionCount: validation.document.connections.length,
        drawingCount: validation.document.drawings.length,
        ...(validation.document.metadata.migratedFromVersion
          ? { migratedFromVersion: validation.document.metadata.migratedFromVersion }
          : {}),
        message,
      });
      return nextState;
    },
    [canvas, projectId, recordAudit, showFeedback]
  );

  const handleImportJSON = useCallback(() => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = (event: Event) => {
      const target = event.target as HTMLInputElement | null;
      const file = target?.files?.[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (loadEvent: ProgressEvent<FileReader>) => {
          const json = loadEvent.target?.result;
          if (typeof json === 'string') {
            handleValidatedImportJSON(json, file.name);
          }
        };
        reader.onerror = () => {
          const message = `Canvas import failed because "${file.name}" could not be read.`;
          setImportState({
            status: 'error',
            message,
            reason: 'file-read-failed',
          });
          showFeedback?.(message, 'error');
          recordAudit({
            outcome: 'failed',
            projectId,
            sourceName: file.name,
            failureReason: 'file-read-failed',
            message,
          });
        };
        reader.readAsText(file);
      }
    };
    input.click();
    setExportMenuAnchor(null);
  }, [handleValidatedImportJSON, projectId, recordAudit, setExportMenuAnchor, showFeedback]);

  const clearImportState = useCallback((): void => {
    setImportState({
      status: 'idle',
      message: null,
    });
  }, []);

  return {
    importState,
    clearImportState,
    handleExportJSON,
    handleExportSVG,
    handleExportPNG,
    handleValidatedImportJSON,
    handleImportJSON,
  };
}
