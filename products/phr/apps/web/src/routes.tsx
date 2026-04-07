import React from 'react';
import { Navigate, createBrowserRouter } from 'react-router';
import { AppShell } from './layout/AppShell';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { ConsentPage } from './pages/ConsentPage';
import { DashboardPage } from './pages/DashboardPage';
import { EmergencyAccessPage } from './pages/EmergencyAccessPage';
import { LabsPage } from './pages/LabsPage';
import { LoginPage } from './pages/LoginPage';
import { MedicationsPage } from './pages/MedicationsPage';
import { RecordDetailPage } from './pages/RecordDetailPage';
import { RecordsPage } from './pages/RecordsPage';
import { SettingsPage } from './pages/SettingsPage';

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/login" replace /> },
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <AppShell />,
    children: [
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'records', element: <RecordsPage /> },
      { path: 'records/:recordId', element: <RecordDetailPage /> },
      { path: 'consents', element: <ConsentPage /> },
      { path: 'appointments', element: <AppointmentsPage /> },
      { path: 'labs', element: <LabsPage /> },
      { path: 'medications', element: <MedicationsPage /> },
      { path: 'emergency', element: <EmergencyAccessPage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
]);