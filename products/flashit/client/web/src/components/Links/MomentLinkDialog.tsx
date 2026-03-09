/**
 * MomentLinkDialog - Modal for creating links between moments
 * Phase 1 Week 11: Linking & Temporal Arcs
 */

import { useState, useMemo } from 'react';
import { X, Link2, Search, Loader2, ArrowRight, Sparkles } from 'lucide-react';
import { useSearchMoments, useCreateMomentLink, useLinkSuggestions } from '../../hooks/use-api';
import { formatDistanceToNow } from 'date-fns';

export interface MomentLinkDialogProps {
  isOpen: boolean;
  onClose: () => void;
  sourceMoment: {
    id: string;
    contentText: string;
    sphereId: string;
    sphereName: string;
  };
  onLinkCreated?: () => void;
}

// Link types with descriptions
const LINK_TYPES = [
  { value: 'related', label: 'Related', description: 'General relationship between moments' },
  { value: 'follows', label: 'Follows', description: 'This moment happened after the linked one' },
  { value: 'precedes', label: 'Precedes', description: 'This moment happened before the linked one' },
  { value: 'references', label: 'References', description: 'Direct reference to another moment' },
  { value: 'causes', label: 'Causes', description: 'Causal relationship - this led to that' },
  { value: 'similar', label: 'Similar', description: 'Similar content or context' },
  { value: 'contradicts', label: 'Contradicts', description: 'Opposing or conflicting ideas' },
  { value: 'elaborates', label: 'Elaborates', description: 'Expands on the linked moment' },
  { value: 'summarizes', label: 'Summarizes', description: 'Summary of the linked moment' },
] as const;

