/**
 * Moments Page
 * Browse, search, and filter captured Moments
 * Phase 1 Week 11: Enhanced with linking UI
 */

import { useState } from 'react';
import { useSearchMoments, useDeleteMoment, useMomentLinks } from '../hooks/use-api';
import Layout from '../components/Layout';
import { Search, Filter, Trash2, Calendar, Link2, ChevronDown, ChevronUp } from 'lucide-react';
import { formatDistanceToNow, format } from 'date-fns';
import { MomentLinkDialog, LinkedMomentsPanel, LinkSuggestionsCard } from '../components/Links';

interface Moment {
  id: string;
  contentText: string;
  capturedAt: string;
  importance?: number;
  emotions: string[];
  tags: string[];
  sphere: { name: string };
  user?: { displayName: string };
}

export default function MomentsPage() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [expandedMomentId, setExpandedMomentId] = useState<string | null>(null);
  const [linkDialogMoment, setLinkDialogMoment] = useState<Moment | null>(null);

  const { data, refetch } = useSearchMoments({
    query: searchQuery || undefined,
    tags: selectedTags.length > 0 ? selectedTags : undefined,
    limit: 20,
  });

  const deleteMoment = useDeleteMoment();

  // Get links for expanded moment
  const { data: linksData } = useMomentLinks(expandedMomentId || '', {
    direction: 'both',
  });

  const existingLinkIds = linksData?.links?.map((l: { linkedMomentId: string }) => l.linkedMomentId) || [];

  const handleDelete = async (id: string) => {
    if (confirm('Are you sure you want to delete this moment?')) {
      try {
        await deleteMoment.mutateAsync(id);
        refetch();
      } catch (error) {
        console.error('Failed to delete moment:', error);
        alert('Failed to delete moment');
      }
    }
  };

  const handleToggleExpand = (momentId: string) => {
    setExpandedMomentId((prev) => (prev === momentId ? null : momentId));
  };

  const handleOpenLinkDialog = (moment: Moment) => {
    setLinkDialogMoment(moment);
  };

  const handleCloseLinkDialog = () => {
    setLinkDialogMoment(null);
  };

  return (
    <Layout>
      <div className="space-y-6">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Your Moments</h1>
          <p className="mt-2 text-gray-600">
            Browse and search through your captured moments
          </p>
        </div>

        {/* Search and Filters */}
        <div className="card">
          <div className="flex gap-4">
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
              <input
                type="text"
                placeholder="Search moments..."
                className="input pl-10"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <button className="btn-secondary inline-flex items-center">
              <Filter className="w-4 h-4 mr-2" />
              Filters
            </button>
          </div>
        </div>

        {/* Moments List */}
        {data && data.moments.length > 0 ? (
          <div className="space-y-4">
            <div className="text-sm text-gray-600">
              Showing {data.moments.length} of {data.totalCount} moments
            </div>

            {data.moments.map((moment: Moment) => (
              <div key={moment.id} className="card hover:shadow-md transition-shadow">
                <div className="flex justify-between items-start mb-3">
                  <div className="flex items-center gap-2">
                    <span className="inline-flex items-center px-3 py-1 rounded-md text-sm font-medium bg-primary-100 text-primary-700">
                      {moment.sphere.name}
                    </span>
                    {moment.importance && moment.importance >= 4 && (
                      <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-yellow-100 text-yellow-800">
                        Important
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleOpenLinkDialog(moment)}
                      className="text-gray-400 hover:text-primary-600 transition-colors"
                      title="Link to another moment"
                    >
                      <Link2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleToggleExpand(moment.id)}
                      className="text-gray-400 hover:text-gray-600 transition-colors"
                      title={expandedMomentId === moment.id ? 'Collapse' : 'Expand links'}
                    >
                      {expandedMomentId === moment.id ? (
                        <ChevronUp className="w-4 h-4" />
                      ) : (
                        <ChevronDown className="w-4 h-4" />
                      )}
                    </button>
                    <button
                      onClick={() => handleDelete(moment.id)}
                      className="text-gray-400 hover:text-red-600 transition-colors"
                      title="Delete moment"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <p className="text-gray-800 mb-3 whitespace-pre-wrap">
                  {moment.contentText}
                </p>

                {moment.emotions.length > 0 && (
                  <div className="flex flex-wrap gap-2 mb-3">
                    {moment.emotions.map((emotion) => (
                      <span
                        key={emotion}
                        className="inline-flex items-center px-2 py-1 rounded-full text-xs bg-purple-50 text-purple-700"
                      >
                        {emotion}
                      </span>
                    ))}
                  </div>
                )}

                {moment.tags.length > 0 && (
                  <div className="flex flex-wrap gap-2 mb-3">
                    {moment.tags.map((tag) => (
                      <span
                        key={tag}
                        className="inline-flex items-center px-2 py-1 rounded text-xs bg-gray-100 text-gray-700"
                      >
                        #{tag}
                      </span>
                    ))}
                  </div>
                )}

                <div className="flex items-center justify-between text-xs text-gray-500 pt-2 border-t">
                  <div className="flex items-center gap-4">
                    <span className="inline-flex items-center">
                      <Calendar className="w-3 h-3 mr-1" />
                      {format(new Date(moment.capturedAt), 'MMM d, yyyy')}
                    </span>
                    <span>
                      {formatDistanceToNow(new Date(moment.capturedAt), { addSuffix: true })}
                    </span>
                  </div>
                  {moment.user?.displayName && (
                    <span>by {moment.user.displayName}</span>
                  )}
                </div>

                {/* Expandable Links Section */}
                {expandedMomentId === moment.id && (
                  <div className="mt-4 space-y-4 border-t pt-4">
                    <LinkedMomentsPanel
                      momentId={moment.id}
                      onLinkMoment={() => handleOpenLinkDialog(moment)}
                    />
                    <LinkSuggestionsCard
                      momentId={moment.id}
                      existingLinkIds={existingLinkIds}
                    />
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <div className="card text-center py-12">
            <div className="text-gray-500">
              <Search className="w-12 h-12 mx-auto mb-3 opacity-50" />
              <p className="text-lg font-medium">No moments found</p>
              <p className="text-sm mt-1">Try adjusting your search or filters</p>
            </div>
          </div>
        )}
      </div>

      {/* Link Dialog */}
      {linkDialogMoment && (
        <MomentLinkDialog
          isOpen={!!linkDialogMoment}
          onClose={handleCloseLinkDialog}
          sourceMoment={{
            id: linkDialogMoment.id,
            contentText: linkDialogMoment.contentText,
            capturedAt: linkDialogMoment.capturedAt,
          }}
          existingLinkIds={existingLinkIds}
        />
      )}
    </Layout>
  );
}