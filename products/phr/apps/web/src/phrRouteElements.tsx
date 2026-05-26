import React from 'react';
import type { PhrRouteContract, PhrRoutePath } from './phrRouteContracts';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { AuditPage } from './pages/AuditPage';
import { CaregiverDependentsPage } from './pages/CaregiverDependentsPage';
import { ConditionsPage } from './pages/ConditionsPage';
import { ConsentPage } from './pages/ConsentPage';
import { DashboardPage } from './pages/DashboardPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { DocumentUploadPage } from './pages/DocumentUploadPage';
import { EmergencyAccessPage } from './pages/EmergencyAccessPage';
import { FchvDashboardPage } from './pages/FchvDashboardPage';
import { FeatureFlagPage } from './pages/FeatureFlagPage';
import { ForbiddenPage } from './pages/ForbiddenPage';
import { ImmunizationsPage } from './pages/ImmunizationsPage';
import { LabsPage } from './pages/LabsPage';
import { MedicationsPage } from './pages/MedicationsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ObservationsPage } from './pages/ObservationsPage';
import { OcrReviewPage } from './pages/OcrReviewPage';
import { ProfilePage } from './pages/ProfilePage';
import { ProviderDashboardPage } from './pages/ProviderDashboardPage';
import { ProviderPatientsPage } from './pages/ProviderPatientsPage';
import { RecordDetailPage } from './pages/RecordDetailPage';
import { RecordsPage } from './pages/RecordsPage';
import { ReleaseCockpitPage } from './pages/ReleaseCockpitPage';
import { SettingsPage } from './pages/SettingsPage';
import { TimelinePage } from './pages/TimelinePage';

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
  '/profile': <ProfilePage />,
  '/timeline': <TimelinePage />,
  '/conditions': <ConditionsPage />,
  '/observations': <ObservationsPage />,
  '/immunizations': <ImmunizationsPage />,
  '/documents': <DocumentsPage />,
  '/documents/upload': <DocumentUploadPage />,
  '/documents/:docId/ocr': <OcrReviewPage />,
  '/notifications': <NotificationsPage />,
  '/forbidden': <ForbiddenPage />,
  '/not-found': <NotFoundPage />,
  '/provider/dashboard': <ProviderDashboardPage />,
  '/provider/patients': <ProviderPatientsPage />,
  '/caregiver/dependents': <CaregiverDependentsPage />,
  '/fchv/dashboard': <FchvDashboardPage />,
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
