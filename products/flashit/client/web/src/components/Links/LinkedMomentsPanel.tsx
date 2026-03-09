/**
 * LinkedMomentsPanel - Displays links for a moment with visualization
 * Phase 1 Week 11: Linking & Temporal Arcs
 */

import { useState } from 'react';
import { Link2, ArrowRight, ArrowLeft, Trash2, ChevronDown, ChevronUp, Loader2 } from 'lucide-react';
import { useMomentLinks, useDeleteMomentLink } from '../../hooks/use-api';
import { formatDistanceToNow } from 'date-fns';

export interface LinkedMomentsPanelProps {
  momentId: string;
  onOpenLinkDialog?: () => void;
  onNavigateToMoment?: (momentId: string) => void;
}

// Link type colors
const LINK_TYPE_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  related: { bg: 'bg-gray-100', text: 'text-gray-700', border: 'border-gray-200' },
  follows: { bg: 'bg-blue-100', text: 'text-blue-700', border: 'border-blue-200' },
  precedes: { bg: 'bg-purple-100', text: 'text-purple-700', border: 'border-purple-200' },
  references: { bg: 'bg-green-100', text: 'text-green-700', border: 'border-green-200' },
  causes: { bg: 'bg-red-100', text: 'text-red-700', border: 'border-red-200' },
  similar: { bg: 'bg-yellow-100', text: 'text-yellow-700', border: 'border-yellow-200' },
  contradicts: { bg: 'bg-orange-100', text: 'text-orange-700', border: 'border-orange-200' },
  elaborates: { bg: 'bg-indigo-100', text: 'text-indigo-700', border: 'border-indigo-200' },
  summarizes: { bg: 'bg-teal-100', text: 'text-teal-700', border: 'border-teal-200' },
};

interface MomentLink {
  id: string;
  linkType: string;
  direction: 'outgoing' | 'incoming';
  targetMoment?: {
    id: string;
    contentText: string;
    capturedAt: string;
    sphere: { name: string };
  };
  sourceMoment?: {
    id: string;
    contentText: string;
    capturedAt: string;
    sphere: { name: string };
  };
  createdAt: string;
}

