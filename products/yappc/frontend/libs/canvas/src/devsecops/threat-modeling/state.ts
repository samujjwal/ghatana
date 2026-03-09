/**
 * Threat Model State Management
 * 
 * Functions for managing threat model state, elements, flows, and boundaries.
 */

import type {
  ThreatModelState,
  ThreatModelConfig,
  ThreatElement,
  ThreatFlow,
  TrustBoundary,
  Threat,
  ThreatMitigation,
  ThreatStatus,
} from './types';

/**
 * Creates default threat model configuration
 */
export function createThreatModelConfig(
  overrides?: Partial<ThreatModelConfig>
): ThreatModelConfig {
  return {
    enableSTRIDE: true,
    enableLINDDUN: false,
    autoAnalyze: true,
    minSeverity: 'low',
    includeMitigations: true,
    ...overrides,
  };
}

/**
 * Creates initial threat model state
 */
export function createThreatModel(
  config?: Partial<ThreatModelConfig>
): ThreatModelState {
  return {
    config: createThreatModelConfig(config),
    elements: [],
    flows: [],
    boundaries: [],
    threats: [],
  };
}

/**
 * Adds an element to the threat model
 */
export function addElement(
  state: ThreatModelState,
  element: ThreatElement
): ThreatModelState {
  return {
    ...state,
    elements: [...state.elements, element],
  };
}

/**
 * Adds a flow to the threat model
 */
export function addFlow(
  state: ThreatModelState,
  flow: ThreatFlow
): ThreatModelState {
  // Check if flow crosses trust boundaries
  const sourceElement = state.elements.find(e => e.id === flow.sourceId);
  const targetElement = state.elements.find(e => e.id === flow.targetId);
  
  const crossesBoundary = sourceElement && targetElement
    ? sourceElement.trustZone !== targetElement.trustZone
    : false;
  
  const enhancedFlow: ThreatFlow = {
    ...flow,
    crossesBoundary,
  };
  
  return {
    ...state,
    flows: [...state.flows, enhancedFlow],
  };
}

/**
 * Adds a trust boundary to the model
 */
export function addBoundary(
  state: ThreatModelState,
  boundary: TrustBoundary
): ThreatModelState {
  return {
    ...state,
    boundaries: [...state.boundaries, boundary],
  };
}

/**
 * Adds a threat manually
 */
export function addThreat(
  state: ThreatModelState,
  threat: Omit<Threat, 'id' | 'discoveredAt' | 'updatedAt'>
): ThreatModelState {
  const now = new Date();
  const newThreat: Threat = {
    ...threat,
    id: `threat-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    discoveredAt: now,
    updatedAt: now,
  };
  
  return {
    ...state,
    threats: [...state.threats, newThreat],
  };
}

/**
 * Updates threat status
 */
export function updateThreatStatus(
  state: ThreatModelState,
  threatId: string,
  status: ThreatStatus
): ThreatModelState {
  return {
    ...state,
    threats: state.threats.map(threat =>
      threat.id === threatId
        ? { ...threat, status, updatedAt: new Date() }
        : threat
    ),
  };
}

/**
 * Adds mitigation to threat
 */
export function addMitigation(
  state: ThreatModelState,
  threatId: string,
  mitigation: Omit<ThreatMitigation, 'id'>
): ThreatModelState {
  const mitigationWithId: ThreatMitigation = {
    ...mitigation,
    id: `mitigation-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
  };
  
  return {
    ...state,
    threats: state.threats.map(threat =>
      threat.id === threatId
        ? {
            ...threat,
            mitigations: [...threat.mitigations, mitigationWithId],
            updatedAt: new Date(),
          }
        : threat
    ),
  };
}

/**
 * Updates mitigation status
 */
export function updateMitigationStatus(
  state: ThreatModelState,
  threatId: string,
  mitigationId: string,
  status: ThreatMitigation['status']
): ThreatModelState {
  return {
    ...state,
    threats: state.threats.map(threat =>
      threat.id === threatId
        ? {
            ...threat,
            mitigations: threat.mitigations.map(m =>
              m.id === mitigationId ? { ...m, status } : m
            ),
            updatedAt: new Date(),
          }
        : threat
    ),
  };
}

/**
 * Gets threats by severity
 */
export function getThreatsBySeverity(
  state: ThreatModelState,
  severity: Threat['severity']
): Threat[] {
  return state.threats.filter(t => t.severity === severity);
}

/**
 * Gets threats by status
 */
export function getThreatsByStatus(
  state: ThreatModelState,
  status: ThreatStatus
): Threat[] {
  return state.threats.filter(t => t.status === status);
}

/**
 * Gets threats by category
 */
export function getThreatsByCategory(
  state: ThreatModelState,
  category: Threat['category']
): Threat[] {
  return state.threats.filter(t => t.category === category);
}

/**
 * Calculates threat score (0-10)
 */
export function calculateThreatScore(threat: Threat): number {
  const severityScores = {
    critical: 10,
    high: 7.5,
    medium: 5,
    low: 2.5,
    info: 1,
  };
  
  const baseScore = severityScores[threat.severity];
  
  // Adjust based on mitigations
  const implementedMitigations = threat.mitigations.filter(
    m => m.status === 'implemented'
  ).length;
  const totalMitigations = threat.mitigations.length;
  
  const mitigationFactor = totalMitigations > 0
    ? 1 - (implementedMitigations / totalMitigations) * 0.5
    : 1;
  
  return Math.round(baseScore * mitigationFactor * 10) / 10;
}
