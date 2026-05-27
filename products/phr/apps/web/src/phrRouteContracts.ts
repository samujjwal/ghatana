import { createRouteAccessEvaluator, type ProductRouteCapability } from '@ghatana/product-shell';
import type { PhrRole } from './auth/PhrAccessContext';
import { t } from './i18n/phrI18n';

export interface PhrRouteContract extends ProductRouteCapability {
  readonly personas?: readonly string[];
  readonly tiers?: readonly string[];
  /**
   * When present and set to `true`, this route is behind a feature flag and
   * is not yet production-ready. The router renders a `FeatureFlagPage`
   * placeholder instead of the real page component.
   */
  readonly featureFlag?: boolean;
  /**
   * Route stability status - only stable, preview, blocked, or hidden allowed.
   * No deprecated or removed states per fix-forward policy.
   */
  readonly stability?: 'stable' | 'preview' | 'blocked' | 'hidden';
  /**
   * Backend API endpoint for this route.
   */
  readonly apiEndpoint?: string;
  /**
   * Policy ID for access control.
   */
  readonly policyId?: string;
  /**
   * Test ID for route verification.
   */
  readonly testId?: string;
}

export type PhrRole = 'patient' | 'caregiver' | 'clinician' | 'admin' | 'fchv';

export const PHR_ROLE_ORDER: Readonly<Record<PhrRole, number>> = {
  patient: 0,
  caregiver: 1,
  clinician: 2,
  fchv: 3,
  admin: 4,
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
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-patient-summary"],
    cards: ["patient-summary","care-plan","emergency-readiness"],
    stability: 'stable',
    apiEndpoint: '/api/v1/dashboard',
    policyId: 'phr.dashboard.view',
    testId: 'phr-dashboard-view-001'
  },
  {
    path: '/records',
    label: t('route.records.label'),
    description: t('route.records.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-records"],
    cards: ["record-highlights","interop-status"],
    stability: 'stable',
    apiEndpoint: '/api/v1/records',
    policyId: 'phr.records.view',
    testId: 'phr-records-view-001'
  },
  {
    path: '/consents',
    label: t('route.consents.label'),
    description: t('route.consents.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["manage-consent"],
    cards: ["active-consent-grants","expiring-consents"],
    stability: 'stable'
  },
  {
    path: '/appointments',
    label: t('route.appointments.label'),
    description: t('route.appointments.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["schedule-visit"],
    cards: ["upcoming-appointments"],
    stability: 'stable'
  },
  {
    path: '/settings',
    label: t('route.settings.label'),
    description: t('route.settings.description'),
    group: t('route.group.governance'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["manage-profile-settings"],
    cards: ["profile-controls","integration-status"],
    stability: 'stable'
  },
  {
    path: '/labs',
    label: t('route.labs.label'),
    description: t('route.labs.description'),
    group: t('route.group.clinical'),
    minimumRole: 'caregiver',
    personas: ["caregiver","clinician","admin"],
    tiers: ["clinical"],
    actions: ["review-lab-results"],
    cards: ["recent-lab-results"],
    stability: 'stable'
  },
  {
    path: '/medications',
    label: t('route.medications.label'),
    description: t('route.medications.description'),
    group: t('route.group.clinical'),
    minimumRole: 'caregiver',
    personas: ["caregiver","clinician","admin"],
    tiers: ["clinical"],
    actions: ["review-medications"],
    cards: ["medication-adherence"],
    stability: 'stable'
  },
  {
    path: '/conditions',
    label: t('route.conditions.label'),
    description: t('route.conditions.description'),
    group: t('route.group.clinical'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-conditions"],
    cards: ["condition-list"],
    stability: 'stable'
  },
  {
    path: '/observations',
    label: t('route.observations.label'),
    description: t('route.observations.description'),
    group: t('route.group.clinical'),
    minimumRole: 'caregiver',
    personas: ["caregiver","clinician","admin"],
    tiers: ["clinical"],
    actions: ["view-observations"],
    cards: ["observation-trends"],
    stability: 'stable'
  },
  {
    path: '/immunizations',
    label: t('route.immunizations.label'),
    description: t('route.immunizations.description'),
    group: t('route.group.clinical'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-immunizations"],
    cards: ["immunization-schedule"],
    stability: 'stable'
  },
  {
    path: '/documents',
    label: t('route.documents.label'),
    description: t('route.documents.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-documents","upload-document"],
    cards: ["document-list"],
    stability: 'stable'
  },
  {
    path: '/documents/upload',
    label: t('route.documents/upload.label'),
    description: t('route.documents/upload.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["upload-document"],
    cards: [],
    stability: 'stable'
  },
  {
    path: '/documents/:docId/ocr',
    label: t('route.documents/docId/ocr.label'),
    description: t('route.documents/docId/ocr.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["review-ocr"],
    cards: [],
    stability: 'stable'
  },
  {
    path: '/timeline',
    label: t('route.timeline.label'),
    description: t('route.timeline.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-timeline"],
    cards: ["health-timeline"],
    stability: 'stable'
  },
  {
    path: '/profile',
    label: t('route.profile.label'),
    description: t('route.profile.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-profile","edit-profile"],
    cards: ["profile-summary"],
    stability: 'stable'
  },
  {
    path: '/records/:recordId',
    label: t('route.records/recordId.label'),
    description: t('route.records/recordId.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-records"],
    cards: [],
    stability: 'stable'
  },
  {
    path: '/notifications',
    label: t('route.notifications.label'),
    description: t('route.notifications.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: ["view-notifications"],
    cards: ["notification-feed"],
    stability: 'stable'
  },
  {
    path: '/forbidden',
    label: t('route.forbidden.label'),
    description: t('route.forbidden.description'),
    group: t('route.group.governance'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: [],
    cards: [],
    stability: 'stable'
  },
  {
    path: '/not-found',
    label: t('route.notFound.label'),
    description: t('route.notFound.description'),
    group: t('route.group.governance'),
    minimumRole: 'patient',
    personas: ["patient","caregiver","clinician","admin"],
    tiers: ["core"],
    actions: [],
    cards: [],
    stability: 'stable'
  },
  {
    path: '/emergency',
    label: t('route.emergency.label'),
    description: t('route.emergency.description'),
    group: t('route.group.governance'),
    minimumRole: 'clinician',
    personas: ["clinician","admin"],
    tiers: ["emergency"],
    actions: ["break-glass-review"],
    cards: ["override-audit-timeline"],
    stability: 'stable',
    apiEndpoint: '/api/v1/emergency/access',
    policyId: 'phr.emergency.break-glass',
    testId: 'phr-emergency-break-glass-001'
  },
  {
    path: '/emergency/reviews',
    label: t('route.emergency/reviews.label'),
    description: t('route.emergency/reviews.description'),
    group: t('route.group.governance'),
    minimumRole: 'admin',
    personas: ["admin"],
    tiers: ["emergency"],
    actions: ["review-emergency-access"],
    cards: ["pending-reviews","overdue-reviews"],
    stability: 'stable',
    apiEndpoint: '/api/v1/emergency/reviews',
    policyId: 'phr.emergency.review',
    testId: 'phr-emergency-review-001'
  },
  {
    path: '/release-readiness',
    label: t('route.releaseReadiness.label'),
    description: t('route.releaseReadiness.description'),
    group: t('route.group.governance'),
    minimumRole: 'admin',
    personas: ["admin"],
    tiers: ["clinical"],
    actions: ["view-release-readiness"],
    cards: ["evidence-freshness","fhir-runtime","consent-cache-proof","rollback-proof"],
    stability: 'stable'
  },
  {
    path: '/audit',
    label: t('route.audit.label'),
    description: t('route.audit.description'),
    group: t('route.group.governance'),
    minimumRole: 'admin',
    personas: ["admin"],
    tiers: ["clinical"],
    actions: ["view-audit-trail"],
    cards: ["audit-trail"],
    stability: 'stable'
  },
  {
    path: '/provider/dashboard',
    label: t('route.provider/dashboard.label'),
    description: t('route.provider/dashboard.description'),
    group: t('route.group.provider'),
    minimumRole: 'clinician',
    personas: ["clinician","admin"],
    tiers: ["clinical"],
    actions: ["view-provider-dashboard"],
    cards: ["provider-panel"],
    stability: 'hidden'
  },
  {
    path: '/provider/patients',
    label: t('route.provider/patients.label'),
    description: t('route.provider/patients.description'),
    group: t('route.group.provider'),
    minimumRole: 'clinician',
    personas: ["clinician","admin"],
    tiers: ["clinical"],
    actions: ["view-patient-list"],
    cards: ["patient-roster"],
    stability: 'hidden'
  },
  {
    path: '/caregiver/dependents',
    label: t('route.caregiver/dependents.label'),
    description: t('route.caregiver/dependents.description'),
    group: t('route.group.caregiver'),
    minimumRole: 'caregiver',
    personas: ["caregiver","admin"],
    tiers: ["core"],
    actions: ["view-dependents"],
    cards: ["dependent-summaries"],
    stability: 'hidden'
  },
  {
    path: '/fchv/dashboard',
    label: t('route.fchv/dashboard.label'),
    description: t('route.fchv/dashboard.description'),
    group: t('route.group.fchv'),
    minimumRole: 'fchv',
    personas: ["fchv","admin"],
    tiers: ["core"],
    actions: ["view-fchv-dashboard"],
    cards: ["community-health-summary"],
    stability: 'hidden'
  },
  {
    path: '/mobile/dashboard',
    label: t('route.mobile/dashboard.label'),
    description: t('route.mobile/dashboard.description'),
    group: t('route.group.care'),
    minimumRole: 'patient',
    personas: ["patient"],
    tiers: ["core"],
    actions: ["view-mobile-dashboard"],
    cards: ["mobile-patient-summary"],
    stability: 'stable'
  }
] as const satisfies readonly PhrRouteContract[];

export type PhrRoutePath = (typeof phrRouteContracts)[number]['path'];
