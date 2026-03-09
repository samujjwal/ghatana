/**
 * STRIDE Threat Catalog
 * 
 * Comprehensive catalog of STRIDE threats with mitigations.
 */

import type { ThreatCatalogEntry } from '../types';

/**
 * Gets STRIDE threat catalog
 */
export function getSTRIDEThreatCatalog(): ThreatCatalogEntry[] {
  return [
    {
      id: 'stride-spoofing-auth',
      category: 'spoofing',
      title: 'Authentication Spoofing',
      description: 'Attacker may spoof identity to gain unauthorized access to {element}',
      applicableToTypes: ['process', 'external-entity'],
      applicableToFlows: false,
      severity: 'high',
      commonMitigations: [
        'Implement multi-factor authentication (MFA)',
        'Use certificate-based authentication',
        'Implement strong password policies',
      ],
      cwe: ['CWE-287', 'CWE-290'],
      references: ['OWASP A07:2021 - Identification and Authentication Failures'],
    },
    {
      id: 'stride-tampering-data',
      category: 'tampering',
      title: 'Data Tampering',
      description: 'Data in {element} may be modified by unauthorized parties',
      applicableToTypes: ['data-store', 'data-flow'],
      applicableToFlows: true,
      severity: 'high',
      commonMitigations: [
        'Implement cryptographic hashing for data integrity',
        'Use digital signatures',
        'Implement access controls',
      ],
      cwe: ['CWE-345', 'CWE-353'],
    },
    {
      id: 'stride-repudiation',
      category: 'repudiation',
      title: 'Action Repudiation',
      description: 'Users may deny performing actions in {element}',
      applicableToTypes: ['process'],
      applicableToFlows: false,
      severity: 'medium',
      commonMitigations: [
        'Implement comprehensive audit logging',
        'Use immutable audit trails with digital signatures',
        'Implement non-repudiation mechanisms',
      ],
      cwe: ['CWE-778'],
    },
    {
      id: 'stride-info-disclosure',
      category: 'information-disclosure',
      title: 'Information Disclosure',
      description: 'Sensitive information in {element} may be exposed',
      applicableToTypes: ['data-store', 'data-flow', 'process'],
      applicableToFlows: true,
      severity: 'high',
      commonMitigations: [
        'Encrypt data at rest',
        'Use TLS 1.3 for data in transit',
        'Implement principle of least privilege',
        'Use data classification and handling policies',
      ],
      cwe: ['CWE-200', 'CWE-311'],
      references: ['OWASP A01:2021 - Broken Access Control'],
    },
    {
      id: 'stride-dos',
      category: 'denial-of-service',
      title: 'Denial of Service',
      description: '{element} may be overwhelmed by excessive requests',
      applicableToTypes: ['process', 'data-store'],
      applicableToFlows: false,
      severity: 'medium',
      commonMitigations: [
        'Implement rate limiting',
        'Use load balancing',
        'Deploy DDoS protection',
        'Implement resource quotas',
      ],
      cwe: ['CWE-400', 'CWE-770'],
    },
    {
      id: 'stride-elevation',
      category: 'elevation-of-privilege',
      title: 'Privilege Elevation',
      description: 'Attacker may gain elevated privileges in {element}',
      applicableToTypes: ['process'],
      applicableToFlows: false,
      severity: 'critical',
      commonMitigations: [
        'Run processes with least privilege',
        'Implement input validation',
        'Use role-based access control (RBAC)',
        'Implement separation of duties',
      ],
      cwe: ['CWE-269', 'CWE-250'],
      references: ['OWASP A04:2021 - Insecure Design'],
    },
    {
      id: 'stride-boundary-crossing',
      category: 'information-disclosure',
      title: 'Trust Boundary Crossing',
      description: 'Data flow crosses trust boundary without proper protection',
      applicableToTypes: ['data-flow'],
      applicableToFlows: true,
      severity: 'medium',
      commonMitigations: [
        'Encrypt all boundary crossings',
        'Implement security gateway filtering',
        'Use mutual TLS authentication',
      ],
      cwe: ['CWE-923'],
    },
  ];
}
