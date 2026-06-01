import React from 'react';
import type { PhrRouteContract, PhrRoutePath } from './phrRouteContracts';
import { phrRoutePlugin } from './phrRoutePlugin';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { AuditPage } from './pages/AuditPage';
import { ConditionsPage } from './pages/ConditionsPage';
import { ConsentPage } from './pages/ConsentPage';
import { DashboardPage } from './pages/DashboardPage';
import { DocumentsPage } from './pages/DocumentsPage';
import { DocumentUploadPage } from './pages/DocumentUploadPage';
import { EmergencyAccessPage } from './pages/EmergencyAccessPage';
import { EmergencyReviewsPage } from './pages/EmergencyReviewsPage';
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
};

export function isPhrRouteBrowserMountable(route: PhrRouteContract): boolean {
  return phrRoutePlugin.isBrowserMountable(route);
}

export function attachPhrRouteElement(route: PhrRouteContract): PhrRouteManifestEntry {
  const finalElement = !isPhrRouteBrowserMountable(route)
    ? <NotFoundPage />
    : route.stability === 'blocked' || route.stability === 'preview'
    ? <ForbiddenPage />
    : route.stability === 'hidden' || route.stability === 'deferred' || route.stability === 'removed'
    ? <NotFoundPage />
    : routeElements[route.path as PhrRoutePath];

  if (!finalElement) {
    throw new Error(`PHR route element is missing for path ${route.path}`);
  }

  return {
    ...route,
    element: finalElement,
  };
}
