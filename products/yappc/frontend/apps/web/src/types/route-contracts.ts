/**
 * Route Module Contract Types
 *
 * Defines the expected shape for all route modules in the application.
 * Ensures consistent loader/action patterns and proper TypeScript support.
 */

import type { ComponentType, ReactNode } from 'react';
import type { LoaderFunctionArgs, ActionFunctionArgs } from 'react-router-dom';

/**
 * Standard route module contract
 * Each route module should export these named exports and a default component
 */
export interface RouteModule {
  /** The route component (default export) */
  Component: ComponentType;

  /** Optional data loader for the route */
  loader?: (args: LoaderFunctionArgs) => Promise<unknown> | any;

  /** Optional action handler for forms and mutations */
  action?: (args: ActionFunctionArgs) => Promise<unknown> | any;

  /** Optional error boundary component */
  ErrorBoundary?: ComponentType<{ error?: Error }>;

  /** Optional handle for route configuration */
  handle?: {
    crumb?: (data: unknown) => ReactNode;
    [key: string]: unknown;
  };
}

/**
 * Project-specific data shapes for loaders
 */
export interface ProjectSummary {
  id: string;
  name: string;
  primaryLanguage: string;
  serviceKind: 'library' | 'api' | 'app' | 'none';
  targets: string[];
  owner: {
    id: string;
    displayName: string;
  };
  createdAt: string;
  pipelineStatus?: {
    build?: string;
    test?: string;
    deploy?: string;
  };
  backlogSummary?: {
    themesCount: number;
    epicsCount: number;
    storiesCount: number;
    tasksCount: number;
  };
  canvasSnapshotId?: string;
  latestArtifact?: {
    version: string;
    artifactUrl: string;
  };
  gates?: Gate[];
}

/**
 * Standard gate interface for CI/CD pipeline integration
 */
export interface Gate {
  id: string;
  name: string;
  status: 'pending' | 'pass' | 'fail';
  detailsUrl?: string;
  lastUpdated?: string;
  metadata?: {
    vulnerabilitiesCount?: number;
    failingTestsCount?: number;
    secretCount?: number;
    policyViolations?: number;
    remediationHints?: string[];
  };
}

/**
 * Canonical gate IDs for consistent UI rendering
 */
export const CANONICAL_GATES = {
  SAST: 'sast',
  SCA: 'sca',
  SECRETS: 'secrets',
  IAC: 'iac',
  UNIT: 'unit',
  INTEGRATION: 'integration',
  E2E: 'e2e',
  PERFORMANCE: 'performance',
  A11Y: 'a11y',
  COMPLIANCE: 'compliance',
} as const;

/**
 *
 */
export type CanonicalGateId =
  (typeof CANONICAL_GATES)[keyof typeof CANONICAL_GATES];

/**
 * Route parameter types for type-safe access
 */
export interface RouteParams {
  workspaceId?: string;
  projectId?: string;
}

/**
 * Common loader return shapes
 */
export interface ProjectRouteData {
  project: ProjectSummary;
  gates: Gate[];
  permissions: string[];
}

/**
 *
 */
export interface BacklogRouteData extends ProjectRouteData {
  themes: unknown[];
  epics: unknown[];
  stories: unknown[];
  tasks: unknown[];
}

/**
 *
 */
export interface CanvasRouteData extends ProjectRouteData {
  canvasState: unknown;
  snapshots: unknown[];
}
