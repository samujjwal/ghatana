import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import type { PhrRole } from './auth/PhrAccessContext';
import { t } from './i18n/phrI18n';

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
    label: t('route.dashboard.label'),
    description: t('route.dashboard.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-patient-summary'],
    cards: ['patient-summary', 'care-plan', 'emergency-readiness'],
  },
  {
    path: '/records',
    label: t('route.records.label'),
    description: t('route.records.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-records'],
    cards: ['record-highlights', 'interop-status'],
  },
  {
    path: '/consents',
    label: t('route.consents.label'),
    description: t('route.consents.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['manage-consent'],
    cards: ['active-consent-grants', 'expiring-consents'],
  },
  {
    path: '/appointments',
    label: t('route.appointments.label'),
    description: t('route.appointments.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['schedule-visit'],
    cards: ['upcoming-appointments'],
  },
  {
    path: '/labs',
    label: t('route.labs.label'),
    description: t('route.labs.description'),
    group: t('route.group.clinical'),
    minimumRole: 'caregiver',
    personas: ['caregiver', 'clinician', 'admin'],
    tiers: ['clinical'],
    actions: ['review-lab-results'],
    cards: ['recent-lab-results'],
  },
  {
    path: '/medications',
    label: t('route.medications.label'),
    description: t('route.medications.description'),
    group: t('route.group.clinical'),
    minimumRole: 'caregiver',
    personas: ['caregiver', 'clinician', 'admin'],
    tiers: ['clinical'],
    actions: ['review-medications'],
    cards: ['medication-adherence'],
  },
  {
    path: '/emergency',
    label: t('route.emergency.label'),
    description: t('route.emergency.description'),
    group: t('route.group.governance'),
    minimumRole: 'clinician',
    personas: ['clinician', 'admin'],
    tiers: ['emergency'],
    emergencyAction: true,
    actions: ['break-glass-review'],
    cards: ['override-audit-timeline'],
  },
  {
    path: '/settings',
    label: t('route.settings.label'),
    description: t('route.settings.description'),
    group: t('route.group.governance'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['manage-profile-settings'],
    cards: ['profile-controls', 'integration-status'],
  },
  {
    path: '/records/:recordId',
    label: t('route.recordDetail.label'),
    description: t('route.recordDetail.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-records'],
    cards: [],
  },
] as const satisfies readonly PhrRouteContract[];

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];
