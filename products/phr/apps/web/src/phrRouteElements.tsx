import React from 'react';
import type { PhrRouteContract, PhrRoutePath } from './phrRouteContracts';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { AuditPage } from './pages/AuditPage';
// CaregiverDependentsPage not imported - route is hidden in contract
import { ConditionsPage } from './pages/ConditionsPage';
import { ConsentPage } from './pages/ConsentPage';
import { DashboardPage } from './pages/DashboardPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { DocumentUploadPage } from './pages/DocumentUploadPage';
import { EmergencyAccessPage } from './pages/EmergencyAccessPage';
import { EmergencyReviewsPage } from './pages/EmergencyReviewsPage';
// FchvDashboardPage not imported - route is hidden in contract
import { ForbiddenPage } from './pages/ForbiddenPage';
import { ImmunizationsPage } from './pages/ImmunizationsPage';
import { LabsPage } from './pages/LabsPage';
import { MedicationDetailPage } from './pages/MedicationDetailPage';
import { MedicationsPage } from './pages/MedicationsPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { ObservationsPage } from './pages/ObservationsPage';
import { OcrReviewPage } from './pages/OcrReviewPage';
import { ProfilePage } from './pages/ProfilePage';
// ProviderDashboardPage, ProviderPatientsPage not imported - routes are hidden in contract
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
  '/medications/:medicationId': <MedicationDetailPage />,
  '/emergency': <EmergencyAccessPage />,
  '/emergency/reviews': <EmergencyReviewsPage />,
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
  // Role-specific routes mapped to NotFoundPage since they are hidden in contract
  '/provider/dashboard': <NotFoundPage />,
  '/provider/patients': <NotFoundPage />,
  '/caregiver/dependents': <NotFoundPage />,
  '/fchv/dashboard': <NotFoundPage />,
};

export function attachPhrRouteElement(route: PhrRouteContract): PhrRouteManifestEntry {
  const element = routeElements[route.path as PhrRoutePath];
  if (!element) {
    throw new Error(`PHR route element is missing for path ${route.path}`);
  }

  const finalElement = route.stability === 'blocked'
    ? <ForbiddenPage />
    : route.stability === 'hidden'
    ? <NotFoundPage />
    : element;

  return {
    ...route,
    element: finalElement,
  };
}
