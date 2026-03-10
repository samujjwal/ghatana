/**
 * Logs Page
 *
 * @description Centralized log viewer with full-text search, structured
 * filtering, and live-tail mode.
 *
 * @doc.type page
 * @doc.purpose Log exploration and search
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams } from 'react-router';
import { FileText, Search, Filter, Play, Pause } from 'lucide-react';

const LogsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [query, setQuery] = useState('');
  const [liveTail, setLiveTail] = useState(false);

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-emerald-500/10">
              <FileText className="w-6 h-6 text-emerald-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Logs</h1>
              <p className="text-zinc-400">Search, filter, and tail application logs</p>
            </div>
          </div>
          <button
            onClick={() => setLiveTail(!liveTail)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              liveTail ? 'bg-emerald-600 hover:bg-emerald-500' : 'bg-zinc-800 hover:bg-zinc-700 text-zinc-300'
            }`}
          >
            {liveTail ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
            {liveTail ? 'Pause' : 'Live Tail'}
          </button>
        </div>

        <div className="flex items-center gap-3 mb-6">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400" />
            <input
              type="text"
              placeholder='Search logs... (e.g., level:error service:api-gateway)'
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-sm text-white placeholder-zinc-500 focus:border-violet-500 focus:outline-none font-mono"
            />
          </div>
          <button className="flex items-center gap-2 px-3 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-sm text-zinc-400 hover:text-white">
            <Filter className="w-4 h-4" /> Filters
          </button>
        </div>

        <div className="p-6 rounded-xl bg-zinc-900 border border-zinc-800 font-mono text-sm text-zinc-500 text-center">
          No log entries. Connect a log source to start exploring.
        </div>
      </div>
    </div>
  );
};

export default LogsPage;
