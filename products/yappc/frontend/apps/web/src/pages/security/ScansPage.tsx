/**
 * Scans List Page
 *
 * @description Security scan history with status, findings summary,
 * and re-scan triggers.
 *
 * @doc.type page
 * @doc.purpose Security scan management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { Scan, Plus, Clock } from 'lucide-react';

const ScansPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-cyan-500/10">
              <Scan className="w-6 h-6 text-cyan-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Security Scans</h1>
              <p className="text-zinc-400">Scan history and findings</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> New Scan
          </button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <Scan className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No scans yet</h3>
          <p className="text-zinc-500 max-w-md">
            Run security scans against your codebase to identify vulnerabilities,
            license issues, and secret leaks.
          </p>
        </div>
      </div>
    </div>
  );
};

export default ScansPage;
