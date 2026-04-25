/**
 * SessionExpiryPage — recovery screen when authentication expires or is invalid.
 *
 * @doc.type page
 * @doc.purpose Guide users through re-authentication after session expiry
 * @doc.layer frontend
 */
import React from 'react';
import { useNavigate } from 'react-router';
import { Button } from '@ghatana/design-system';
import { Clock, ShieldAlert, LogIn } from 'lucide-react';

export function SessionExpiryPage(): React.ReactElement {
  const navigate = useNavigate();

  const handleReauth = () => {
    // Clear any stale session state
    sessionStorage.removeItem('aep-token');
    sessionStorage.removeItem('aep-session');
    sessionStorage.removeItem('aep:active-tenant');
    navigate('/login');
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-950 px-4">
      <div className="w-full max-w-md rounded-xl border border-gray-200 bg-white p-8 shadow-sm dark:border-gray-800 dark:bg-gray-900">
        <div className="mb-4 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-amber-100 dark:bg-amber-900">
            <Clock className="h-5 w-5 text-amber-700 dark:text-amber-300" />
          </div>
          <div>
            <h1 className="text-base font-semibold text-gray-900 dark:text-white">
              Session Expired
            </h1>
            <p className="text-xs text-gray-500 dark:text-gray-400">
              Your authentication session has ended or is invalid.
            </p>
          </div>
        </div>

        <div className="space-y-3 text-sm text-gray-600 dark:text-gray-300">
          <div className="flex items-start gap-2 rounded-lg bg-gray-50 p-3 dark:bg-gray-800/50">
            <ShieldAlert className="mt-0.5 h-4 w-4 flex-shrink-0 text-gray-500" />
            <div className="space-y-1">
              <p className="font-medium text-gray-700 dark:text-gray-200">
                Why this happened
              </p>
              <ul className="list-disc pl-4 text-xs text-gray-500 dark:text-gray-400">
                <li>Session token expired or was revoked</li>
                <li>Tenant context was invalidated</li>
                <li>Platform identity provider requires re-authentication</li>
                <li>Security policy enforced sign-out</li>
              </ul>
            </div>
          </div>
        </div>

        <div className="mt-6 flex flex-col gap-2">
          <Button
            onClick={handleReauth}
            variant="primary"
            className="w-full justify-center gap-2"
          >
            <LogIn className="h-4 w-4" />
            Sign in again
          </Button>
          <p className="text-center text-[11px] text-gray-400 dark:text-gray-500">
            You will be redirected to your platform identity provider for secure SSO.
          </p>
        </div>
      </div>
    </div>
  );
}
