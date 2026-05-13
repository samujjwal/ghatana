import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import type { PhrRole } from './auth/PhrAccessContext';

export interface PhrRouteContract extends ProductRouteCapability {
  readonly personas?: readonly string[];
  readonly tiers?: readonly string[];
  readonly emergencyAction?: boolean;
}

export const PHR_ROLE_ORDER: Readonly<Record<PhrRole, number>> = {
  patient: 0,
  caregiver: 1,
  clinician: 2,
  admin: 3,
};

export const phrRouteAccess = createRouteAccessEvaluator(PHR_ROLE_ORDER);

export function isRouteAllowedForRole(route: Pick<PhrRouteContract, 'minimumRole'>, role: PhrRole): boolean {
  return phrRouteAccess.isRouteAllowed(route, role);
}

export const phrRouteContracts = [
  {
    path: '/dashboard',
    label: 'Dashboard',
    description: 'Overview of care, consent, and alerts.',
    group: 'Care',
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-patient-summary'],
    cards: ['patient-summary', 'care-plan', 'emergency-readiness'],
  },
  {
    path: '/records',
    label: 'Records',
    description: 'FHIR-native record browser and summaries.',
    group: 'Care',
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-records'],
    cards: ['record-highlights', 'interop-status'],
  },
  {
    path: '/consents',
    label: 'Consents',
    description: 'Consent grants and revocations.',
    group: 'Care',
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['manage-consent'],
    cards: ['active-consent-grants', 'expiring-consents'],
  },
  {
    path: '/appointments',
    label: 'Appointments',
    description: 'Scheduling and visit coordination.',
    group: 'Care',
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['schedule-visit'],
    cards: ['upcoming-appointments'],
  },
  {
    path: '/labs',
    label: 'Labs',
    description: 'Lab result review and follow-up.',
    group: 'Clinical',
    minimumRole: 'caregiver',
    personas: ['caregiver', 'clinician', 'admin'],
    tiers: ['clinical'],
    actions: ['review-lab-results'],
    cards: ['recent-lab-results'],
  },
  {
    path: '/medications',
    label: 'Medications',
    description: 'Medication history and adherence workflows.',
    group: 'Clinical',
    minimumRole: 'caregiver',
    personas: ['caregiver', 'clinician', 'admin'],
    tiers: ['clinical'],
    actions: ['review-medications'],
    cards: ['medication-adherence'],
  },
  {
    path: '/emergency',
    label: 'Emergency',
    description: 'Emergency override and review workflow.',
    group: 'Governance',
    minimumRole: 'clinician',
    personas: ['clinician', 'admin'],
    tiers: ['emergency'],
    emergencyAction: true,
    actions: ['break-glass-review'],
    cards: ['override-audit-timeline'],
  },
  {
    path: '/settings',
    label: 'Settings',
    description: 'Profile, integrations, and environment settings.',
    group: 'Governance',
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['manage-profile-settings'],
    cards: ['profile-controls', 'integration-status'],
  },
  {
    path: '/records/:recordId',
    label: 'Record detail',
    description: 'FHIR resource rendering for a specific record.',
    group: 'Care',
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-records'],
    cards: [],
  },
] as const satisfies readonly PhrRouteContract[];

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];
