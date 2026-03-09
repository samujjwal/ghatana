/**
 * Threat Analysis Engine
 * 
 * Analyzes threat models and generates threats based on STRIDE/LINDDUN.
 */

import { getLINDDUNThreatCatalog } from './catalogs/linddun';
import { getSTRIDEThreatCatalog } from './catalogs/stride';

import type {
  ThreatModelState,
  ThreatAnalysisResult,
  Threat,
  ThreatCatalogEntry,
  ThreatSeverity,
  ThreatStatus,
} from './types';

/**
 * Analyzes threat model and generates threats
 */
export function analyzeThreatModel(
  state: ThreatModelState
): ThreatAnalysisResult {
  const newThreats: Threat[] = [];
  const now = new Date();
  
  // Get applicable catalogs
  const catalogs: ThreatCatalogEntry[] = [];
  if (state.config.enableSTRIDE) {
    catalogs.push(...getSTRIDEThreatCatalog());
  }
  if (state.config.enableLINDDUN) {
    catalogs.push(...getLINDDUNThreatCatalog());
  }
  
  // Analyze elements
  for (const element of state.elements) {
    for (const catalogEntry of catalogs) {
      if (catalogEntry.applicableToTypes.includes(element.type)) {
        // Check if threat already exists
        const existingThreat = state.threats.find(
          t => t.affectedElements.includes(element.id) &&
               t.category === catalogEntry.category
        );
        
        if (!existingThreat && catalogEntry.severity >= state.config.minSeverity) {
          const threat: Threat = {
            id: `threat-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            title: catalogEntry.title.replace('{element}', element.name),
            description: catalogEntry.description.replace('{element}', element.name),
            category: catalogEntry.category,
            severity: catalogEntry.severity,
            status: 'identified',
            affectedElements: [element.id],
            affectedFlows: [],
            mitigations: state.config.includeMitigations
              ? catalogEntry.commonMitigations.map((m, i) => ({
                  id: `mitigation-${i}`,
                  title: m,
                  description: '',
                  effort: 'medium' as const,
                  impact: 'medium' as const,
                  status: 'proposed' as const,
                }))
              : [],
            discoveredAt: now,
            updatedAt: now,
            cwe: catalogEntry.cwe,
            references: catalogEntry.references,
          };
          
          newThreats.push(threat);
        }
      }
    }
  }
  
  // Analyze flows
  for (const flow of state.flows) {
    for (const catalogEntry of catalogs) {
      if (catalogEntry.applicableToFlows) {
        // Special handling for boundary-crossing flows
        if (flow.crossesBoundary) {
          const threat: Threat = {
            id: `threat-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            title: 'Trust Boundary Crossing',
            description: `Data flow crosses trust boundary without proper protection`,
            category: 'information-disclosure',
            severity: flow.encrypted ? 'low' : 'high',
            status: 'identified',
            affectedElements: [],
            affectedFlows: [flow.id],
            mitigations: [],
            discoveredAt: now,
            updatedAt: now,
          };
          
          newThreats.push(threat);
        }
      }
    }
  }
  
  // Calculate summary
  const allThreats = [...state.threats, ...newThreats];
  const summary = {
    total: allThreats.length,
    bySeverity: {
      critical: allThreats.filter(t => t.severity === 'critical').length,
      high: allThreats.filter(t => t.severity === 'high').length,
      medium: allThreats.filter(t => t.severity === 'medium').length,
      low: allThreats.filter(t => t.severity === 'low').length,
      info: allThreats.filter(t => t.severity === 'info').length,
    },
    byCategory: allThreats.reduce((acc, t) => {
      acc[t.category] = (acc[t.category] || 0) + 1;
      return acc;
    }, {} as Record<string, number>),
    byStatus: {
      identified: allThreats.filter(t => t.status === 'identified').length,
      analyzing: allThreats.filter(t => t.status === 'analyzing').length,
      mitigating: allThreats.filter(t => t.status === 'mitigating').length,
      accepted: allThreats.filter(t => t.status === 'accepted').length,
      resolved: allThreats.filter(t => t.status === 'resolved').length,
    },
  };
  
  return {
    newThreats,
    updatedThreats: [],
    resolvedThreats: [],
    summary,
  };
}
