/**
 * Mobile Backlog Route
 * 
 * Mobile-optimized backlog management with touch interactions.
 * Task prioritization, filtering, and progress tracking.
 */

import React, { useState } from 'react';
import { useParams } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { logger } from '../../utils/Logger';

export interface BacklogItem {
  id: string;
  title: string;
  description: string;
  priority: 'low' | 'medium' | 'high' | 'critical';
  status: 'todo' | 'in-progress' | 'review' | 'done';
  assignee?: string;
  tags: string[];
  estimatedHours?: number;
  actualHours?: number;
  dueDate?: string;
  createdAt: string;
  updatedAt: string;
  projectId: string;
  sprintId?: string;
}

export interface BacklogFilter {
  priority: string;
  status: string;
  assignee: string;
  tags: string[];
  searchQuery: string;
}

export interface BacklogStats {
  total: number;
  byPriority: Record<string, number>;
  byStatus: Record<string, number>;
  estimatedHours: number;
  actualHours: number;
}

/**
 * Mobile Backlog Component
 */
export default function Component() {
  const { projectId } = useParams<{ projectId: string }>();
  const queryClient = useQueryClient();

  const [filter, setFilter] = useState<BacklogFilter>({
    priority: 'all',
    status: 'all',
    assignee: 'all',
    tags: [],
    searchQuery: ''
  });
  const [showFilters, setShowFilters] = useState(false);
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [sortBy, setSortBy] = useState<'priority' | 'dueDate' | 'title'>('priority');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  const BACKLOG_KEY = ['backlog', projectId, filter.priority, filter.status, sortBy, sortOrder] as const;

  // Fetch backlog items from API
  const { data: backlogItems = [], isLoading } = useQuery<BacklogItem[]>({
    queryKey: BACKLOG_KEY,
    queryFn: async () => {
      const params = new URLSearchParams();
      if (filter.priority !== 'all') params.set('priority', filter.priority);
      if (filter.status !== 'all') params.set('status', filter.status);
      params.set('sort', sortBy);
      params.set('order', sortOrder);
      const res = await fetch(`/api/projects/${projectId}/backlog?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json() as BacklogItem[];
      logger.info('Backlog items loaded', 'mobile-backlog', { count: data.length });
      return data;
    },
    enabled: !!projectId,
  });

  // Update item status
  const updateStatusMutation = useMutation({
    mutationFn: async ({ itemId, newStatus }: { itemId: string; newStatus: BacklogItem['status'] }) => {
      const res = await fetch(`/api/backlog/items/${itemId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return res.json() as Promise<BacklogItem>;
    },
    onSuccess: (_data, { itemId, newStatus }) => {
      logger.info('Item status updated', 'mobile-backlog', { itemId, newStatus });
      void queryClient.invalidateQueries({ queryKey: ['backlog', projectId] });
    },
    onError: (error, { itemId, newStatus }) => {
      logger.error('Failed to update item status', 'mobile-backlog', {
        itemId,
        newStatus,
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const updateItemStatus = (itemId: string, newStatus: BacklogItem['status']) =>
    updateStatusMutation.mutate({ itemId, newStatus });

  // Update item priority
  const updatePriorityMutation = useMutation({
    mutationFn: async ({ itemId, newPriority }: { itemId: string; newPriority: BacklogItem['priority'] }) => {
      const res = await fetch(`/api/backlog/items/${itemId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ priority: newPriority }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      return res.json() as Promise<BacklogItem>;
    },
    onSuccess: (_data, { itemId, newPriority }) => {
      logger.info('Item priority updated', 'mobile-backlog', { itemId, newPriority });
      void queryClient.invalidateQueries({ queryKey: ['backlog', projectId] });
    },
    onError: (error, { itemId, newPriority }) => {
      logger.error('Failed to update item priority', 'mobile-backlog', {
        itemId,
        newPriority,
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const updateItemPriority = (itemId: string, newPriority: BacklogItem['priority']) =>
    updatePriorityMutation.mutate({ itemId, newPriority });

  // Delete item
  const deleteMutation = useMutation({
    mutationFn: async (itemId: string) => {
      const res = await fetch(`/api/backlog/items/${itemId}`, { method: 'DELETE' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    },
    onSuccess: (_data, itemId) => {
      logger.info('Item deleted', 'mobile-backlog', { itemId });
      setSelectedItems(prev => {
        const next = new Set(prev);
        next.delete(itemId);
        return next;
      });
      void queryClient.invalidateQueries({ queryKey: ['backlog', projectId] });
    },
    onError: (error, itemId) => {
      logger.error('Failed to delete item', 'mobile-backlog', {
        itemId,
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const deleteItem = (itemId: string) => deleteMutation.mutate(itemId);

  // Toggle item selection
  const toggleItemSelection = (itemId: string) => {
    setSelectedItems(prev => {
      const newSet = new Set(prev);
      if (newSet.has(itemId)) {
        newSet.delete(itemId);
      } else {
        newSet.add(itemId);
      }
      return newSet;
    });
  };

  // Select all items
  const selectAllItems = () => {
    setSelectedItems(new Set(filteredItems.map(item => item.id)));
  };

  // Clear selection
  const clearSelection = () => {
    setSelectedItems(new Set());
  };

  // Bulk update selected items
  const bulkUpdateMutation = useMutation({
    mutationFn: async (updates: Partial<BacklogItem>) => {
      const res = await fetch('/api/backlog/items/bulk', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ids: Array.from(selectedItems), updates }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    },
    onSuccess: (_data, updates) => {
      logger.info('Bulk update completed', 'mobile-backlog', {
        itemCount: selectedItems.size,
        updates,
      });
      clearSelection();
      void queryClient.invalidateQueries({ queryKey: ['backlog', projectId] });
    },
    onError: (error) => {
      logger.error('Failed to bulk update items', 'mobile-backlog', {
        itemCount: selectedItems.size,
        error: error instanceof Error ? error.message : String(error),
      });
    },
  });
  const bulkUpdate = (updates: Partial<BacklogItem>) => bulkUpdateMutation.mutate(updates);

  // Filter and sort items
  const filteredItems = backlogItems
    .filter(item => {
      if (filter.priority !== 'all' && item.priority !== filter.priority) {
        return false;
      }
      if (filter.status !== 'all' && item.status !== filter.status) {
        return false;
      }
      if (filter.assignee !== 'all' && item.assignee !== filter.assignee) {
        return false;
      }
      if (filter.tags.length > 0 && !filter.tags.some(tag => item.tags.includes(tag))) {
        return false;
      }
      if (filter.searchQuery && !item.title.toLowerCase().includes(filter.searchQuery.toLowerCase()) &&
        !item.description.toLowerCase().includes(filter.searchQuery.toLowerCase())) {
        return false;
      }
      return true;
    })
    .sort((a, b) => {
      let comparison = 0;

      switch (sortBy) {
        case 'priority':
          const priorityOrder = { critical: 4, high: 3, medium: 2, low: 1 };
          comparison = priorityOrder[b.priority] - priorityOrder[a.priority];
          break;
        case 'dueDate':
          if (!a.dueDate && !b.dueDate) comparison = 0;
          else if (!a.dueDate) comparison = 1;
          else if (!b.dueDate) comparison = -1;
          else comparison = new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
          break;
        case 'title':
          comparison = a.title.localeCompare(b.title);
          break;
      }

      return sortOrder === 'asc' ? comparison : -comparison;
    });

  // Get priority color
  const getPriorityColor = (priority: BacklogItem['priority']) => {
    switch (priority) {
      case 'critical': return 'text-error-color bg-error-color/10';
      case 'high': return 'text-warning-color bg-warning-color/10';
      case 'medium': return 'text-info-color bg-info-color/10';
      case 'low': return 'text-success-color bg-success-color/10';
      default: return 'text-text-secondary bg-bg-surface';
    }
  };

  // Get status color
  const getStatusColor = (status: BacklogItem['status']) => {
    switch (status) {
      case 'done': return 'text-success-color bg-success-color/10';
      case 'review': return 'text-warning-color bg-warning-color/10';
      case 'in-progress': return 'text-info-color bg-info-color/10';
      case 'todo': return 'text-text-secondary bg-bg-surface';
      default: return 'text-text-secondary bg-bg-surface';
    }
  };

  // Format date
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: date.getFullYear() !== new Date().getFullYear() ? 'numeric' : undefined
    });
  };

  // Calculate stats
  const stats: BacklogStats = {
    total: backlogItems.length,
    byPriority: backlogItems.reduce((acc, item) => {
      acc[item.priority] = (acc[item.priority] || 0) + 1;
      return acc;
    }, {} as Record<string, number>),
    byStatus: backlogItems.reduce((acc, item) => {
      acc[item.status] = (acc[item.status] || 0) + 1;
      return acc;
    }, {} as Record<string, number>),
    estimatedHours: backlogItems.reduce((sum, item) => sum + (item.estimatedHours || 0), 0),
    actualHours: backlogItems.reduce((sum, item) => sum + (item.actualHours || 0), 0),
  };

  return (
    <div className="min-h-screen bg-bg-default">
      {/* Header */}
      <div className="bg-bg-paper border-b border-divider px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-2xl">📋</span>
            <h1 className="text-xl font-semibold text-text-primary">Backlog</h1>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => setShowFilters(!showFilters)}
              className="p-2 rounded-lg hover:bg-bg-surface transition-colors"
              aria-label="Filter backlog"
            >
              <span className="text-xl">🔍</span>
            </button>
            <button
              className="p-2 rounded-lg hover:bg-bg-surface transition-colors"
              aria-label="Sort backlog"
            >
              <span className="text-xl">📊</span>
            </button>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="bg-bg-surface border-b border-divider px-4 py-3">
        <div className="grid grid-cols-2 gap-4">
          <div className="text-center">
            <div className="text-2xl font-bold text-text-primary">{stats.total}</div>
            <div className="text-xs text-text-secondary">Total Items</div>
          </div>
          <div className="text-center">
            <div className="text-2xl font-bold text-primary-600">{stats.byStatus['todo'] || 0}</div>
            <div className="text-xs text-text-secondary">To Do</div>
          </div>
        </div>
      </div>

      {/* Filters */}
      {showFilters && (
        <div className="bg-bg-surface border-b border-divider px-4 py-3">
          <div className="space-y-3">
            <div>
              <input
                type="text"
                placeholder="Search backlog..."
                value={filter.searchQuery}
                onChange={(e) => setFilter(prev => ({ ...prev, searchQuery: e.target.value }))}
                className="w-full p-2 rounded-lg border border-divider bg-bg-paper text-text-primary"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-text-primary mb-1">Priority</label>
                <select
                  value={filter.priority}
                  onChange={(e) => setFilter(prev => ({ ...prev, priority: e.target.value }))}
                  className="w-full p-2 rounded-lg border border-divider bg-bg-paper text-text-primary text-sm"
                >
                  <option value="all">All</option>
                  <option value="critical">Critical</option>
                  <option value="high">High</option>
                  <option value="medium">Medium</option>
                  <option value="low">Low</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-text-primary mb-1">Status</label>
                <select
                  value={filter.status}
                  onChange={(e) => setFilter(prev => ({ ...prev, status: e.target.value }))}
                  className="w-full p-2 rounded-lg border border-divider bg-bg-paper text-text-primary text-sm"
                >
                  <option value="all">All</option>
                  <option value="todo">To Do</option>
                  <option value="in-progress">In Progress</option>
                  <option value="review">Review</option>
                  <option value="done">Done</option>
                </select>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Bulk Actions */}
      {selectedItems.size > 0 && (
        <div className="bg-primary-50 border-b border-primary-200 px-4 py-3">
          <div className="flex items-center justify-between">
            <span className="text-sm text-primary-700">
              {selectedItems.size} item{selectedItems.size !== 1 ? 's' : ''} selected
            </span>
            <div className="flex items-center gap-2">
              <button
                onClick={() => bulkUpdate({ status: 'done' })}
                className="text-xs text-primary-600 hover:text-primary-700 transition-colors"
              >
                Mark Done
              </button>
              <button
                onClick={clearSelection}
                className="text-xs text-error-color hover:text-error-color/80 transition-colors"
              >
                Clear
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Backlog List */}
      <div className="px-4 py-2">
        {isLoading ? (
          <div className="flex items-center justify-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
        ) : filteredItems.length === 0 ? (
          <div className="text-center py-8">
            <span className="text-4xl">📋</span>
            <p className="text-text-secondary mt-4">
              {backlogItems.length === 0 ? 'No backlog items yet' : 'No items match your filters'}
            </p>
          </div>
        ) : (
          <div className="space-y-2">
            {filteredItems.map((item) => (
              <div
                key={item.id}
                className={`bg-bg-paper rounded-lg border p-4 transition-all ${selectedItems.has(item.id) ? 'border-primary-500 bg-primary-50' : 'border-divider'
                  }`}
              >
                <div className="flex items-start gap-3">
                  <input
                    type="checkbox"
                    checked={selectedItems.has(item.id)}
                    onChange={() => toggleItemSelection(item.id)}
                    className="mt-1 rounded border-border-divider"
                  />
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between mb-2">
                      <h3 className="font-medium text-text-primary leading-tight">
                        {item.title}
                      </h3>
                      <button
                        onClick={() => deleteItem(item.id)}
                        className="p-1 rounded hover:bg-bg-surface transition-colors"
                        aria-label="Delete item"
                      >
                        <span className="text-sm">🗑️</span>
                      </button>
                    </div>

                    <p className="text-sm text-text-secondary mb-3 line-clamp-2">
                      {item.description}
                    </p>

                    <div className="flex flex-wrap items-center gap-2 mb-3">
                      <span className={`text-xs px-2 py-1 rounded-full ${getPriorityColor(item.priority)}`}>
                        {item.priority}
                      </span>
                      <span className={`text-xs px-2 py-1 rounded-full ${getStatusColor(item.status)}`}>
                        {item.status.replace('-', ' ')}
                      </span>
                      {item.assignee && (
                        <span className="text-xs px-2 py-1 rounded-full bg-bg-surface text-text-secondary">
                          👤 {item.assignee}
                        </span>
                      )}
                      {item.dueDate && (
                        <span className="text-xs px-2 py-1 rounded-full bg-bg-surface text-text-secondary">
                          📅 {formatDate(item.dueDate)}
                        </span>
                      )}
                    </div>

                    {item.tags.length > 0 && (
                      <div className="flex flex-wrap gap-1 mb-3">
                        {item.tags.map((tag, index) => (
                          <span
                            key={index}
                            className="text-xs px-2 py-1 bg-bg-surface text-text-secondary rounded"
                          >
                            #{tag}
                          </span>
                        ))}
                      </div>
                    )}

                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-4 text-xs text-text-tertiary">
                        {item.estimatedHours && (
                          <span>⏱️ {item.estimatedHours}h est.</span>
                        )}
                        {item.actualHours && (
                          <span>✅ {item.actualHours}h actual</span>
                        )}
                      </div>

                      <div className="flex items-center gap-2">
                        <select
                          value={item.status}
                          onChange={(e) => updateItemStatus(item.id, e.target.value as BacklogItem['status'])}
                          className="text-xs p-1 rounded border border-divider bg-bg-paper"
                        >
                          <option value="todo">To Do</option>
                          <option value="in-progress">In Progress</option>
                          <option value="review">Review</option>
                          <option value="done">Done</option>
                        </select>

                        <select
                          value={item.priority}
                          onChange={(e) => updateItemPriority(item.id, e.target.value as BacklogItem['priority'])}
                          className="text-xs p-1 rounded border border-divider bg-bg-paper"
                        >
                          <option value="low">Low</option>
                          <option value="medium">Medium</option>
                          <option value="high">High</option>
                          <option value="critical">Critical</option>
                        </select>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export const ErrorBoundary = () => {
  return (
    <div className="min-h-screen bg-bg-default flex items-center justify-center px-4">
      <div className="text-center">
        <span className="text-4xl">📋</span>
        <h2 className="text-xl font-semibold text-text-primary mb-2 mt-4">
          Backlog Error
        </h2>
        <p className="text-text-secondary">
          Unable to load backlog items. Please try again later.
        </p>
      </div>
    </div>
  );
};