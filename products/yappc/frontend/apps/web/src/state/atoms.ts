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
import {
  userAtom as _userAtom,
  isAuthenticatedAtom as _isAuthenticatedAtom,
  breadcrumbsAtom as _breadcrumbsAtom,
  currentProjectAtom as _currentProjectAtom,
  sidebarCollapsedAtom as _sidebarCollapsedAtom,
  notificationsAtom as _notificationsAtom,
  unreadNotificationsCountAtom as _unreadNotificationsCountAtom,
  globalSearchOpenAtom as _globalSearchOpenAtom,
  globalSearchQueryAtom as _globalSearchQueryAtom,
  globalSearchResultsAtom as _globalSearchResultsAtom,
  globalSearchLoadingAtom as _globalSearchLoadingAtom,
  bootstrapSessionAtom as _bootstrapSessionAtom,
  savedSessionsAtom as _savedSessionsAtom,
  selectedTemplateAtom as _selectedTemplateAtom,
  canvasNodesAtom as _canvasNodesAtom,
  canvasEdgesAtom as _canvasEdgesAtom,
  uploadedDocsAtom as _uploadedDocsAtom,
  sprintStoriesAtom as _sprintStoriesAtom,
  selectedStoryAtom as _selectedStoryAtom,
  sprintsAtom as _sprintsAtom,
  alertsAtom as _alertsAtom,
  metricsAtom as _metricsAtom,
  securityAlertsAtom as _securityAlertsAtom,
  getWorkspaceService as _getWorkspaceService,
} from '@ghatana/yappc-canvas';

// Re-export types
export type {
  Breadcrumb,
  User,
  Project,
  Notification,
  BootstrapSession,
} from '@ghatana/yappc-canvas';

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

// ============================================================================
// Re-export Existing Atoms
// ============================================================================

export const userAtom = _userAtom;
export const isAuthenticatedAtom = _isAuthenticatedAtom;
export const breadcrumbsAtom = _breadcrumbsAtom;
export const currentProjectAtom = _currentProjectAtom;
export const sidebarCollapsedAtom = _sidebarCollapsedAtom;
export const notificationsAtom = _notificationsAtom;
export const unreadNotificationsCountAtom = _unreadNotificationsCountAtom;
export const globalSearchOpenAtom = _globalSearchOpenAtom;
export const globalSearchQueryAtom = _globalSearchQueryAtom;
export const globalSearchResultsAtom = _globalSearchResultsAtom;
export const globalSearchLoadingAtom = _globalSearchLoadingAtom;
export const bootstrapSessionAtom = _bootstrapSessionAtom;
export const savedSessionsAtom = _savedSessionsAtom;
export const selectedTemplateAtom = _selectedTemplateAtom;
export const canvasNodesAtom = _canvasNodesAtom;
export const canvasEdgesAtom = _canvasEdgesAtom;
export const uploadedDocsAtom = _uploadedDocsAtom;
export const sprintStoriesAtom = _sprintStoriesAtom;
export const selectedStoryAtom = _selectedStoryAtom;
export const sprintsAtom = _sprintsAtom;
export const alertsAtom = _alertsAtom;
export const metricsAtom = _metricsAtom;
export const securityAlertsAtom = _securityAlertsAtom;
export const getWorkspaceService = _getWorkspaceService;

// ============================================================================
// Stub Atoms for Missing Exports
// ============================================================================
// These atoms don't exist in @ghatana/yappc-canvas but are imported by app code.
// Creating stubs here to unblock migration. TODO: Implement properly.

/** Stub: Current user atom (alias for userAtom) */
export const currentUserAtom = _userAtom;

/** Stub: Active project atom (alias for currentProjectAtom) */
export const activeProjectAtom = _currentProjectAtom;

/** Navigation history with path and timestamp */
export const navigationHistoryAtom = atom<Array<{ path: string; timestamp: number }>>([]);

/** Projects list */
export const projectsAtom = atom<Array<{
  id: string;
  name: string;
  description: string;
  status: 'active' | 'paused' | 'archived';
  phase: string;
  progress: number;
  lastActivity: string;
  team: Array<{ name: string; avatar?: string }>;
}>>([]);

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
export const serviceHealthAtom = atom<Array<{
  name: string;
  status: 'healthy' | 'degraded' | 'down';
  latency: number;
  uptime: number;
}>>([]);

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
export const incidentsAtom = atom<Array<{
  id: string;
  title: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  status: 'open' | 'investigating' | 'resolved';
  createdAt: string;
  startedAt?: string;
  resolvedAt?: string;
  assignee?: string;
  description?: string;
}>>([]);

/** Vulnerabilities atom for security dashboard */
export const vulnerabilitiesAtom = atom<Array<{
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
}>>([]);

/** Theme atom */
export const themeAtom = atom<'light' | 'dark' | 'system'>('system');

/** Conversation history atom for AI chat */
export const conversationHistoryAtom = atom<Array<{
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}>>([]);

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
