/**
 * Metrics Page
 *
 * @description Metrics explorer with PromQL-style queries, chart builder,
 * and saved views.
 *
 * @doc.type page
 * @doc.purpose Metrics exploration and visualization
 * @doc.layer product
 */

import React from 'react';
import { useParams } from 'react-router';
import { BarChart3, Plus, Search } from 'lucide-react';

const MetricsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-violet-500/10">
              <BarChart3 className="w-6 h-6 text-violet-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Metrics</h1>
              <p className="text-zinc-400">Explore, query, and visualize system metrics</p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" /> New Chart
          </button>
        </div>

        <div className="flex flex-col items-center justify-center py-20 text-center">
          <BarChart3 className="w-12 h-12 text-zinc-600 mb-4" />
          <h3 className="text-lg font-semibold text-zinc-300 mb-2">No metrics data</h3>
          <p className="text-zinc-500 max-w-md">
            Connect your metrics backend to start exploring system performance,
            latency percentiles, and throughput data.
          </p>
        </div>
      </div>
    </div>
  );
};

export default MetricsPage;
