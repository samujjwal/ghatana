import React from 'react';
import type { ProductRouteCapability } from '@ghatana/product-shell';
import type { PhrRole } from './auth/PhrAccessContext';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { ConsentPage } from './pages/ConsentPage';
import { DashboardPage } from './pages/DashboardPage';
import { EmergencyAccessPage } from './pages/EmergencyAccessPage';
import { LabsPage } from './pages/LabsPage';
import { MedicationsPage } from './pages/MedicationsPage';
import { RecordsPage } from './pages/RecordsPage';
import { SettingsPage } from './pages/SettingsPage';

export interface PhrRouteManifestEntry extends ProductRouteCapability {
  readonly element: React.ReactElement;
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

export function isRouteAllowedForRole(route: Pick<PhrRouteManifestEntry, 'minimumRole'>, role: PhrRole): boolean {
  if (!route.minimumRole) {
    return true;
  }
  return PHR_ROLE_ORDER[role] >= PHR_ROLE_ORDER[route.minimumRole as PhrRole];
}

export const phrRouteManifest: readonly PhrRouteManifestEntry[] = [
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
    element: <DashboardPage />,
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
    element: <RecordsPage />,
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
    element: <ConsentPage />,
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
    element: <AppointmentsPage />,
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
    element: <LabsPage />,
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
    element: <MedicationsPage />,
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
    element: <EmergencyAccessPage />,
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
    element: <SettingsPage />,
  },
];
