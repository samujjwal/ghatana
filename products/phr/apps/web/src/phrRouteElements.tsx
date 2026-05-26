import React from 'react';
import type { PhrRouteContract, PhrRoutePath } from './phrRouteContracts';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { AuditPage } from './pages/AuditPage';
import { ConsentPage } from './pages/ConsentPage';
import { DashboardPage } from './pages/DashboardPage';
import { EmergencyAccessPage } from './pages/EmergencyAccessPage';
import { FeatureFlagPage } from './pages/FeatureFlagPage';
import { LabsPage } from './pages/LabsPage';
import { MedicationsPage } from './pages/MedicationsPage';
import { RecordDetailPage } from './pages/RecordDetailPage';
import { RecordsPage } from './pages/RecordsPage';
import { ReleaseCockpitPage } from './pages/ReleaseCockpitPage';
import { SettingsPage } from './pages/SettingsPage';

export interface PhrRouteManifestEntry extends PhrRouteContract {
  readonly element: React.ReactElement;
}

const routeElements: Record<PhrRoutePath, React.ReactElement> = {
  '/dashboard': <DashboardPage />,
  '/records': <RecordsPage />,
  '/consents': <ConsentPage />,
  '/appointments': <AppointmentsPage />,
  '/labs': <LabsPage />,
  '/medications': <MedicationsPage />,
  '/emergency': <EmergencyAccessPage />,
  '/release-readiness': <ReleaseCockpitPage />,
  '/audit': <AuditPage />,
  '/settings': <SettingsPage />,
  '/records/:recordId': <RecordDetailPage />,
  // Feature-flagged placeholder routes
  '/provider/dashboard': <FeatureFlagPage routePath="/provider/dashboard" />,
  '/provider/patients': <FeatureFlagPage routePath="/provider/patients" />,
  '/caregiver/dependents': <FeatureFlagPage routePath="/caregiver/dependents" />,
  '/fchv/dashboard': <FeatureFlagPage routePath="/fchv/dashboard" />,
};

export function attachPhrRouteElement(route: PhrRouteContract): PhrRouteManifestEntry {
  const element = routeElements[route.path as PhrRoutePath];
  if (!element) {
    throw new Error(`PHR route element is missing for path ${route.path}`);
  }

  return {
    ...route,
    element,
  };
}
