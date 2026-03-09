/**
 * Compliance Frameworks Utility
 *
 * Configuration and definitions for compliance frameworks.
 * Provides framework definitions, control lists, and assessment criteria.
 *
 * Frameworks supported:
 * - SOC 2 Type II
 * - ISO 27001
 * - HIPAA
 * - GDPR
 * - PCI DSS
 */

export enum ComplianceFramework {
  SOC2 = 'SOC2',
  ISO_27001 = 'ISO_27001',
  HIPAA = 'HIPAA',
  GDPR = 'GDPR',
  PCI_DSS = 'PCI_DSS',
}

export interface ControlDefinition {
  id: string;
  title: string;
  description: string;
  category: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  assessmentFrequency: number; // days
}

/**
 * SOC 2 Type II Controls
 *
 * Trust Service Criteria covering:
 * - CC: Common Criteria (all SOC 2 reports)
 * - A: Availability
 * - C: Confidentiality
 * - I: Integrity
 * - P: Privacy
 */
export const SOC2_CONTROLS: ControlDefinition[] = [
  {
    id: 'CC6.1',
    title: 'Logical and Physical Access Controls',
    description: 'Implement logical access controls to prevent unauthorized access',
    category: 'CC',
    severity: 'CRITICAL',
    assessmentFrequency: 90,
  },
  {
    id: 'CC6.2',
    title: 'Prior to Issuance of Physical or Logical Credentials',
    description: 'User identity and access rights are verified before credential issuance',
    category: 'CC',
    severity: 'HIGH',
    assessmentFrequency: 180,
  },
  {
    id: 'CC7.1',
    title: 'System Monitoring',
    description: 'System components and records are monitored and evaluated for effectiveness',
    category: 'CC',
    severity: 'HIGH',
    assessmentFrequency: 30,
  },
  {
    id: 'CC7.2',
    title: 'Log Records',
    description: 'System log records are maintained and retained to support auditing',
    category: 'CC',
    severity: 'CRITICAL',
    assessmentFrequency: 30,
  },
  {
    id: 'A1.1',
    title: 'Availability - Control Objectives',
    description: 'Availability objectives and responsibilities are defined',
    category: 'A',
    severity: 'MEDIUM',
    assessmentFrequency: 180,
  },
];

/**
 * ISO 27001 Controls
 *
 * Information Security Management System controls
 * Categories: A5-A18 (14 main categories)
 */
export const ISO_27001_CONTROLS: ControlDefinition[] = [
  {
    id: 'A5.1.1',
    title: 'Information Security Policies',
    description: 'Information security policies are documented and approved',
    category: 'A5',
    severity: 'CRITICAL',
    assessmentFrequency: 365,
  },
  {
    id: 'A6.1.1',
    title: 'Information Security Roles and Responsibilities',
    description: 'Roles and responsibilities are clearly defined and allocated',
    category: 'A6',
    severity: 'HIGH',
    assessmentFrequency: 365,
  },
  {
    id: 'A7.1.1',
    title: 'Access Control Policy',
    description: 'Access control policy is defined and implemented',
    category: 'A7',
    severity: 'CRITICAL',
    assessmentFrequency: 180,
  },
  {
    id: 'A8.1.1',
    title: 'Cryptography Policy',
    description: 'Cryptography is used to protect information',
    category: 'A8',
    severity: 'HIGH',
    assessmentFrequency: 180,
  },
];

/**
 * HIPAA Security Rule Controls
 *
 * Administrative, Physical, and Technical safeguards
 */
export const HIPAA_CONTROLS: ControlDefinition[] = [
  {
    id: 'AS.164.306',
    title: 'Administrative Safeguards',
    description: 'Manage security measures through policies and procedures',
    category: 'Administrative',
    severity: 'CRITICAL',
    assessmentFrequency: 180,
  },
  {
    id: 'PS.164.308',
    title: 'Physical Safeguards',
    description: 'Control access to data and IT assets',
    category: 'Physical',
    severity: 'HIGH',
    assessmentFrequency: 180,
  },
  {
    id: 'TS.164.312',
    title: 'Technical Safeguards',
    description: 'Technology-based protections',
    category: 'Technical',
    severity: 'CRITICAL',
    assessmentFrequency: 90,
  },
];

/**
 * Get controls for framework
 */
export function getFrameworkControls(framework: ComplianceFramework): ControlDefinition[] {
  switch (framework) {
    case ComplianceFramework.SOC2:
      return SOC2_CONTROLS;
    case ComplianceFramework.ISO_27001:
      return ISO_27001_CONTROLS;
    case ComplianceFramework.HIPAA:
      return HIPAA_CONTROLS;
    default:
      return [];
  }
}

/**
 * Get control by ID
 */
export function getControlRequirements(framework: ComplianceFramework, controlId: string): ControlDefinition | null {
  const controls = getFrameworkControls(framework);
  return controls.find((c) => c.id === controlId) || null;
}

/**
 * Calculate average compliance for framework
 *
 * GIVEN: List of control statuses
 * WHEN: calculateFrameworkCompliance called
 * THEN: Average compliance percentage returned
 */
export function calculateFrameworkCompliance(statuses: Array<{ status: string; score?: number }>): number {
  if (statuses.length === 0) return 0;

  const totalScore = statuses.reduce((sum, s) => sum + (s.score || 0), 0);
  return Math.round(totalScore / statuses.length);
}

/**
 * Get criticality-weighted compliance
 *
 * GIVEN: Controls with statuses and scores
 * WHEN: calculateWeightedCompliance called
 * THEN: Compliance percentage weighted by control severity
 */
export function calculateWeightedCompliance(
  framework: ComplianceFramework,
  statuses: Map<string, number>
): number {
  const controls = getFrameworkControls(framework);
  const severityWeights = { CRITICAL: 3, HIGH: 2, MEDIUM: 1, LOW: 0.5 };

  let totalWeight = 0;
  let weightedScore = 0;

  for (const control of controls) {
    const weight = severityWeights[control.severity as keyof typeof severityWeights];
    const score = statuses.get(control.id) || 0;
    totalWeight += weight;
    weightedScore += weight * score;
  }

  return totalWeight > 0 ? Math.round(weightedScore / totalWeight) : 0;
}
