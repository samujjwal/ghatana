/**
 * SavedSearches Component for Flashit Web
 * Manages user's saved searches and search history
 *
 * @doc.type component
 * @doc.purpose Display and manage saved searches and search history
 * @doc.layer product
 * @doc.pattern ListComponent
 */

import React, { useState, useCallback } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search,
  Star,
  Clock,
  Trash2,
  MoreVertical,
  Edit2,
  Check,
  X,
  ChevronRight,
  BookmarkPlus,
  History,
} from 'lucide-react';
import { api } from '../lib/api';

// ============================================================================
// Types
// ============================================================================

interface SavedSearch {
  id: string;
  name: string;
  filters: SearchFilters;
  isDefault: boolean;
  createdAt: Date;
  lastUsedAt: Date;
  useCount: number;
}

interface SearchHistory {
  id: string;
  query: string;
  filters: SearchFilters;
  resultCount: number;
  searchedAt: Date;
}

interface SearchFilters {
  query?: string;
  sphereIds?: string[];
  emotions?: string[];
  tags?: string[];
  contentTypes?: string[];
  startDate?: Date;
  endDate?: Date;
  importanceMin?: number;
  importanceMax?: number;
  hasMedia?: boolean;
  hasTranscript?: boolean;
}

interface SavedSearchesProps {
  onSelectSearch: (filters: SearchFilters) => void;
  currentFilters?: SearchFilters;
  className?: string;
}

// ============================================================================
// API Functions
// ============================================================================

const fetchSavedSearches = async (): Promise<SavedSearch[]> => {
  const response = await api.get('/search/saved');
  return response.data.map((s: SavedSearch) => ({
    ...s,
    createdAt: new Date(s.createdAt),
    lastUsedAt: new Date(s.lastUsedAt),
  }));
};

const fetchSearchHistory = async (limit: number = 20): Promise<SearchHistory[]> => {
  const response = await api.get(`/search/history?limit=${limit}`);
  return response.data.map((h: SearchHistory) => ({
    ...h,
    searchedAt: new Date(h.searchedAt),
  }));
};

const deleteSavedSearch = async (id: string): Promise<void> => {
  await api.delete(`/search/saved/${id}`);
};

const updateSavedSearch = async (id: string, data: Partial<SavedSearch>): Promise<SavedSearch> => {
  const response = await api.patch(`/search/saved/${id}`, data);
  return response.data;
};

const clearSearchHistory = async (): Promise<void> => {
  await api.delete('/search/history');
};

const saveSearchFromHistory = async (
  historyId: string,
  name: string
): Promise<SavedSearch> => {
  const response = await api.post('/search/saved/from-history', { historyId, name });
  return response.data;
};

// ============================================================================
// Sub-components
// ============================================================================

