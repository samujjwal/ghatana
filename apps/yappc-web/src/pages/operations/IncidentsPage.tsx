/**
 * Incidents List Page
 *
 * @description Incident management with severity filtering, timeline view,
 * and quick-action to open war rooms.
 *
 * @doc.type page
 * @doc.purpose Incident list and triage
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams, NavLink } from 'react-router';
import { AlertTriangle, Plus, Search, Filter, Clock, ArrowRight } from 'lucide-react';
import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';

const IncidentsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [filter, setFilter] = useState<'all' | 'active' | 'resolved'>('active');

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-red-500/10">
              <AlertTriangle className="w-6 h-6 text-red-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Incidents</h1>
              <p className="text-zinc-400">Active and resolved incidents</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-red-600 hover:bg-red-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> Declare Incident
          </button>
        </div>

        <div className="flex items-center gap-3 mb-6">
          {(['all', 'active', 'resolved'] as const).map((f) => (
            <button
              key={f}
              onClick={() => setFilter(f)}
              className={cn(
                'px-3 py-1.5 rounded-lg text-sm capitalize transition-colors',
                filter === f ? 'bg-zinc-800 text-white' : 'text-zinc-400 hover:text-white'
              )}
            >
              {f}
            </button>
          ))}
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <AlertTriangle className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No incidents</h3>
          <p className="text-zinc-500 max-w-md">
            When incidents are declared, they will appear here with severity, impact, and
            real-time status updates.
          </p>
        </div>
      </div>
    </div>
  );
};

export default IncidentsPage;
