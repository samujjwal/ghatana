/**
 * Decision Queue Component
 *
 * Displays pending decisions requiring approval, review, or escalation.
 * Provides bulk processing capabilities and filtering by type and status.
 *
 * @doc.type component
 * @doc.purpose Decision queue UI for pending approvals
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from 'react';
import { ChevronRight, Check, X, Clock, AlertTriangle, Shield, FileText } from 'lucide-react';
import { Typography, Button, Chip, Box, Surface as Paper } from '@ghatana/design-system';
import type { DecisionQueueItem } from '../../clients/dashboard';

interface DecisionQueueProps {
  items: DecisionQueueItem[];
  onApprove?: (itemId: string) => Promise<void>;
  onReject?: (itemId: string) => Promise<void>;
  onDefer?: (itemId: string) => Promise<void>;
  onViewAll?: () => void;
  projectId?: string;
}

/**
 * Decision Queue component
 * 
 * Displays a list of pending decisions requiring action.
 */
export function DecisionQueue({
  items,
  onApprove,
  onReject,
  onDefer,
  onViewAll,
  projectId,
}: DecisionQueueProps) {
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [processingItems, setProcessingItems] = useState<Set<string>>(new Set());

  const hasActions = Boolean(onApprove || onReject || onDefer);
  const pendingItems = items.filter(item => item.status === 'pending');
  const displayItems = projectId ? pendingItems.filter(item => item.projectId === projectId) : pendingItems;

  const toggleSelection = (itemId: string) => {
    setSelectedItems(prev => {
      const next = new Set(prev);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });
  };

  const handleApprove = async (itemId: string) => {
    if (!onApprove || processingItems.has(itemId)) return;
    
    setProcessingItems(prev => new Set(prev).add(itemId));
    try {
      await onApprove(itemId);
    } finally {
      setProcessingItems(prev => {
        const next = new Set(prev);
        next.delete(itemId);
        return next;
      });
    }
  };

  const handleReject = async (itemId: string) => {
    if (!onReject || processingItems.has(itemId)) return;
    
    setProcessingItems(prev => new Set(prev).add(itemId));
    try {
      await onReject(itemId);
    } finally {
      setProcessingItems(prev => {
        const next = new Set(prev);
        next.delete(itemId);
        return next;
      });
    }
  };

  const handleDefer = async (itemId: string) => {
    if (!onDefer || processingItems.has(itemId)) return;
    
    setProcessingItems(prev => new Set(prev).add(itemId));
    try {
      await onDefer(itemId);
    } finally {
      setProcessingItems(prev => {
        const next = new Set(prev);
        next.delete(itemId);
        return next;
      });
    }
  };

  const getTypeIcon = (type: DecisionQueueItem['type']) => {
    switch (type) {
      case 'approval':
        return <Shield className="w-5 h-5 text-blue-600" />;
      case 'review':
        return <FileText className="w-5 h-5 text-purple-600" />;
      case 'escalation':
        return <AlertTriangle className="w-5 h-5 text-orange-600" />;
      default:
        return null;
    }
  };

  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'critical':
        return 'text-red-600 bg-red-100';
      case 'high':
        return 'text-orange-600 bg-orange-100';
      case 'medium':
        return 'text-yellow-600 bg-yellow-100';
      case 'low':
        return 'text-green-600 bg-green-100';
      default:
        return 'text-gray-600 bg-gray-100';
    }
  };

  return (
    <div className="mb-10">
      <div className="flex justify-between items-center mb-2">
        <Typography className="flex items-center gap-2 font-bold text-lg">
          Decision Queue
          <Chip label={displayItems.length} size="sm" />
        </Typography>
        <Button size="sm" endIcon={<ChevronRight />} onClick={onViewAll}>View all</Button>
      </div>
      <Paper className="rounded-lg overflow-hidden border">
        {displayItems.length === 0 ? (
          <div className="p-8 text-center text-gray-500 dark:text-gray-400">
            <Check className="mb-2 w-20 h-20 opacity-50 mx-auto" />
            <Typography>
              No pending decisions. You're all caught up!
            </Typography>
          </div>
        ) : (
          <>
            <div className="divide-y divide-gray-200 dark:divide-gray-700">
              {displayItems.map((item) => (
                <div
                  key={item.id}
                  className={`py-4 px-4 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors ${selectedItems.has(item.id) ? 'bg-blue-50 dark:bg-blue-900/20' : ''}`}
                >
                  <div className="flex items-center gap-4">
                    {hasActions && (
                      <input
                        type="checkbox"
                        checked={selectedItems.has(item.id)}
                        onChange={() => toggleSelection(item.id)}
                        className="w-4 h-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      />
                    )}
                    <div className="flex-shrink-0">
                      {getTypeIcon(item.type)}
                    </div>
                    <div className="flex-1 min-w-0">
                      <Typography className="text-lg font-medium truncate">
                        {item.title}
                      </Typography>
                      <Typography className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                        {item.description}
                      </Typography>
                      <div className="flex items-center gap-4 mt-2">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${getPriorityColor(item.priority)}`}>
                          {item.priority}
                        </span>
                        <span className="flex items-center gap-1 text-sm text-gray-500">
                          <Clock className="w-4 h-4" />
                          {new Date(item.requestedAt).toLocaleDateString()}
                        </span>
                        {item.dueDate && (
                          <span className="text-sm text-gray-500">
                            Due: {new Date(item.dueDate).toLocaleDateString()}
                          </span>
                        )}
                        <span className="text-sm text-gray-500">
                          Requested by: {item.requestedBy}
                        </span>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {hasActions && (
                        <div className="flex gap-1">
                          <button
                            onClick={() => handleApprove(item.id)}
                            disabled={processingItems.has(item.id)}
                            className="p-2 rounded-md bg-green-100 text-green-700 hover:bg-green-200 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            title="Approve"
                          >
                            <Check className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleReject(item.id)}
                            disabled={processingItems.has(item.id)}
                            className="p-2 rounded-md bg-red-100 text-red-700 hover:bg-red-200 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            title="Reject"
                          >
                            <X className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleDefer(item.id)}
                            disabled={processingItems.has(item.id)}
                            className="p-2 rounded-md bg-gray-100 text-gray-700 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                            title="Defer"
                          >
                            <Clock className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                      <ChevronRight className="w-5 h-5 text-gray-400" />
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="p-2 text-center bg-gray-100 dark:bg-gray-800">
              <Button size="sm" endIcon={<ChevronRight />} onClick={onViewAll}>Go to Decision Queue</Button>
            </div>
          </>
        )}
      </Paper>
    </div>
  );
}