export default function MomentLinkDialog({
  isOpen,
  onClose,
  sourceMoment,
  onLinkCreated,
}: MomentLinkDialogProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedMomentId, setSelectedMomentId] = useState<string | null>(null);
  const [selectedLinkType, setSelectedLinkType] = useState<string>('related');
  const [showSuggestions, setShowSuggestions] = useState(true);

  const createLink = useCreateMomentLink();

  // Search for moments to link
  const { data: searchResults, isLoading: isSearching } = useSearchMoments({
    query: searchQuery || undefined,
    limit: 10,
  });

  // Get AI suggestions
  const { data: suggestions, isLoading: isLoadingSuggestions } = useLinkSuggestions(
    sourceMoment.id,
    { limit: 5 }
  );

  // Filter out the source moment from results
  const filteredResults = useMemo(() => {
    if (!searchResults?.moments) return [];
    return searchResults.moments.filter((m: { id: string }) => m.id !== sourceMoment.id);
  }, [searchResults, sourceMoment.id]);

  const handleCreateLink = async () => {
    if (!selectedMomentId || !selectedLinkType) return;

    try {
      await createLink.mutateAsync({
        momentId: sourceMoment.id,
        data: {
          targetMomentId: selectedMomentId,
          linkType: selectedLinkType,
        },
      });
      onLinkCreated?.();
      onClose();
    } catch (error) {
      console.error('Failed to create link:', error);
      alert('Failed to create link. Please try again.');
    }
  };

  const handleSelectSuggestion = (suggestion: { targetMomentId: string; suggestedLinkType: string }) => {
    setSelectedMomentId(suggestion.targetMomentId);
    setSelectedLinkType(suggestion.suggestedLinkType);
    setShowSuggestions(false);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto">
      <div className="flex min-h-screen items-center justify-center p-4">
        {/* Backdrop */}
        <div 
          className="fixed inset-0 bg-black/50 transition-opacity" 
          onClick={onClose}
        />

        {/* Dialog */}
        <div className="relative w-full max-w-2xl rounded-xl bg-white shadow-xl">
          {/* Header */}
          <div className="flex items-center justify-between border-b px-6 py-4">
            <div className="flex items-center gap-3">
              <Link2 className="h-5 w-5 text-primary-600" />
              <h2 className="text-lg font-semibold text-gray-900">Link Moment</h2>
            </div>
            <button
              onClick={onClose}
              className="rounded-lg p-2 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Content */}
          <div className="p-6 space-y-6">
            {/* Source Moment Preview */}
            <div className="rounded-lg bg-gray-50 p-4">
              <div className="text-xs font-medium text-gray-500 mb-1">Linking from:</div>
              <p className="text-gray-800 line-clamp-2">{sourceMoment.contentText}</p>
              <div className="mt-2 text-xs text-gray-500">{sourceMoment.sphereName}</div>
            </div>

            {/* AI Suggestions */}
            {showSuggestions && suggestions?.suggestions && suggestions.suggestions.length > 0 && (
              <div className="space-y-3">
                <div className="flex items-center gap-2 text-sm font-medium text-gray-700">
                  <Sparkles className="h-4 w-4 text-amber-500" />
                  AI Suggested Links
                </div>
                <div className="space-y-2">
                  {isLoadingSuggestions ? (
                    <div className="flex items-center justify-center py-4">
                      <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
                    </div>
                  ) : (
                    suggestions.suggestions.map((suggestion: {
                      targetMomentId: string;
                      targetTitle: string;
                      suggestedLinkType: string;
                      confidence: number;
                      reason: string;
                    }) => (
                      <button
                        key={suggestion.targetMomentId}
                        onClick={() => handleSelectSuggestion(suggestion)}
                        className="w-full rounded-lg border border-amber-200 bg-amber-50 p-3 text-left hover:border-amber-300 hover:bg-amber-100 transition-colors"
                      >
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <p className="text-sm text-gray-800 line-clamp-1">
                              {suggestion.targetTitle}
                            </p>
                            <p className="mt-1 text-xs text-gray-500">
                              {suggestion.reason}
                            </p>
                          </div>
                          <div className="ml-3 flex flex-col items-end">
                            <span className="inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                              {suggestion.suggestedLinkType}
                            </span>
                            <span className="mt-1 text-xs text-gray-400">
                              {Math.round(suggestion.confidence * 100)}% match
                            </span>
                          </div>
                        </div>
                      </button>
                    ))
                  )}
                </div>
              </div>
            )}

            {/* Search */}
            <div className="space-y-3">
              <div className="text-sm font-medium text-gray-700">Or search for a moment:</div>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
                <input
                  type="text"
                  placeholder="Search moments..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 py-2 pl-10 pr-4 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
                />
              </div>

              {/* Search Results */}
              {searchQuery && (
                <div className="max-h-48 overflow-y-auto rounded-lg border border-gray-200">
                  {isSearching ? (
                    <div className="flex items-center justify-center py-4">
                      <Loader2 className="h-5 w-5 animate-spin text-gray-400" />
                    </div>
                  ) : filteredResults.length > 0 ? (
                    filteredResults.map((moment: {
                      id: string;
                      contentText: string;
                      capturedAt: string;
                      sphere: { name: string };
                    }) => (
                      <button
                        key={moment.id}
                        onClick={() => {
                          setSelectedMomentId(moment.id);
                          setShowSuggestions(false);
                        }}
                        className={`w-full border-b border-gray-100 p-3 text-left last:border-0 hover:bg-gray-50 ${
                          selectedMomentId === moment.id ? 'bg-primary-50' : ''
                        }`}
                      >
                        <p className="text-sm text-gray-800 line-clamp-2">
                          {moment.contentText}
                        </p>
                        <div className="mt-1 flex items-center gap-2 text-xs text-gray-500">
                          <span>{moment.sphere.name}</span>
                          <span>•</span>
                          <span>{formatDistanceToNow(new Date(moment.capturedAt), { addSuffix: true })}</span>
                        </div>
                      </button>
                    ))
                  ) : (
                    <div className="py-4 text-center text-sm text-gray-500">
                      No moments found
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Link Type Selection */}
            {selectedMomentId && (
              <div className="space-y-3">
                <div className="text-sm font-medium text-gray-700">Link type:</div>
                <div className="grid grid-cols-3 gap-2">
                  {LINK_TYPES.map((type) => (
                    <button
                      key={type.value}
                      onClick={() => setSelectedLinkType(type.value)}
                      className={`rounded-lg border p-2 text-center transition-colors ${
                        selectedLinkType === type.value
                          ? 'border-primary-500 bg-primary-50 text-primary-700'
                          : 'border-gray-200 text-gray-700 hover:border-gray-300 hover:bg-gray-50'
                      }`}
                      title={type.description}
                    >
                      <span className="text-sm font-medium">{type.label}</span>
                    </button>
                  ))}
                </div>
                <p className="text-xs text-gray-500">
                  {LINK_TYPES.find((t) => t.value === selectedLinkType)?.description}
                </p>
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t px-6 py-4">
            <button
              onClick={onClose}
              className="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100"
            >
              Cancel
            </button>
            <button
              onClick={handleCreateLink}
              disabled={!selectedMomentId || createLink.isPending}
              className="inline-flex items-center gap-2 rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {createLink.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <ArrowRight className="h-4 w-4" />
              )}
              Create Link
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
