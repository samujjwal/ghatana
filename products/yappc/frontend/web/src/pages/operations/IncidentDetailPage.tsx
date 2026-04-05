/**
 * Incident Detail Page
 *
 * @description Individual incident view with timeline, responders,
 * status updates, and war room link.
 *
 * @doc.type page
 * @doc.purpose Incident detail and response coordination
 * @doc.layer product
 */

import React from 'react';
import { useParams, NavLink } from 'react-router';
import { AlertTriangle, ArrowLeft, Users, Clock, ExternalLink } from 'lucide-react';
import { ROUTES } from '../../router/paths';

const IncidentDetailPage: React.FC = () => {
  const { projectId, incidentId } = useParams<{ projectId: string; incidentId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-4xl mx-auto">
        <NavLink
          to={projectId ? ROUTES.operations.incidents(projectId) : '/'}
          className="inline-flex items-center gap-2 text-sm text-zinc-400 hover:text-white mb-6"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Incidents
        </NavLink>

        <div className="flex items-center gap-4 mb-8">
          <div className="p-3 rounded-xl bg-red-500/10">
            <AlertTriangle className="w-6 h-6 text-red-400" />
          </div>
          <div className="flex-1">
            <h1 className="text-2xl font-bold">Incident {incidentId}</h1>
            <p className="text-zinc-400">Incident details and response timeline</p>
          </div>
          {projectId && incidentId && (
            <NavLink
              to={ROUTES.operations.warroom(projectId, incidentId)}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-red-600 hover:bg-red-500 transition-colors text-sm font-medium"
            >
              <ExternalLink className="w-4 h-4" /> Open War Room
            </NavLink>
          )}
        </div>

        <div className="grid grid-cols-3 gap-6">
          <div className="col-span-2 space-y-6">
            <div className="p-6 rounded-xl bg-zinc-900 border border-zinc-800">
              <h2 className="text-lg font-semibold mb-4">Timeline</h2>
              <p className="text-zinc-500 text-sm">No timeline events yet.</p>
            </div>
          </div>
          <div className="space-y-4">
            <div className="p-4 rounded-xl bg-zinc-900 border border-zinc-800 text-sm space-y-3">
              <div className="flex justify-between"><span className="text-zinc-400">Severity</span><span className="text-red-400">—</span></div>
              <div className="flex justify-between"><span className="text-zinc-400">Status</span><span>—</span></div>
              <div className="flex justify-between"><span className="text-zinc-400">Commander</span><span>Unassigned</span></div>
              <div className="flex justify-between"><span className="text-zinc-400">Duration</span><span>—</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default IncidentDetailPage;
