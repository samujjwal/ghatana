/**
 * Security Alerts Page
 *
 * @description Security-specific alerts for dependency vulnerabilities,
 * secret leaks, and policy violations.
 *
 * @doc.type page
 * @doc.purpose Security alert management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { ShieldAlert, Bell } from 'lucide-react';

const SecurityAlertsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-red-500/10">
            <ShieldAlert className="w-6 h-6 text-red-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Security Alerts</h1>
            <p className="text-zinc-400">Active security notifications and advisories</p>
          </div>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <Bell className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No security alerts</h3>
          <p className="text-zinc-500 max-w-md">
            Security alerts will appear here when vulnerabilities, secret leaks,
            or policy violations are detected.
          </p>
        </div>
      </div>
    </div>
  );
};

export default SecurityAlertsPage;
