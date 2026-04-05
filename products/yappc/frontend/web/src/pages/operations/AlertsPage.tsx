/**
 * Alerts Page
 *
 * @description Alert management with grouping, silencing, and escalation rules.
 *
 * @doc.type page
 * @doc.purpose Alert management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { Bell, Plus, Filter } from 'lucide-react';

const AlertsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-amber-500/10">
              <Bell className="w-6 h-6 text-amber-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Alerts</h1>
              <p className="text-zinc-400">Active alerts, silenced rules, and escalation policies</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> New Alert Rule
          </button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <Bell className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No active alerts</h3>
          <p className="text-zinc-500 max-w-md">
            Configure alert rules to monitor your services and receive notifications
            when thresholds are breached.
          </p>
        </div>
      </div>
    </div>
  );
};

export default AlertsPage;
