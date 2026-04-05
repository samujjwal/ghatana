/**
 * Vulnerabilities List Page
 *
 * @description Vulnerability inventory with severity filtering,
 * CVSS scores, and remediation tracking.
 *
 * @doc.type page
 * @doc.purpose Vulnerability management
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams, NavLink } from 'react-router';
import { ShieldAlert, Search, Filter } from 'lucide-react';
import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';

const VulnerabilitiesPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [severity, setSeverity] = useState<'all' | 'critical' | 'high' | 'medium' | 'low'>('all');

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-red-500/10">
            <ShieldAlert className="w-6 h-6 text-red-400" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Vulnerabilities</h1>
            <p className="text-zinc-400">Security vulnerabilities across your codebase</p>
          </div>
        </div>

        <div className="flex items-center gap-3 mb-6">
          {(['all', 'critical', 'high', 'medium', 'low'] as const).map((s) => (
            <button
              key={s}
              onClick={() => setSeverity(s)}
              className={cn(
                'px-3 py-1.5 rounded-lg text-sm capitalize transition-colors',
                severity === s ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'
              )}
            >
              {s}
            </button>
          ))}
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <ShieldAlert className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No vulnerabilities found</h3>
          <p className="text-zinc-500 max-w-md">
            Run a security scan to discover vulnerabilities in your dependencies and code.
          </p>
        </div>
      </div>
    </div>
  );
};

export default VulnerabilitiesPage;
