/**
 * YAPPC Canvas Domain Configuration
 *
 * Product-specific lifecycle phases and persona roles for the YAPPC canvas.
 * Uses the platform domain injection API to avoid polluting @ghatana/canvas.
 *
 * @doc.type configuration
 * @doc.purpose YAPPC-specific canvas domain config
 * @doc.layer product
 * @doc.pattern Configuration
 */

import { createCanvasDomainConfig, type DomainPhase, type DomainRole } from '@ghatana/canvas';

// ============================================================================
// YAPPC Lifecycle Phases
// ============================================================================

export type YAPPCLifecyclePhase =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'RUN'
  | 'OBSERVE'
  | 'IMPROVE';

const YAPPC_PHASES: readonly DomainPhase[] = [
  { id: 'INTENT', label: 'Intent', primary: '#8e24aa', background: '#f3e5f5', text: '#4a148c' },
  { id: 'SHAPE', label: 'Shape', primary: '#1976d2', background: '#e3f2fd', text: '#0d47a1' },
  { id: 'VALIDATE', label: 'Validate', primary: '#00897b', background: '#e0f2f1', text: '#004d40' },
  { id: 'GENERATE', label: 'Generate', primary: '#388e3c', background: '#e8f5e9', text: '#1b5e20' },
  { id: 'RUN', label: 'Run', primary: '#f57c00', background: '#fff3e0', text: '#e65100' },
  { id: 'OBSERVE', label: 'Observe', primary: '#5e35b1', background: '#ede7f6', text: '#311b92' },
  { id: 'IMPROVE', label: 'Improve', primary: '#c62828', background: '#ffebee', text: '#b71c1c' },
] as const;

// ============================================================================
// YAPPC Persona Roles
// ============================================================================

export type YAPPCPersonaRole =
  | 'product_owner'
  | 'architect'
  | 'developer'
  | 'qa_engineer'
  | 'devops_engineer'
  | 'security_engineer'
  | 'ux_designer'
  | 'data_engineer'
  | 'business_analyst';

const YAPPC_ROLES: readonly DomainRole[] = [
  { id: 'product_owner', displayName: 'Product Owner', icon: '📋', color: '#8e24aa' },
  { id: 'architect', displayName: 'Architect', icon: '🏗️', color: '#1976d2' },
  { id: 'developer', displayName: 'Developer', icon: '💻', color: '#388e3c' },
  { id: 'qa_engineer', displayName: 'QA Engineer', icon: '✓', color: '#f57c00' },
  { id: 'devops_engineer', displayName: 'DevOps Engineer', icon: '⚙️', color: '#c62828' },
  { id: 'security_engineer', displayName: 'Security Engineer', icon: '🔒', color: '#5e35b1' },
  { id: 'ux_designer', displayName: 'UX Designer', icon: '🎨', color: '#00897b' },
  { id: 'data_engineer', displayName: 'Data Engineer', icon: '📊', color: '#0288d1' },
  { id: 'business_analyst', displayName: 'Business Analyst', icon: '📈', color: '#7b1fa2' },
] as const;

// ============================================================================
// Exported Config
// ============================================================================

/**
 * YAPPC canvas domain configuration.
 * Use this instead of platform chrome.tsx PERSONA_CONFIGS / PHASE_COLORS.
 */
export const yappcCanvasDomain = createCanvasDomainConfig<YAPPCLifecyclePhase, YAPPCPersonaRole>({
  phases: YAPPC_PHASES,
  roles: YAPPC_ROLES,
  defaultPhase: 'SHAPE',
  defaultRoles: ['architect'],
});

/**
 * @deprecated Use yappcCanvasDomain.getPhaseColors() instead.
 * Legacy re-export for backward compatibility.
 */
export const PHASE_COLORS = Object.fromEntries(
  YAPPC_PHASES.map((p) => [p.id, { primary: p.primary, background: p.background, text: p.text }])
) as Record<YAPPCLifecyclePhase, { primary: string; background: string; text: string }>;

/**
 * @deprecated Use yappcCanvasDomain.getRoleConfig() instead.
 * Legacy re-export for backward compatibility.
 */
export const PERSONA_CONFIGS = Object.fromEntries(
  YAPPC_ROLES.map((r) => [r.id, { role: r.id, displayName: r.displayName, icon: r.icon, color: r.color, quickActions: [] }])
) as Record<YAPPCPersonaRole, { role: string; displayName: string; icon: string; color: string; quickActions: unknown[] }>;
