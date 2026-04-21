/**
 * Preview Host Types
 *
 * Type definitions for preview host components.
 *
 * @packageDocumentation
 */

import type { PageConfig } from '@yappc/config-schema';

export interface PreviewHostProps {
  config: PageConfig;
  onConfigChange?: (config: PageConfig) => void;
  readOnly?: boolean;
}

export interface ConfigRendererProps {
  config: PageConfig;
  mockData?: Record<string, unknown>;
}

export interface MockDataManagerProps {
  config: PageConfig;
  onDataChange?: (data: Record<string, unknown>) => void;
}

export interface AccessibilityCheckerProps {
  config: PageConfig;
}

export interface RegressionTestProps {
  config: PageConfig;
}

export type PreviewPanel = 'preview' | 'mock-data' | 'a11y' | 'visual-regression';
