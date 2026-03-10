/**
 * Feature Flags Page
 *
 * @description Feature flag management with targeting rules, rollout
 * percentages, and kill-switch controls.
 *
 * @doc.type page
 * @doc.purpose Feature flag management
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { Flag, Plus, ToggleLeft } from 'lucide-react';

const FeatureFlagsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-amber-500/10">
              <Flag className="w-6 h-6 text-amber-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Feature Flags</h1>
              <p className="text-zinc-400">Control feature rollouts and experiments</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> New Flag
          </button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <ToggleLeft className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No feature flags</h3>
          <p className="text-zinc-500 max-w-md">
            Create feature flags to safely roll out new features with targeting
            rules and kill-switch controls.
          </p>
        </div>
      </div>
    </div>
  );
};

export default FeatureFlagsPage;
