/**
 * Route guard for the AEP console.
 *
 * @doc.type component
 * @doc.purpose Redirect unauthenticated users to the login page
 * @doc.layer frontend
 */
import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '@/context/AuthContext';

interface ProtectedRouteProps {
  children?: React.ReactNode;
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    const redirectTarget = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" replace state={{ from: redirectTarget }} />;
  }

  if (children) {
    return <>{children}</>;
  }

  return <Outlet />;
}