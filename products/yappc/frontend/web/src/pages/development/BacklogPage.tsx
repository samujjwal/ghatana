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
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { useTranslation } from '@ghatana/i18n';

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
  critical: 'text-destructive bg-destructive-bg/10',
  high: 'text-warning-color bg-warning-bg/10',
  medium: 'text-warning-color bg-warning-bg/10',
  low: 'text-fg-muted bg-surface-muted/10',
};

const BacklogPage: React.FC = () => {
    const { t } = useTranslation('common');
  const { projectId } = useParams<{ projectId: string }>();
  const [search, setSearch] = useState('');
  const [items] = useState<BacklogItem[]>([]);

  return (
    <div className="min-h-screen bg-surface text-white p-8">
      <div className="max-w-6xl mx-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-4">
            <div className="p-3 rounded-xl bg-info-bg/10">
              <ListTodo className="w-6 h-6 text-info-color" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">Backlog</h1>
              <p className="text-fg-muted">
                {items.length} items · Prioritize and estimate work
              </p>
            </div>
          </div>
          <Button
            className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium hover:bg-violet-500"
            startIcon={<Plus className="w-4 h-4" />}
          >
            New Item
          </Button>
        </div>

        {/* Toolbar */}
        <div className="flex items-center gap-3 mb-6">
          <div className="relative flex-1 max-w-md">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-fg-muted" />
            <Input
              type="text"
              placeholder={t('backlog.searchPlaceholder')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              fullWidth
              size="sm"
              className="pl-10 pr-4 py-2 rounded-lg bg-surface border-border text-sm text-white placeholder-zinc-500 focus:border-violet-500"
            />
          </div>
          <Button
            variant="outline"
            size="sm"
            startIcon={<Filter className="w-4 h-4" />}
            className="rounded-lg bg-surface border-border px-3 py-2 text-sm text-fg-muted hover:text-white hover:border-border"
          >
            Filter
          </Button>
          <Button
            variant="outline"
            size="sm"
            startIcon={<ArrowUpDown className="w-4 h-4" />}
            className="rounded-lg bg-surface border-border px-3 py-2 text-sm text-fg-muted hover:text-white hover:border-border"
          >
            Sort
          </Button>
        </div>

        {/* Empty State */}
        {items.length === 0 && (
          <div className="flex flex-col items-center justify-center py-20 text-center">
            <ListTodo className="w-12 h-12 text-fg-muted mb-4" />
            <h3 className="text-lg font-semibold text-fg-muted mb-2">
              No backlog items yet
            </h3>
            <p className="text-fg-muted max-w-md mb-6">
              Start by adding stories, bugs, or tasks to your product backlog.
              Items can be prioritized and moved into sprints.
            </p>
            <Button
              className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium hover:bg-violet-500"
              startIcon={<Plus className="w-4 h-4" />}
            >
              Create First Item
            </Button>
          </div>
        )}
      </div>
    </div>
  );
};

export default BacklogPage;
