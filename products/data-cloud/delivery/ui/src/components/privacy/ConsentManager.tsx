/**
 * ConsentManager - Re-export from shared platform component
 * 
 * @doc.type component
 * @doc.purpose Manage user consent for privacy-sensitive features
 * @doc.layer frontend
 * @doc.pattern Privacy Component
 */

export { ConsentManager, useConsent } from '@ghatana/domain-components/privacy';
export type { ConsentRecord, ConsentManagerProps } from '@ghatana/domain-components/privacy';
