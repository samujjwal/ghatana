/**
 * Dashboards List Page
 *
 * @description Custom operations dashboards gallery with create, clone,
 * and share functionality.
 *
 * @doc.type page
 * @doc.purpose Operations dashboard management
 * @doc.layer product
 */

import React from 'react';
import { useParams, NavLink } from 'react-router';
import { LayoutDashboard, Plus } from 'lucide-react';
import { ROUTES } from '../../router/paths';

const DashboardsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-blue-500/10">
              <LayoutDashboard className="w-6 h-6 text-blue-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Dashboards</h1>
              <p className="text-zinc-400">Custom monitoring dashboards</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> New Dashboard
          </button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <LayoutDashboard className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No dashboards yet</h3>
          <p className="text-zinc-500 max-w-md">
            Create custom dashboards to visualize metrics, logs, and service health.
          </p>
        </div>
      </div>
    </div>
  );
};

export default DashboardsPage;
