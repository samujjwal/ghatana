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
  /**
   * Route lifecycle metadata for tracking introduction, deprecation, and removal.
   */
  readonly lifecycle?: {
    readonly introducedAt: string; // Version or date when route was introduced
    readonly stability: 'stable' | 'experimental' | 'deprecated';
    readonly deprecatedAt?: string; // Version or date when route was deprecated
    readonly removedAt?: string; // Version or date when route was removed
    readonly migrationNotes?: string; // Notes for migrating from this route
  };
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/emergency/reviews',
    label: t('route.emergency.label'),
    description: t('route.emergency.description'),
    group: t('route.group.governance'),
    minimumRole: 'admin',
    personas: ['admin'],
    tiers: ['emergency'],
    actions: ['review-emergency-access'],
    cards: ['pending-reviews', 'overdue-reviews'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/profile',
    label: t('route.profile.label'),
    description: t('route.profile.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-profile', 'edit-profile'],
    cards: ['profile-summary'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/timeline',
    label: t('route.timeline.label'),
    description: t('route.timeline.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-timeline'],
    cards: ['health-timeline'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/conditions',
    label: t('route.conditions.label'),
    description: t('route.conditions.description'),
    group: t('route.group.clinical'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-conditions'],
    cards: ['condition-list'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/observations',
    label: t('route.observations.label'),
    description: t('route.observations.description'),
    group: t('route.group.clinical'),
    minimumRole: 'caregiver',
    personas: ['caregiver', 'clinician', 'admin'],
    tiers: ['clinical'],
    actions: ['view-observations'],
    cards: ['observation-trends'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/immunizations',
    label: t('route.immunizations.label'),
    description: t('route.immunizations.description'),
    group: t('route.group.clinical'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-immunizations'],
    cards: ['immunization-schedule'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/documents',
    label: t('route.documents.label'),
    description: t('route.documents.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-documents', 'upload-document'],
    cards: ['document-list'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/documents/upload',
    label: t('route.documents.upload.label'),
    description: t('route.documents.upload.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['upload-document'],
    cards: [],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/documents/:docId/ocr',
    label: t('route.ocr.label'),
    description: t('route.ocr.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['review-ocr'],
    cards: [],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/notifications',
    label: t('route.notifications.label'),
    description: t('route.notifications.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: ['view-notifications'],
    cards: ['notification-feed'],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/forbidden',
    label: t('route.forbidden.label'),
    description: t('route.forbidden.description'),
    group: t('route.group.governance'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: [],
    cards: [],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
  },
  {
    path: '/not-found',
    label: t('route.notFound.label'),
    description: t('route.notFound.description'),
    group: t('route.group.governance'),
    minimumRole: 'patient',
    personas: ['patient', 'caregiver', 'clinician', 'admin'],
    tiers: ['core'],
    actions: [],
    cards: [],
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'stable',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'experimental',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'experimental',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'experimental',
    },
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
    lifecycle: {
      introducedAt: '1.0.0',
      stability: 'experimental',
    },
  },
] as const satisfies readonly PhrRouteContract[];

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];
