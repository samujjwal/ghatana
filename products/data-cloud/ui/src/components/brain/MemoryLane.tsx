/**
 * Memory Lane Component
 *
 * Displays episodic memory recall for similar past incidents.
 * Part of Journey 2: The Investigation (Memory & Pattern)
 *
 * @doc.type component
 * @doc.purpose Episodic memory recall interface
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Clock, Search, ChevronRight, BookOpen, AlertCircle } from 'lucide-react';
import BaseCard from '../cards/BaseCard';

interface MemoryRecall {
  id: string;
  timestamp: string;
  summary: string;
  similarity: number;
  context: string;
  resolution?: string;
  tags: string[];
  metadata: Record<string, any>;
}

interface MemoryLaneProps {
  query: string;
  maxResults?: number;
  onMemorySelect?: (memory: MemoryRecall) => void;
}

const fetchMemoryRecall = async (query: string, maxResults: number): Promise<MemoryRecall[]> => {
  const response = await fetch(
    `/api/memory/recall?query=${encodeURIComponent(query)}&limit=${maxResults}`
  );
  if (!response.ok) {
    throw new Error('Failed to fetch memory recall');
  }
  return response.json();
};

export function MemoryLane({ query, maxResults = 5, onMemorySelect }: MemoryLaneProps) {
  const [selectedMemory, setSelectedMemory] = useState<MemoryRecall | null>(null);
  const [isExpanded, setIsExpanded] = useState(false);

  const { data: memories, isLoading, error } = useQuery({
    queryKey: ['memory-recall', query],
    queryFn: () => fetchMemoryRecall(query, maxResults),
    enabled: !!query,
  });

  const getSimilarityColor = (similarity: number): string => {
    if (similarity >= 0.9) return 'text-green-600';
    if (similarity >= 0.7) return 'text-blue-600';
    if (similarity >= 0.5) return 'text-yellow-600';
    return 'text-gray-600';
  };

  const getSimilarityBadge = (similarity: number): string => {
    if (similarity >= 0.9) return 'bg-green-100 text-green-800 border-green-300';
    if (similarity >= 0.7) return 'bg-blue-100 text-blue-800 border-blue-300';
    if (similarity >= 0.5) return 'bg-yellow-100 text-yellow-800 border-yellow-300';
    return 'bg-gray-100 text-gray-800 border-gray-300';
  };

  const getRelativeTime = (timestamp: string): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays === 0) return 'Today';
    if (diffDays === 1) return 'Yesterday';
    if (diffDays < 7) return `${diffDays} days ago`;
    if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`;
    if (diffDays < 365) return `${Math.floor(diffDays / 30)} months ago`;
    return `${Math.floor(diffDays / 365)} years ago`;
  };

  if (!query) {
    return (
      <BaseCard>
        <div className="flex items-center gap-2 text-gray-500">
          <Search className="h-5 w-5" />
          <p>Enter a query to search memory</p>
        </div>
      </BaseCard>
    );
  }

  if (isLoading) {
    return (
      <BaseCard>
        <div className="animate-pulse space-y-3">
          <div className="flex items-center gap-2">
            <Clock className="h-5 w-5 text-gray-400" />
            <div className="h-5 bg-gray-200 rounded w-1/3"></div>
          </div>
          {[1, 2, 3].map((i) => (
            <div key={i} className="h-24 bg-gray-200 rounded"></div>
          ))}
        </div>
      </BaseCard>
    );
  }

  if (error) {
    return (
      <BaseCard>
        <div className="text-red-600 flex items-center gap-2">
          <AlertCircle className="h-5 w-5" />
          <p>Failed to load memory recall</p>
        </div>
      </BaseCard>
    );
  }

  return (
    <>
      <BaseCard>
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-2">
            <Clock className="h-5 w-5 text-indigo-600" />
            <h3 className="text-lg font-semibold text-gray-900">Memory Lane</h3>
          </div>
          {memories && memories.length > 0 && (
            <button
              onClick={() => setIsExpanded(!isExpanded)}
              className="text-sm text-indigo-600 hover:text-indigo-700 font-medium"
            >
              {isExpanded ? 'Collapse' : 'Expand All'}
            </button>
          )}
        </div>

        <div className="mb-4 p-3 bg-gray-50 rounded-lg">
          <div className="flex items-center gap-2 text-sm text-gray-700">
            <Search className="h-4 w-4" />
            <span className="font-medium">Query:</span>
            <span className="italic">{query}</span>
          </div>
        </div>

        {!memories || memories.length === 0 ? (
          <div className="text-center py-8 text-gray-500">
            <BookOpen className="h-8 w-8 mx-auto mb-2 text-gray-400" />
            <p>No similar past incidents found</p>
            <p className="text-sm mt-1">This appears to be a new situation</p>
          </div>
        ) : (
          <div className="space-y-3">
            {memories.map((memory, index) => (
              <div
                key={memory.id}
                className="border border-gray-200 rounded-lg hover:border-indigo-300 hover:shadow-md transition-all"
              >
                {/* Memory Header */}
                <div
                  onClick={() => {
                    setSelectedMemory(memory);
                    onMemorySelect?.(memory);
                  }}
                  className="p-4 cursor-pointer"
                >
                  <div className="flex items-start justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-gray-500">#{index + 1}</span>
                      <span
                        className={`px-2 py-1 text-xs font-semibold rounded-full border ${getSimilarityBadge(
                          memory.similarity
                        )}`}
                      >
                        {(memory.similarity * 100).toFixed(0)}% similar
                      </span>
                    </div>
                    <div className="flex items-center gap-2 text-sm text-gray-600">
                      <Clock className="h-4 w-4" />
                      <span>{getRelativeTime(memory.timestamp)}</span>
                    </div>
                  </div>

                  <h4 className="font-medium text-gray-900 mb-2">{memory.summary}</h4>

                  <p className="text-sm text-gray-700 line-clamp-2 mb-2">{memory.context}</p>

                  {memory.resolution && (
                    <div className="mt-2 p-2 bg-green-50 border border-green-200 rounded text-sm">
                      <span className="font-medium text-green-800">Resolution: </span>
                      <span className="text-green-700">{memory.resolution}</span>
                    </div>
                  )}

                  {memory.tags && memory.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-2">
                      {memory.tags.map((tag) => (
                        <span
                          key={tag}
                          className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-700"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}

                  <div className="flex items-center justify-between mt-3">
                    <div className="flex items-center gap-2">
                      {/* Similarity Bar */}
                      <div className="w-24 bg-gray-200 rounded-full h-1.5">
                        <div
                          className={`h-1.5 rounded-full ${
                            memory.similarity >= 0.9
                              ? 'bg-green-500'
                              : memory.similarity >= 0.7
                              ? 'bg-blue-500'
                              : memory.similarity >= 0.5
                              ? 'bg-yellow-500'
                              : 'bg-gray-400'
                          }`}
                          style={{ width: `${memory.similarity * 100}%` }}
                        ></div>
                      </div>
                    </div>
                    <ChevronRight className="h-4 w-4 text-gray-400" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {memories && memories.length > 0 && (
          <div className="mt-4 text-sm text-gray-600 text-center">
            Found {memories.length} similar incident{memories.length !== 1 ? 's' : ''} in episodic
            memory
          </div>
        )}
      </BaseCard>

      {/* Memory Detail Modal */}
      {selectedMemory && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={() => setSelectedMemory(null)}
        >
          <div
            className="bg-white rounded-lg shadow-xl max-w-3xl w-full mx-4 p-6 max-h-[90vh] overflow-y-auto"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                <Clock className="h-6 w-6 text-indigo-600" />
                <div>
                  <h3 className="text-xl font-bold text-gray-900">{selectedMemory.summary}</h3>
                  <p className="text-sm text-gray-600 mt-1">
                    {getRelativeTime(selectedMemory.timestamp)} •{' '}
                    {new Date(selectedMemory.timestamp).toLocaleString()}
                  </p>
                </div>
              </div>
              <button
                onClick={() => setSelectedMemory(null)}
                className="text-gray-400 hover:text-gray-600 text-2xl"
              >
                ×
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-gray-700">Similarity Score</label>
                <div className="flex items-center gap-3 mt-1">
                  <div className="flex-1 bg-gray-200 rounded-full h-3">
                    <div
                      className={`h-3 rounded-full ${
                        selectedMemory.similarity >= 0.9
                          ? 'bg-green-500'
                          : selectedMemory.similarity >= 0.7
                          ? 'bg-blue-500'
                          : 'bg-yellow-500'
                      }`}
                      style={{ width: `${selectedMemory.similarity * 100}%` }}
                    ></div>
                  </div>
                  <span className={`text-lg font-bold ${getSimilarityColor(selectedMemory.similarity)}`}>
                    {(selectedMemory.similarity * 100).toFixed(1)}%
                  </span>
                </div>
              </div>

              <div>
                <label className="text-sm font-medium text-gray-700">Context</label>
                <p className="text-sm text-gray-900 mt-1 whitespace-pre-wrap">{selectedMemory.context}</p>
              </div>

              {selectedMemory.resolution && (
                <div>
                  <label className="text-sm font-medium text-gray-700">Resolution</label>
                  <div className="mt-1 p-3 bg-green-50 border border-green-200 rounded">
                    <p className="text-sm text-green-900">{selectedMemory.resolution}</p>
                  </div>
                </div>
              )}

              {selectedMemory.tags && selectedMemory.tags.length > 0 && (
                <div>
                  <label className="text-sm font-medium text-gray-700">Tags</label>
                  <div className="flex flex-wrap gap-2 mt-1">
                    {selectedMemory.tags.map((tag) => (
                      <span
                        key={tag}
                        className="px-3 py-1 text-sm rounded-full bg-indigo-100 text-indigo-700"
                      >
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {selectedMemory.metadata && Object.keys(selectedMemory.metadata).length > 0 && (
                <div>
                  <label className="text-sm font-medium text-gray-700">Additional Details</label>
                  <div className="mt-1 bg-gray-50 rounded p-3 text-xs font-mono max-h-60 overflow-auto">
                    <pre>{JSON.stringify(selectedMemory.metadata, null, 2)}</pre>
                  </div>
                </div>
              )}
            </div>

            <div className="mt-6 flex justify-end gap-2">
              <button
                onClick={() => setSelectedMemory(null)}
                className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
              >
                Close
              </button>
              <button className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700">
                Apply This Solution
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default MemoryLane;

