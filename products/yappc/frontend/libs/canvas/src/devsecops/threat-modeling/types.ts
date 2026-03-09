/**
 * Threat Modeling Types
 * 
 * Type definitions for STRIDE/LINDDUN threat modeling.
 */

/**
 * STRIDE threat categories
 */
export type STRIDECategory =
  | 'spoofing'
  | 'tampering'
  | 'repudiation'
  | 'information-disclosure'
  | 'denial-of-service'
  | 'elevation-of-privilege';

/**
 * LINDDUN privacy threat categories
 */
export type LINDDUNCategory =
  | 'linkability'
  | 'identifiability'
  | 'non-repudiation'
  | 'detectability'
  | 'disclosure-of-information'
  | 'unawareness'
  | 'non-compliance';

/**
 * Combined threat category type
 */
export type ThreatCategory = STRIDECategory | LINDDUNCategory;

/**
 * Threat severity levels
 */
export type ThreatSeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';

/**
 * Threat status in workflow
 */
export type ThreatStatus = 'identified' | 'analyzing' | 'mitigating' | 'accepted' | 'resolved';

/**
 * Trust boundary zone types
 */
export type TrustZone =
  | 'internet'
  | 'dmz'
  | 'internal'
  | 'trusted'
  | 'restricted';

/**
 * Element types in threat model
 */
export type ThreatElementType =
  | 'process'
  | 'data-store'
  | 'data-flow'
  | 'external-entity';

/**
 * A trust boundary in the threat model
 */
export interface TrustBoundary {
  id: string;
  name: string;
  zone: TrustZone;
  elementIds: string[];
  description?: string;
}

/**
 * A threat element (node in threat model)
 */
export interface ThreatElement {
  id: string;
  type: ThreatElementType;
  name: string;
  trustZone: TrustZone;
  metadata?: Record<string, unknown>;
}

/**
 * A data flow between elements
 */
export interface ThreatFlow {
  id: string;
  sourceId: string;
  targetId: string;
  protocol?: string;
  encrypted: boolean;
  authenticated: boolean;
  crossesBoundary: boolean;
  metadata?: Record<string, unknown>;
}

/**
 * A suggested mitigation for a threat
 */
export interface ThreatMitigation {
  id: string;
  title: string;
  description: string;
  effort: 'low' | 'medium' | 'high';
  impact: 'low' | 'medium' | 'high';
  status: 'proposed' | 'in-progress' | 'implemented' | 'rejected';
  assignee?: string;
  dueDate?: Date;
}

/**
 * An identified threat
 */
export interface Threat {
  id: string;
  title: string;
  description: string;
  category: ThreatCategory;
  severity: ThreatSeverity;
  status: ThreatStatus;
  affectedElements: string[];
  affectedFlows: string[];
  mitigations: ThreatMitigation[];
  discoveredAt: Date;
  updatedAt: Date;
  cvss?: number;
  cwe?: string[];
  references?: string[];
}

/**
 * Threat modeling configuration
 */
export interface ThreatModelConfig {
  enableSTRIDE: boolean;
  enableLINDDUN: boolean;
  autoAnalyze: boolean;
  minSeverity: ThreatSeverity;
  includeMitigations: boolean;
}

/**
 * Entry in threat catalog
 */
export interface ThreatCatalogEntry {
  id: string;
  category: ThreatCategory;
  title: string;
  description: string;
  applicableToTypes: ThreatElementType[];
  applicableToFlows: boolean;
  severity: ThreatSeverity;
  commonMitigations: string[];
  cwe?: string[];
  references?: string[];
}

/**
 * Threat model state
 */
export interface ThreatModelState {
  config: ThreatModelConfig;
  elements: ThreatElement[];
  flows: ThreatFlow[];
  boundaries: TrustBoundary[];
  threats: Threat[];
  lastAnalyzed?: Date;
}

/**
 * Threat analysis result
 */
export interface ThreatAnalysisResult {
  newThreats: Threat[];
  updatedThreats: Threat[];
  resolvedThreats: Threat[];
  summary: {
    total: number;
    bySeverity: Record<ThreatSeverity, number>;
    byCategory: Record<string, number>;
    byStatus: Record<ThreatStatus, number>;
  };
}

/**
 * Export format for threat reports
 */
export type ThreatExportFormat = 'yaml' | 'csv' | 'json' | 'markdown';
