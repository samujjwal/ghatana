/**
 * Backlog Page
 *
 * @description Product backlog with story prioritization, estimation,
 * and filtering by epic/label/assignee.
 *
 * @doc.type page
 * @doc.purpose Development backlog management
 * @doc.layer product
 */

import React, { useState } from 'react';
import { useParams, NavLink } from 'react-router';
import {
  ListTodo,
  Plus,
  Filter,
  ArrowUpDown,
  Search,
  Tag,
  Users,
  MoreHorizontal,
} from 'lucide-react';
import { cn } from '../../utils/cn';
import { ROUTES } from '../../router/paths';

type Priority = 'critical' | 'high' | 'medium' | 'low';

interface BacklogItem {
  id: string;
  title: string;
  type: 'story' | 'bug' | 'task' | 'spike';
  priority: Priority;
  points?: number;
  epic?: string;
  assignee?: string;
  labels: string[];
}

const priorityColors: Record<Priority, string> = {
  critical: 'text-red-400 bg-red-500/10',
  high: 'text-orange-400 bg-orange-500/10',
  medium: 'text-yellow-400 bg-yellow-500/10',
  low: 'text-zinc-400 bg-zinc-500/10',
};

const BacklogPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();
  const [search, setSearch] = useState('');
  const [items] = useState<BacklogItem[]>([]);

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-8">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-blue-500/10">
              <ListTodo className="w-6 h-6 text-blue-400" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Backlog</h1>
              <p className="text-zinc-400">
                {items.length} items · Prioritize and estimate work
              </p>
            </div>
          </div>
          <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
            <Plus className="w-4 h-4" />
            New Item
          </button>
        </div>

        {/* Toolbar */}
        <div className="flex items-center gap-3 mb-6">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-zinc-400" />
            <input
              type="text"
              placeholder="Search backlog..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full pl-10 pr-4 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-sm text-white placeholder-zinc-500 focus:border-violet-500 focus:outline-none"
            />
          </div>
          <button className="flex items-center gap-2 px-3 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-sm text-zinc-400 hover:text-white hover:border-zinc-700">
            <Filter className="w-4 h-4" /> Filter
          </button>
          <button className="flex items-center gap-2 px-3 py-2 rounded-lg bg-zinc-900 border border-zinc-800 text-sm text-zinc-400 hover:text-white hover:border-zinc-700">
            <ArrowUpDown className="w-4 h-4" /> Sort
          </button>
        </div>

        {/* Empty State */}
        {items.length === 0 && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <ListTodo className="w-12 h-12 text-zinc-600 mb-4" />
            <h3 className="text-lg font-semibold text-zinc-300 mb-2">
              No backlog items yet
            </h3>
            <p className="text-zinc-500 max-w-md mb-6">
              Start by adding stories, bugs, or tasks to your product backlog.
              Items can be prioritized and moved into sprints.
            </p>
            <button className="flex items-center gap-2 px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 transition-colors text-sm font-medium">
              <Plus className="w-4 h-4" />
              Create First Item
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default BacklogPage;