const SavedSearchItem: React.FC<{
  search: SavedSearch;
  onSelect: () => void;
  onDelete: () => void;
  onSetDefault: () => void;
  onRename: (name: string) => void;
}> = ({ search, onSelect, onDelete, onSetDefault, onRename }) => {
  const [menuOpen, setMenuOpen] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState(search.name);

  const handleRename = useCallback(() => {
    if (editName.trim() && editName !== search.name) {
      onRename(editName.trim());
    }
    setIsEditing(false);
  }, [editName, search.name, onRename]);

  const filterSummary = useCallback(() => {
    const parts: string[] = [];
    if (search.filters.query) parts.push(`"${search.filters.query}"`);
    if (search.filters.emotions?.length) parts.push(`${search.filters.emotions.length} emotions`);
    if (search.filters.tags?.length) parts.push(`${search.filters.tags.length} tags`);
    if (search.filters.startDate || search.filters.endDate) parts.push('date range');
    return parts.length > 0 ? parts.join(', ') : 'All moments';
  }, [search.filters]);

  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -10 }}
      className="group relative bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 hover:border-indigo-300 dark:hover:border-indigo-600 transition-colors"
    >
      <button
        onClick={onSelect}
        className="w-full text-left p-4 pr-12"
      >
        <div className="flex items-center gap-2">
          {search.isDefault && (
            <Star className="w-4 h-4 text-yellow-500 fill-yellow-500" />
          )}
          {isEditing ? (
            <input
              type="text"
              value={editName}
              onChange={(e) => setEditName(e.target.value)}
              onBlur={handleRename}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleRename();
                if (e.key === 'Escape') {
                  setEditName(search.name);
                  setIsEditing(false);
                }
              }}
              onClick={(e) => e.stopPropagation()}
              className="flex-1 px-2 py-1 text-sm font-medium border border-indigo-500 rounded bg-white dark:bg-gray-700"
              autoFocus
            />
          ) : (
            <span className="font-medium text-gray-900 dark:text-white">
              {search.name}
            </span>
          )}
        </div>
        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1 truncate">
          {filterSummary()}
        </p>
        <div className="flex items-center gap-4 mt-2 text-xs text-gray-400">
          <span>Used {search.useCount} times</span>
          <span>Last used {formatRelativeTime(search.lastUsedAt)}</span>
        </div>
      </button>

      {/* Menu button */}
      <div className="absolute right-2 top-2">
        <button
          onClick={(e) => {
            e.stopPropagation();
            setMenuOpen(!menuOpen);
          }}
          className="p-2 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700"
        >
          <MoreVertical className="w-4 h-4" />
        </button>

        <AnimatePresence>
          {menuOpen && (
            <motion.div
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95 }}
              className="absolute right-0 mt-1 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 z-10"
            >
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setIsEditing(true);
                  setMenuOpen(false);
                }}
                className="w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
              >
                <Edit2 className="w-4 h-4" />
                Rename
              </button>
              {!search.isDefault && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    onSetDefault();
                    setMenuOpen(false);
                  }}
                  className="w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
                >
                  <Star className="w-4 h-4" />
                  Set as default
                </button>
              )}
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  onDelete();
                  setMenuOpen(false);
                }}
                className="w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center gap-2"
              >
                <Trash2 className="w-4 h-4" />
                Delete
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </motion.div>
  );
};

const HistoryItem: React.FC<{
  history: SearchHistory;
  onSelect: () => void;
  onSave: (name: string) => void;
}> = ({ history, onSelect, onSave }) => {
  const [showSaveInput, setShowSaveInput] = useState(false);
  const [saveName, setSaveName] = useState('');

  const handleSave = useCallback(() => {
    if (saveName.trim()) {
      onSave(saveName.trim());
      setSaveName('');
      setShowSaveInput(false);
    }
  }, [saveName, onSave]);

  return (
    <div className="group flex items-center gap-3 py-2 px-3 hover:bg-gray-50 dark:hover:bg-gray-800 rounded-lg transition-colors">
      <Clock className="w-4 h-4 text-gray-400 flex-shrink-0" />
      <button
        onClick={onSelect}
        className="flex-1 text-left min-w-0"
      >
        <span className="text-sm text-gray-700 dark:text-gray-300 truncate block">
          {history.query || 'Filter search'}
        </span>
        <span className="text-xs text-gray-400">
          {history.resultCount} results • {formatRelativeTime(history.searchedAt)}
        </span>
      </button>

      {showSaveInput ? (
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={saveName}
            onChange={(e) => setSaveName(e.target.value)}
            placeholder="Name..."
            className="w-24 px-2 py-1 text-xs border border-gray-300 dark:border-gray-600 rounded"
            autoFocus
          />
          <button
            onClick={handleSave}
            className="p-1 text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20 rounded"
          >
            <Check className="w-4 h-4" />
          </button>
          <button
            onClick={() => {
              setShowSaveInput(false);
              setSaveName('');
            }}
            className="p-1 text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      ) : (
        <button
          onClick={() => setShowSaveInput(true)}
          className="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 dark:hover:bg-indigo-900/20 rounded transition-opacity"
          title="Save this search"
        >
          <BookmarkPlus className="w-4 h-4" />
        </button>
      )}
    </div>
  );
};

// ============================================================================
// Helper Functions
// ============================================================================

