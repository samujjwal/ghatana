/**
 * Standups Page
 *
 * @description Async standup board with yesterday/today/blockers format,
 * team timeline, and streak tracking.
 *
 * @doc.type page
 * @doc.purpose Async standup management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { MessageSquare, Plus, Users, Clock } from 'lucide-react';

const StandupsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-sky-500/10">
              <MessageSquare className="w-6 h-6 text-sky-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Standups</h1>
              <p className="text-zinc-400">Async daily standups for your team</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> Post Update
          </button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <MessageSquare className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No standups today</h3>
          <p className="text-zinc-500 max-w-md">
            Post your daily standup to share what you worked on, what's next,
            and any blockers with the team.
          </p>
        </div>
      </div>
    </div>
  );
};

export default StandupsPage;
