// @ts-nocheck
/**
 * Application State Atoms
 *
 * Canonical location for app-level Jotai atoms.
 * Combines re-exports from @ghatana/yappc-canvas with stub atoms for missing ones.
 *
 * This file serves as the migration bridge - all app code should import from here
 * instead of directly from @ghatana/yappc-canvas.
 *
 * @doc.type module
 * @doc.purpose Application state management
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';

export interface Breadcrumb {
  id?: string;
  label: string;
  href?: string;
  path?: string;
  icon?: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
}

export interface Project {
  id: string;
  name: string;
  description?: string;
}

export interface Notification {
  id: string;
  type: 'success' | 'error' | 'warning' | 'info';
  message: string;
}

export interface BootstrapSession {
  id: string;
  projectId?: string;
  phase?: string;
  status?: string;
}

// Local type definitions for missing types
export interface SecurityAlert {
  id: string;
  title: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  status: 'open' | 'investigating' | 'resolved';
  createdAt?: string;
  resolvedAt?: string;
  description?: string;
}

const createWorkspaceService = () => ({
  getWorkspace: () => null,
  getWorkspaceMembers: () => [],
  addMember: async () => {},
  removeMember: async () => {},
  updateMemberRole: async () => {},
  updateMemberPersonas: async () => {},
});

// ============================================================================
// Re-export Existing Atoms
// ============================================================================

export const userAtom = atom(null);
export const isAuthenticatedAtom = atom(false);
export const breadcrumbsAtom = atom<Breadcrumb[]>([]);
export const currentProjectAtom = atom(null);
export const sidebarCollapsedAtom = atom(false);
export const notificationsAtom = atom([]);
export const unreadNotificationsCountAtom = atom(0);
export const globalSearchOpenAtom = atom(false);
globalSearchOpenAtom.debugLabel = 'globalSearchOpenAtom';
export const globalSearchQueryAtom = atom('');
globalSearchQueryAtom.debugLabel = 'globalSearchQueryAtom';
export const globalSearchResultsAtom = atom([]);
globalSearchResultsAtom.debugLabel = 'globalSearchResultsAtom';
export const globalSearchLoadingAtom = atom(false);
globalSearchLoadingAtom.debugLabel = 'globalSearchLoadingAtom';
export const bootstrapSessionAtom = atom(null);
export const savedSessionsAtom = atom([]);
export const selectedTemplateAtom = atom(null);
export const canvasNodesAtom = atom([]);
export const canvasEdgesAtom = atom([]);
export const uploadedDocsAtom = atom([]);
export const sprintStoriesAtom = atom([]);
export const selectedStoryAtom = atom(null);
export const sprintsAtom = atom([]);
export const alertsAtom = atom([]);
export const metricsAtom = atom([]);
export const securityAlertsAtom = atom([]);
export const getWorkspaceService = createWorkspaceService;

// ============================================================================
// Stub Atoms for Missing Exports
// ============================================================================
// These atoms don't exist in @ghatana/yappc-canvas but are imported by app code.
// Creating stubs here to unblock migration. TODO: Implement properly.

/** Stub: Current user atom (alias for userAtom) */
export const currentUserAtom = userAtom;

/** Stub: Active project atom (alias for currentProjectAtom) */
export const activeProjectAtom = currentProjectAtom;

/** Navigation history with path and timestamp */
export const navigationHistoryAtom = atom<
  Array<{ path: string; timestamp: number }>
>([]);

/** Projects list */
export const projectsAtom = atom<
  Array<{
    id: string;
    name: string;
    description: string;
    status: 'active' | 'paused' | 'archived';
    phase: string;
    progress: number;
    lastActivity: string;
    team: Array<{ name: string; avatar?: string }>;
  }>
>([]);

/** Project phase */
export const projectPhaseAtom = atom<string | null>(null);

/** Validation state for bootstrapping */
export const validationStateAtom = atom<{
  isValid: boolean;
  errors: string[];
  warnings: string[];
} | null>(null);

/** Active sprint */
export const activeSprintAtom = atom<{
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: 'planning' | 'active' | 'review' | 'completed' | 'cancelled';
  daysRemaining?: number;
  progress?: number;
} | null>(null);

/** Service health status */
export const serviceHealthAtom = atom<
  Array<{
    name: string;
    status: 'healthy' | 'degraded' | 'down';
    latency: number;
    uptime: number;
  }>
>([]);

/** Compliance status atom */
export const complianceStatusAtom = atom<{
  overall: number;
  overallScore?: number;
  frameworks: Array<{
    name: string;
    score: number;
    status: 'compliant' | 'partial' | 'non-compliant';
  }>;
} | null>(null);

/** Security score */
export const securityScoreAtom = atom<{
  overall: number;
  categories: {
    vulnerabilities: number;
    compliance: number;
    access: number;
  };
} | null>(null);

/** Incidents atom for operations dashboard */
export const incidentsAtom = atom<
  Array<{
    id: string;
    title: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'open' | 'investigating' | 'resolved';
    createdAt: string;
    startedAt?: string;
    resolvedAt?: string;
    assignee?: string;
    description?: string;
  }>
>([]);

/** Vulnerabilities atom for security dashboard */
export const vulnerabilitiesAtom = atom<
  Array<{
    id: string;
    title: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'open' | 'fixed' | 'mitigated' | 'in-progress' | 'resolved';
    package?: string;
    cve?: string;
    scanType?: string;
    affectedComponent?: string;
    description?: string;
    detectedAt?: string;
  }>
>([]);

/** Theme atom */
export const themeAtom = atom<'light' | 'dark' | 'system'>('system');

/** Conversation history atom for AI chat */
export const conversationHistoryAtom = atom<
  Array<{
    id: string;
    role: 'user' | 'assistant';
    content: string;
    timestamp: number;
  }>
>([]);

/** AI agent state atom */
export const aiAgentStateAtom = atom<{
  status: 'idle' | 'thinking' | 'responding';
  currentTask?: string;
  isProcessing?: boolean;
}>({ status: 'idle' });

/** Canvas state atom */
export const canvasStateAtom = atom<{
  zoom: number;
  pan: { x: number; y: number };
  selectedNodeIds: string[];
  nodes?: unknown[];
}>({
  zoom: 1,
  pan: { x: 0, y: 0 },
  selectedNodeIds: [],
});