function formatRelativeTime(date: Date): string {
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes}m ago`;
  if (hours < 24) return `${hours}h ago`;
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString();
}

// ============================================================================
// Main Component
// ============================================================================

export const SavedSearches: React.FC<SavedSearchesProps> = ({
  onSelectSearch,
  currentFilters,
  className = '',
}) => {
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<'saved' | 'history'>('saved');

  // Fetch saved searches
  const { data: savedSearches = [], isLoading: loadingSaved } = useQuery({
    queryKey: ['savedSearches'],
    queryFn: fetchSavedSearches,
  });

  // Fetch search history
  const { data: searchHistory = [], isLoading: loadingHistory } = useQuery({
    queryKey: ['searchHistory'],
    queryFn: () => fetchSearchHistory(20),
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: deleteSavedSearch,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['savedSearches'] });
    },
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<SavedSearch> }) =>
      updateSavedSearch(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['savedSearches'] });
    },
  });

  // Save from history mutation
  const saveFromHistoryMutation = useMutation({
    mutationFn: ({ historyId, name }: { historyId: string; name: string }) =>
      saveSearchFromHistory(historyId, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['savedSearches'] });
    },
  });

  // Clear history mutation
  const clearHistoryMutation = useMutation({
    mutationFn: clearSearchHistory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['searchHistory'] });
    },
  });

  return (
    <div
      className={`bg-white dark:bg-gray-900 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 ${className}`}
    >
      {/* Tabs */}
      <div className="flex border-b border-gray-200 dark:border-gray-700">
        <button
          onClick={() => setActiveTab('saved')}
          className={`flex-1 py-3 px-4 text-sm font-medium flex items-center justify-center gap-2 transition-colors ${
            activeTab === 'saved'
              ? 'text-indigo-600 dark:text-indigo-400 border-b-2 border-indigo-600 dark:border-indigo-400'
              : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
          }`}
        >
          <Star className="w-4 h-4" />
          Saved ({savedSearches.length})
        </button>
        <button
          onClick={() => setActiveTab('history')}
          className={`flex-1 py-3 px-4 text-sm font-medium flex items-center justify-center gap-2 transition-colors ${
            activeTab === 'history'
              ? 'text-indigo-600 dark:text-indigo-400 border-b-2 border-indigo-600 dark:border-indigo-400'
              : 'text-gray-500 hover:text-gray-700 dark:hover:text-gray-300'
          }`}
        >
          <History className="w-4 h-4" />
          History
        </button>
      </div>

      {/* Content */}
      <div className="p-4 max-h-[500px] overflow-y-auto">
        <AnimatePresence mode="wait">
          {activeTab === 'saved' ? (
            <motion.div
              key="saved"
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 10 }}
              className="space-y-3"
            >
              {loadingSaved ? (
                <div className="flex items-center justify-center py-8">
                  <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600" />
                </div>
              ) : savedSearches.length === 0 ? (
                <div className="text-center py-8">
                  <Search className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto mb-3" />
                  <p className="text-gray-500 dark:text-gray-400">No saved searches yet</p>
                  <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">
                    Save your frequently used searches for quick access
                  </p>
                </div>
              ) : (
                savedSearches.map((search) => (
                  <SavedSearchItem
                    key={search.id}
                    search={search}
                    onSelect={() => onSelectSearch(search.filters)}
                    onDelete={() => deleteMutation.mutate(search.id)}
                    onSetDefault={() =>
                      updateMutation.mutate({ id: search.id, data: { isDefault: true } })
                    }
                    onRename={(name) =>
                      updateMutation.mutate({ id: search.id, data: { name } })
                    }
                  />
                ))
              )}
            </motion.div>
          ) : (
            <motion.div
              key="history"
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
            >
              {/* Clear history button */}
              {searchHistory.length > 0 && (
                <div className="flex justify-end mb-2">
                  <button
                    onClick={() => clearHistoryMutation.mutate()}
                    disabled={clearHistoryMutation.isPending}
                    className="text-xs text-gray-500 hover:text-red-600 flex items-center gap-1"
                  >
                    <Trash2 className="w-3 h-3" />
                    Clear history
                  </button>
                </div>
              )}

              {loadingHistory ? (
                <div className="flex items-center justify-center py-8">
                  <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-indigo-600" />
                </div>
              ) : searchHistory.length === 0 ? (
                <div className="text-center py-8">
                  <Clock className="w-12 h-12 text-gray-300 dark:text-gray-600 mx-auto mb-3" />
                  <p className="text-gray-500 dark:text-gray-400">No search history</p>
                  <p className="text-sm text-gray-400 dark:text-gray-500 mt-1">
                    Your recent searches will appear here
                  </p>
                </div>
              ) : (
                <div className="space-y-1">
                  {searchHistory.map((history) => (
                    <HistoryItem
                      key={history.id}
                      history={history}
                      onSelect={() => onSelectSearch(history.filters)}
                      onSave={(name) =>
                        saveFromHistoryMutation.mutate({ historyId: history.id, name })
                      }
                    />
                  ))}
                </div>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default SavedSearches;