export default function LinkedMomentsPanel({
  momentId,
  onOpenLinkDialog,
  onNavigateToMoment,
}: LinkedMomentsPanelProps) {
  const [isExpanded, setIsExpanded] = useState(true);
  const [filter, setFilter] = useState<'all' | 'outgoing' | 'incoming'>('all');

  const { data, isLoading, refetch } = useMomentLinks(momentId, {
    direction: filter === 'all' ? 'both' : filter,
  });

  const deleteLink = useDeleteMomentLink();

  const handleDeleteLink = async (linkId: string) => {
    if (!confirm('Are you sure you want to remove this link?')) return;

    try {
      await deleteLink.mutateAsync({ momentId, linkId });
      refetch();
    } catch (error) {
      console.error('Failed to delete link:', error);
      alert('Failed to remove link. Please try again.');
    }
  };

  const getLinkedMoment = (link: MomentLink) => {
    return link.direction === 'outgoing' ? link.targetMoment : link.sourceMoment;
  };

  const links: MomentLink[] = data?.links || [];
  const outgoingCount = links.filter((l) => l.direction === 'outgoing').length;
  const incomingCount = links.filter((l) => l.direction === 'incoming').length;

  return (
    <div className="rounded-lg border border-gray-200 bg-white">
      {/* Header */}
      <div
        className="flex cursor-pointer items-center justify-between px-4 py-3"
        onClick={() => setIsExpanded(!isExpanded)}
      >
        <div className="flex items-center gap-2">
          <Link2 className="h-4 w-4 text-gray-500" />
          <span className="font-medium text-gray-900">Linked Moments</span>
          <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600">
            {links.length}
          </span>
        </div>
        {isExpanded ? (
          <ChevronUp className="h-4 w-4 text-gray-400" />
        ) : (
          <ChevronDown className="h-4 w-4 text-gray-400" />
        )}
      </div>

      {isExpanded && (
        <div className="border-t border-gray-200">
          {/* Actions */}
          <div className="flex items-center justify-between px-4 py-2 bg-gray-50">
            <div className="flex items-center gap-2">
              <button
                onClick={() => setFilter('all')}
                className={`rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                  filter === 'all'
                    ? 'bg-gray-200 text-gray-800'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                All ({links.length})
              </button>
              <button
                onClick={() => setFilter('outgoing')}
                className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                  filter === 'outgoing'
                    ? 'bg-blue-100 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                <ArrowRight className="h-3 w-3" />
                Outgoing ({outgoingCount})
              </button>
              <button
                onClick={() => setFilter('incoming')}
                className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                  filter === 'incoming'
                    ? 'bg-purple-100 text-purple-700'
                    : 'text-gray-600 hover:bg-gray-100'
                }`}
              >
                <ArrowLeft className="h-3 w-3" />
                Incoming ({incomingCount})
              </button>
            </div>
            {onOpenLinkDialog && (
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onOpenLinkDialog();
                }}
                className="inline-flex items-center gap-1 rounded-md bg-primary-600 px-3 py-1 text-xs font-medium text-white hover:bg-primary-700"
              >
                <Link2 className="h-3 w-3" />
                Add Link
              </button>
            )}
          </div>

          {/* Links List */}
          <div className="max-h-80 overflow-y-auto">
            {isLoading ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
              </div>
            ) : links.length === 0 ? (
              <div className="py-8 text-center">
                <Link2 className="mx-auto h-8 w-8 text-gray-300" />
                <p className="mt-2 text-sm text-gray-500">No links yet</p>
                {onOpenLinkDialog && (
                  <button
                    onClick={onOpenLinkDialog}
                    className="mt-3 text-sm font-medium text-primary-600 hover:text-primary-700"
                  >
                    Create your first link
                  </button>
                )}
              </div>
            ) : (
              <div className="divide-y divide-gray-100">
                {links.map((link) => {
                  const linkedMoment = getLinkedMoment(link);
                  const colors = LINK_TYPE_COLORS[link.linkType] || LINK_TYPE_COLORS.related;

                  return (
                    <div
                      key={link.id}
                      className="group flex items-start gap-3 px-4 py-3 hover:bg-gray-50"
                    >
                      {/* Direction Icon */}
                      <div className={`mt-1 rounded-full p-1 ${colors.bg}`}>
                        {link.direction === 'outgoing' ? (
                          <ArrowRight className={`h-3 w-3 ${colors.text}`} />
                        ) : (
                          <ArrowLeft className={`h-3 w-3 ${colors.text}`} />
                        )}
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-medium ${colors.bg} ${colors.text}`}
                          >
                            {link.linkType}
                          </span>
                          <span className="text-xs text-gray-400">
                            {formatDistanceToNow(new Date(link.createdAt), { addSuffix: true })}
                          </span>
                        </div>
                        {linkedMoment && (
                          <button
                            onClick={() => onNavigateToMoment?.(linkedMoment.id)}
                            className="block w-full text-left"
                          >
                            <p className="text-sm text-gray-800 line-clamp-2 hover:text-primary-600">
                              {linkedMoment.contentText}
                            </p>
                            <div className="mt-1 flex items-center gap-2 text-xs text-gray-500">
                              <span>{linkedMoment.sphere.name}</span>
                              <span>•</span>
                              <span>
                                {formatDistanceToNow(new Date(linkedMoment.capturedAt), {
                                  addSuffix: true,
                                })}
                              </span>
                            </div>
                          </button>
                        )}
                      </div>

                      {/* Delete Action */}
                      <button
                        onClick={() => handleDeleteLink(link.id)}
                        className="mt-1 rounded p-1 text-gray-400 opacity-0 transition-opacity hover:bg-red-50 hover:text-red-600 group-hover:opacity-100"
                        title="Remove link"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Stats Footer */}
          {links.length > 0 && (
            <div className="border-t border-gray-200 bg-gray-50 px-4 py-2">
              <div className="flex items-center justify-between text-xs text-gray-500">
                <span>{outgoingCount} outgoing, {incomingCount} incoming</span>
                <span>
                  Link types: {[...new Set(links.map((l) => l.linkType))].join(', ')}
                </span>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
