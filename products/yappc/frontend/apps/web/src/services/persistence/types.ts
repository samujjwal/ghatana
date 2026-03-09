import type { CanvasState } from '../../components/canvas/workspace/canvasAtoms';

/**
 *
 */
export interface CanvasSnapshot {
  id: string;
  projectId: string;
  canvasId: string;
  version: string;
  timestamp: string;
  data: CanvasState;
  checksum?: string;
}

/**
 *
 */
export interface PersistenceSuccess<T> {
  success: true;
  data: T;
}

/**
 *
 */
export interface PersistenceFailure {
  success: false;
  error: string;
}

/**
 *
 */
export type PersistenceResult<T> = PersistenceSuccess<T> | PersistenceFailure;
