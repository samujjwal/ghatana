import React from 'react';
import type { PhrRouteContract, PhrRoutePath } from './phrRouteContracts';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { ConsentPage } from './pages/ConsentPage';
import { DashboardPage } from './pages/DashboardPage';
import { EmergencyAccessPage } from './pages/EmergencyAccessPage';
import { LabsPage } from './pages/LabsPage';
import { MedicationsPage } from './pages/MedicationsPage';
import { RecordDetailPage } from './pages/RecordDetailPage';
import { RecordsPage } from './pages/RecordsPage';
import { SettingsPage } from './pages/SettingsPage';

export interface PhrRouteManifestEntry extends PhrRouteContract {
  readonly element: React.ReactElement;
}

const routeElements = {
  '/dashboard': <DashboardPage />,
  '/records': <RecordsPage />,
  '/consents': <ConsentPage />,
  '/appointments': <AppointmentsPage />,
  '/labs': <LabsPage />,
  '/medications': <MedicationsPage />,
  '/emergency': <EmergencyAccessPage />,
  '/settings': <SettingsPage />,
  '/records/:recordId': <RecordDetailPage />,
} satisfies Record<PhrRoutePath, React.ReactElement>;

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
