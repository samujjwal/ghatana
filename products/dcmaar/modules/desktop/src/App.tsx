import React, { Suspense } from 'react';
import { createBrowserRouter, RouterProvider, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Box, CircularProgress, Typography, useTheme } from '@mui/material';
import { SnackbarProvider } from 'notistack';
import { Provider as JotaiProvider } from 'jotai';

// Theme and Layout
import MainLayout from './components/layout/MainLayout';
import { useConnectionMonitor } from './services/connection';
// import { useExtensionBridge } from './hooks/useExtensionBridge'; // Temporarily disabled
import { useConnectorManager } from './hooks/useConnectorManager';
import { ThemeModeProvider } from './providers/ThemeModeProvider';

// Pages
const DashboardPage = React.lazy(() => import('./pages/DashboardPage'));
const MetricsPage = React.lazy(() => import('./pages/MetricsPage'));
const EventsPage = React.lazy(() => import('./pages/EventsPage'));
const CommandsPage = React.lazy(() => import('./pages/CommandsPage'));
const CopilotPage = React.lazy(() => import('./pages/CopilotPage'));
const SettingsPage = React.lazy(() => import('./pages/SettingsPage'));
const DiagnosticsPage = React.lazy(() => import('./pages/DiagnosticsPage'));
const PoliciesPage = React.lazy(() => import('./pages/PoliciesPage'));
const ReportsPage = React.lazy(() => import('./pages/ReportsPage'));

// Agent Pages
const ControlHubPage = React.lazy(() => import('./routes/control'));
const AuditViewerPage = React.lazy(() => import('./routes/audit'));

// Create a client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
});

// Separate component to ensure QueryClient is available
function MainLayoutWithConnection() {
  useConnectionMonitor();
  // useExtensionBridge(); // Temporarily disabled - requires WebSocket server on ws://localhost:3001
  useConnectorManager(); // Initialize desktop connector system
  return <MainLayout />;
}

function AppLoader() {
  const theme = useTheme();

  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 2,
        height: '100vh',
        bgcolor: theme.palette.background.default,
        color: theme.palette.text.secondary,
      }}
    >
      <CircularProgress color="primary" />
      <Typography variant="body2">Loading application…</Typography>
    </Box>
  );
}

function App() {
  const router = createBrowserRouter([
    {
      path: '/',
      element: <MainLayoutWithConnection />,
      children: [
        { index: true, element: <Navigate to="/dashboard" replace /> },
        { path: 'dashboard', element: <DashboardPage /> },
        { path: 'metrics', element: <MetricsPage /> },
        { path: 'events', element: <EventsPage /> },
        { path: 'commands', element: <CommandsPage /> },
        { path: 'copilot', element: <CopilotPage /> },
        { path: 'policies', element: <PoliciesPage /> },
        { path: 'settings', element: <SettingsPage /> },
        { path: 'diagnostics', element: <DiagnosticsPage /> },
        { path: 'reports', element: <ReportsPage /> },
        { path: 'control', element: <ControlHubPage /> },
        { path: 'audit', element: <AuditViewerPage /> },
        { path: '*', element: <Navigate to="/dashboard" replace /> },
      ],
    },
  ]);

  return (
    <JotaiProvider>
      {/* DevTools disabled to avoid tree-shaking warnings - enable in vite.config.ts if needed */}
      <QueryClientProvider client={queryClient}>
        <ThemeModeProvider>
          <SnackbarProvider maxSnack={3}>
            <Suspense fallback={<AppLoader />}>
              <RouterProvider router={router} />
            </Suspense>
          </SnackbarProvider>
        </ThemeModeProvider>
      </QueryClientProvider>
    </JotaiProvider>
  );
}

export default App;
