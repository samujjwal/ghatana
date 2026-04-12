/**
 * @fileoverview Privacy types barrel export.
 */

export type {
  DataClassification,
  PrivacyPolicy,
  RedactionRule,
  PrivacyMetadata,
} from './types';

export {
  DATA_CLASSIFICATIONS,
  isValidDataClassification,
  createDefaultPrivacyPolicy,
  DEFAULT_REDACTION_RULES,
  requiresExplicitConsent,
  getDefaultRetentionPeriod,
} from './types';
