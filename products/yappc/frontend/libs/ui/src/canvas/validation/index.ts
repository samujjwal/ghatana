/**
 * Canvas Validation System
 *
 * Validation utilities and error reporting.
 *
 * @module canvas/validation
 */

// Canvas validator
export { CanvasValidator } from './CanvasValidator';
export type {
  ValidationSeverity,
  ValidationIssue,
  ValidationResult,
  ValidationOptions,
} from './CanvasValidator';

// Error panel
export { ErrorPanel } from './ErrorPanel';
export type { ErrorPanelProps } from './ErrorPanel';
