import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import type { PhrRole } from './auth/PhrAccessContext';
import { t } from './i18n/phrI18n';

export interface PhrRouteContract extends ProductRouteCapability {
  readonly personas?: readonly string[];
  readonly tiers?: readonly string[];
  readonly emergencyAction?: boolean;
  /**
   * When present and set to `true`, this route is behind a feature flag and
   * is not yet production-ready. The router renders a `FeatureFlagPage`
   * placeholder instead of the real page component.
   */
  readonly featureFlag?: boolean;
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
    path: '/release-readiness',
    label: t('route.releaseReadiness.label'),
    description: t('route.releaseReadiness.description'),
    group: t('route.group.governance'),
    minimumRole: 'admin',
    personas: ['admin'],
    tiers: ['clinical'],
    actions: ['view-release-readiness'],
    cards: ['evidence-freshness', 'fhir-runtime', 'consent-cache-proof', 'rollback-proof'],
  },
  {
    path: '/audit',
    label: t('route.audit.label'),
    description: t('route.audit.description'),
    group: t('route.group.governance'),
    minimumRole: 'admin',
    personas: ['admin'],
    tiers: ['clinical'],
    actions: ['view-audit-trail'],
    cards: ['audit-trail'],
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

  // ── Feature-flagged routes (not yet production-ready) ──────────────────────
  // These routes are visible in the IA but render a "coming soon" placeholder
  // until the backing service is promoted to production.

  {
    path: '/provider/dashboard',
    label: t('route.provider.dashboard.label'),
    description: t('route.provider.dashboard.description'),
    group: t('route.group.provider'),
    minimumRole: 'clinician',
    personas: ['clinician', 'admin'],
    tiers: ['clinical'],
    actions: ['view-provider-dashboard'],
    cards: ['provider-panel'],
    featureFlag: true,
  },
  {
    path: '/provider/patients',
    label: t('route.provider.patients.label'),
    description: t('route.provider.patients.description'),
    group: t('route.group.provider'),
    minimumRole: 'clinician',
    personas: ['clinician', 'admin'],
    tiers: ['clinical'],
    actions: ['view-patient-list'],
    cards: ['patient-roster'],
    featureFlag: true,
  },
  {
    path: '/caregiver/dependents',
    label: t('route.caregiver.dependents.label'),
    description: t('route.caregiver.dependents.description'),
    group: t('route.group.caregiver'),
    minimumRole: 'caregiver',
    personas: ['caregiver', 'admin'],
    tiers: ['core'],
    actions: ['view-dependents'],
    cards: ['dependent-summaries'],
    featureFlag: true,
  },
  {
    path: '/fchv/dashboard',
    label: t('route.fchv.dashboard.label'),
    description: t('route.fchv.dashboard.description'),
    group: t('route.group.fchv'),
    minimumRole: 'caregiver',
    personas: ['caregiver', 'admin'],
    tiers: ['core'],
    actions: ['view-fchv-dashboard'],
    cards: ['community-health-summary'],
    featureFlag: true,
  },
] as const satisfies readonly PhrRouteContract[];

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];
