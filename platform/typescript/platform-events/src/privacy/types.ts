/**
 * @fileoverview Privacy types and data classification for the platform.
 */

/** Data classification levels. */
export type DataClassification =
  | 'PUBLIC'
  | 'INTERNAL'
  | 'SENSITIVE'
  | 'CREDENTIALS'
  | 'REGULATED';

/** All valid data classifications. */
export const DATA_CLASSIFICATIONS: readonly DataClassification[] = [
  'PUBLIC',
  'INTERNAL',
  'SENSITIVE',
  'CREDENTIALS',
  'REGULATED',
] as const;

/** Validates a data classification. */
export function isValidDataClassification(
  classification: string
): classification is DataClassification {
  return DATA_CLASSIFICATIONS.includes(classification as DataClassification);
}

/** Privacy policy for data handling. */
export interface PrivacyPolicy {
  readonly dataMinimization: boolean;
  readonly retentionPeriod: number; // days
  readonly redactionRules: readonly RedactionRule[];
  readonly externalUseConsent: boolean;
  readonly gdprCompliant: boolean;
  readonly ccpaCompliant: boolean;
  readonly encryptionRequired: boolean;
  readonly allowedRegions: readonly string[];
}

/** Redaction rule for sensitive data. */
export interface RedactionRule {
  readonly fieldPattern: string; // regex or field path
  readonly classification: DataClassification;
  readonly redactionMethod: 'mask' | 'hash' | 'remove' | 'tokenize';
  readonly preserveLength?: boolean;
}

/** Metadata attached to components and documents for privacy. */
export interface PrivacyMetadata {
  readonly dataClassification: DataClassification;
  readonly piiFields: readonly string[];
  readonly containsCredentials: boolean;
  readonly requiresConsent: boolean;
  readonly retentionDays: number;
  readonly dataSubjectType?: 'user' | 'customer' | 'employee' | 'none';
}

/** Creates a default privacy policy. */
export function createDefaultPrivacyPolicy(): PrivacyPolicy {
  return {
    dataMinimization: true,
    retentionPeriod: 90,
    redactionRules: [],
    externalUseConsent: false,
    gdprCompliant: true,
    ccpaCompliant: true,
    encryptionRequired: true,
    allowedRegions: ['us', 'eu', 'apac'],
  };
}

/** Default redaction rules for common sensitive fields. */
export const DEFAULT_REDACTION_RULES: readonly RedactionRule[] = [
  {
    fieldPattern: 'password|token|secret|key|credential',
    classification: 'CREDENTIALS',
    redactionMethod: 'remove',
  },
  {
    fieldPattern: 'ssn|socialSecurity|taxId',
    classification: 'REGULATED',
    redactionMethod: 'mask',
    preserveLength: true,
  },
  {
    fieldPattern: 'email',
    classification: 'SENSITIVE',
    redactionMethod: 'hash',
  },
  {
    fieldPattern: 'phone|address|zip|postal',
    classification: 'SENSITIVE',
    redactionMethod: 'mask',
  },
];

/** Checks if a data classification requires explicit consent. */
export function requiresExplicitConsent(
  classification: DataClassification
): boolean {
  return classification === 'SENSITIVE' ||
         classification === 'CREDENTIALS' ||
         classification === 'REGULATED';
}

/** Gets the retention period for a data classification. */
export function getDefaultRetentionPeriod(
  classification: DataClassification
): number {
  switch (classification) {
    case 'PUBLIC':
      return 365;
    case 'INTERNAL':
      return 180;
    case 'SENSITIVE':
      return 90;
    case 'CREDENTIALS':
      return 30;
    case 'REGULATED':
      return 2555; // 7 years for regulatory compliance
    default:
      return 90;
  }
}
