/**
 * Preview Host Types
 *
 * Type definitions for preview host components.
 *
 * @packageDocumentation
 */

export interface PreviewHostProps {
  config: import('@yappc/config-schema').PageConfig;
  onConfigChange?: (config: import('@yappc/config-schema').PageConfig) => void;
  readOnly?: boolean;
}

export interface ConfigRendererProps {
  config: import('@yappc/config-schema').PageConfig;
  mockData?: Record<string, unknown>;
}

export interface MockDataManagerProps {
  config: import('@yappc/config-schema').PageConfig;
  onDataChange?: (data: Record<string, unknown>) => void;
}

export interface AccessibilityCheckerProps {
  config: import('@yappc/config-schema').PageConfig;
}

export interface RegressionTestProps {
  config: import('@yappc/config-schema').PageConfig;
}

export type PreviewPanel = 'preview' | 'mock-data' | 'a11y' | 'visual-regression';
