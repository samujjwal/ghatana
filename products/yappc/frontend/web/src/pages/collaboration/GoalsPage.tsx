/**
 * Goals Page
 *
 * @description Team and project goals with OKR-style tracking,
 * progress indicators, and alignment views.
 *
 * @doc.type page
 * @doc.purpose Goal tracking and OKRs
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { Target, Plus, TrendingUp } from 'lucide-react';

const GoalsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-emerald-500/10">
              <Target className="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Goals</h1>
              <p className="text-zinc-400">Track objectives and key results</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> New Goal
          </button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <Target className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No goals set</h3>
          <p className="text-zinc-500 max-w-md">
            Define team objectives and measurable key results to align everyone
            on what matters most.
          </p>
        </div>
      </div>
    </div>
  );
};

export default GoalsPage;
