/**
 * LINDDUN Threat Catalog
 * 
 * Privacy threat catalog based on LINDDUN methodology.
 */

import type { ThreatCatalogEntry } from '../types';

/**
 * Gets LINDDUN threat catalog
 */
export function getLINDDUNThreatCatalog(): ThreatCatalogEntry[] {
  return [
    {
      id: 'linddun-linkability',
      category: 'linkability',
      title: 'Data Linkability',
      description: 'Data in {element} can be linked across contexts',
      applicableToTypes: ['data-store', 'process'],
      applicableToFlows: false,
      severity: 'medium',
      commonMitigations: [
        'Use pseudonymization',
        'Implement data minimization',
        'Use separate identifiers per context',
      ],
    },
    {
      id: 'linddun-identifiability',
      category: 'identifiability',
      title: 'User Identifiability',
      description: 'Users can be identified from data in {element}',
      applicableToTypes: ['data-store', 'process'],
      applicableToFlows: false,
      severity: 'high',
      commonMitigations: [
        'Implement anonymization',
        'Use k-anonymity techniques',
        'Remove or hash personally identifiable information',
      ],
    },
    {
      id: 'linddun-non-repudiation',
      category: 'non-repudiation',
      title: 'Excessive Non-Repudiation',
      description: 'Excessive proof of actions in {element} reduces privacy',
      applicableToTypes: ['process'],
      applicableToFlows: false,
      severity: 'low',
      commonMitigations: [
        'Balance audit requirements with privacy',
        'Use privacy-preserving audit mechanisms',
        'Implement data retention policies',
      ],
    },
    {
      id: 'linddun-detectability',
      category: 'detectability',
      title: 'Activity Detectability',
      description: 'User activities in {element} are detectable',
      applicableToTypes: ['process', 'data-flow'],
      applicableToFlows: true,
      severity: 'medium',
      commonMitigations: [
        'Use traffic padding',
        'Implement timing obfuscation',
        'Use onion routing or similar techniques',
      ],
    },
    {
      id: 'linddun-disclosure',
      category: 'disclosure-of-information',
      title: 'Information Disclosure',
      description: 'Personal information in {element} may be disclosed',
      applicableToTypes: ['data-store', 'data-flow', 'process'],
      applicableToFlows: true,
      severity: 'high',
      commonMitigations: [
        'Implement access controls',
        'Use encryption',
        'Apply data classification',
        'Implement privacy by design',
      ],
    },
    {
      id: 'linddun-unawareness',
      category: 'unawareness',
      title: 'User Unawareness',
      description: 'Users are unaware of data processing in {element}',
      applicableToTypes: ['process'],
      applicableToFlows: false,
      severity: 'medium',
      commonMitigations: [
        'Provide clear privacy notices',
        'Implement transparency mechanisms',
        'Use privacy dashboards',
        'Obtain informed consent',
      ],
    },
    {
      id: 'linddun-non-compliance',
      category: 'non-compliance',
      title: 'Privacy Regulation Non-Compliance',
      description: '{element} may not comply with privacy regulations',
      applicableToTypes: ['process', 'data-store'],
      applicableToFlows: false,
      severity: 'critical',
      commonMitigations: [
        'Implement GDPR/CCPA compliance',
        'Conduct privacy impact assessments',
        'Implement data subject rights',
        'Maintain data processing records',
      ],
    },
  ];
}
