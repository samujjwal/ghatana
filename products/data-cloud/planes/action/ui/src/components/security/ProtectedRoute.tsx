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
  const { isAuthenticated, isVerifyingAuth } = useAuth();
  const location = useLocation();

  if (isVerifyingAuth) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-white text-sm text-gray-500 dark:bg-gray-950 dark:text-gray-300">
        Verifying access…
      </div>
    );
  }

  if (!isAuthenticated) {
    const redirectTarget = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to="/login" replace state={{ from: redirectTarget }} />;
  }

  if (children) {
    return <>{children}</>;
  }

  return <Outlet />;
}
