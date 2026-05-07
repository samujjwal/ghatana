/**
 * Pattern Overlay Component
 *
 * Overlays historical patterns on current data visualization.
 * Part of Journey 2: The Investigation (Memory & Pattern)
 *
 * @doc.type component
 * @doc.purpose Overlay pattern recognition on data
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { TrendingUp, AlertCircle, CheckCircle, Info } from 'lucide-react';
import BaseCard from '../cards/BaseCard';

interface PatternMatch {
  patternId: string;
  patternName: string;
  confidence: number;
  description: string;
  category: string;
  matchedAt: string;
  historicalOccurrences: number;
  lastOccurrence?: string;
  metadata: Record<string, any>;
}

interface PatternOverlayProps {
  dataContext: string;
  onPatternSelect?: (pattern: PatternMatch) => void;
}

const fetchPatternMatches = async (context: string): Promise<PatternMatch[]> => {
  const response = await fetch(`/api/patterns/match?context=${encodeURIComponent(context)}`);
  if (!response.ok) {
    throw new Error('Failed to fetch pattern matches');
  }
  return response.json();
};

export function PatternOverlay({ dataContext, onPatternSelect }: PatternOverlayProps) {
  const [selectedPattern, setSelectedPattern] = useState<PatternMatch | null>(null);

  const { data: patterns, isLoading, error } = useQuery({
    queryKey: ['pattern-matches', dataContext],
    queryFn: () => fetchPatternMatches(dataContext),
    enabled: !!dataContext,
  });

  const getConfidenceColor = (confidence: number): string => {
    if (confidence >= 0.8) return 'text-green-600';
    if (confidence >= 0.6) return 'text-yellow-600';
    return 'text-gray-600';
  };

  const getConfidenceBadge = (confidence: number): string => {
    if (confidence >= 0.8) return 'bg-green-100 text-green-800 border-green-300';
    if (confidence >= 0.6) return 'bg-yellow-100 text-yellow-800 border-yellow-300';
    return 'bg-gray-100 text-gray-800 border-gray-300';
  };

  if (isLoading) {
    return (
      <BaseCard>
        <div className="animate-pulse space-y-3">
          <div className="h-6 bg-gray-200 rounded w-1/3"></div>
          <div className="h-20 bg-gray-200 rounded"></div>
          <div className="h-20 bg-gray-200 rounded"></div>
        </div>
      </BaseCard>
    );
  }

  if (error) {
    return (
      <BaseCard>
        <div className="text-red-600 flex items-center gap-2">
          <AlertCircle className="h-5 w-5" />
          <p>Failed to load pattern matches</p>
        </div>
      </BaseCard>
    );
  }

  return (
    <BaseCard>
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <TrendingUp className="h-5 w-5 text-purple-600" />
          <h3 className="text-lg font-semibold text-gray-900">Pattern Recognition</h3>
        </div>
        {patterns && patterns.length > 0 && (
          <span className="text-sm text-gray-500">{patterns.length} patterns detected</span>
        )}
      </div>

      {!patterns || patterns.length === 0 ? (
        <div className="text-center py-8 text-gray-500">
          <Info className="h-8 w-8 mx-auto mb-2 text-gray-400" />
          <p>No patterns detected for current context</p>
          <p className="text-sm mt-1">Patterns will appear as the system learns</p>
        </div>
      ) : (
        <div className="space-y-3">
          {patterns.map((pattern) => (
            <div
              key={pattern.patternId}
              onClick={() => {
                setSelectedPattern(pattern);
                onPatternSelect?.(pattern);
              }}
              className="p-4 border border-gray-200 rounded-lg hover:border-purple-300 hover:shadow-md transition-all cursor-pointer"
            >
              <div className="flex items-start justify-between mb-2">
                <div className="flex items-center gap-2">
                  <CheckCircle className="h-5 w-5 text-purple-600" />
                  <h4 className="font-semibold text-gray-900">{pattern.patternName}</h4>
                </div>
                <span
                  className={`px-2 py-1 text-xs font-semibold rounded-full border ${getConfidenceBadge(
                    pattern.confidence
                  )}`}
                >
                  {(pattern.confidence * 100).toFixed(0)}% match
                </span>
              </div>

              <p className="text-sm text-gray-700 mb-3">{pattern.description}</p>

              <div className="flex items-center gap-4 text-xs text-gray-600">
                <span className="flex items-center gap-1">
                  <span className="font-medium">Category:</span>
                  <span className="px-2 py-0.5 bg-purple-100 text-purple-700 rounded">
                    {pattern.category}
                  </span>
                </span>
                <span>
                  Seen <strong>{pattern.historicalOccurrences}x</strong> before
                </span>
                {pattern.lastOccurrence && (
                  <span>
                    Last: {new Date(pattern.lastOccurrence).toLocaleDateString()}
                  </span>
                )}
              </div>

              {/* Confidence Bar */}
              <div className="mt-3">
                <div className="flex items-center justify-between text-xs mb-1">
                  <span className="text-gray-600">Confidence Level</span>
                  <span className={`font-semibold ${getConfidenceColor(pattern.confidence)}`}>
                    {(pattern.confidence * 100).toFixed(1)}%
                  </span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2">
                  <div
                    className={`h-2 rounded-full transition-all ${
                      pattern.confidence >= 0.8
                        ? 'bg-green-500'
                        : pattern.confidence >= 0.6
                        ? 'bg-yellow-500'
                        : 'bg-gray-400'
                    }`}
                    style={{ width: `${pattern.confidence * 100}%` }}
                  ></div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Pattern Detail Modal */}
      {selectedPattern && (
        <div
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          onClick={() => setSelectedPattern(null)}
        >
          <div
            className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 p-6"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                <TrendingUp className="h-6 w-6 text-purple-600" />
                <h3 className="text-xl font-bold text-gray-900">{selectedPattern.patternName}</h3>
              </div>
              <button
                onClick={() => setSelectedPattern(null)}
                className="text-gray-400 hover:text-gray-600 text-2xl"
              >
                ×
              </button>
            </div>

            <div className="space-y-4">
              <div>
                <label className="text-sm font-medium text-gray-700">Description</label>
                <p className="text-sm text-gray-900 mt-1">{selectedPattern.description}</p>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-sm font-medium text-gray-700">Confidence</label>
                  <p className="text-2xl font-bold text-gray-900 mt-1">
                    {(selectedPattern.confidence * 100).toFixed(1)}%
                  </p>
                </div>
                <div>
                  <label className="text-sm font-medium text-gray-700">Historical Occurrences</label>
                  <p className="text-2xl font-bold text-gray-900 mt-1">
                    {selectedPattern.historicalOccurrences}
                  </p>
                </div>
              </div>

              <div>
                <label className="text-sm font-medium text-gray-700">Category</label>
                <p className="text-sm text-gray-900 mt-1">
                  <span className="px-3 py-1 bg-purple-100 text-purple-700 rounded-full">
                    {selectedPattern.category}
                  </span>
                </p>
              </div>

              {selectedPattern.lastOccurrence && (
                <div>
                  <label className="text-sm font-medium text-gray-700">Last Occurrence</label>
                  <p className="text-sm text-gray-900 mt-1">
                    {new Date(selectedPattern.lastOccurrence).toLocaleString()}
                  </p>
                </div>
              )}

              {selectedPattern.metadata && Object.keys(selectedPattern.metadata).length > 0 && (
                <div>
                  <label className="text-sm font-medium text-gray-700">Additional Details</label>
                  <div className="mt-1 bg-gray-50 rounded p-3 text-xs font-mono max-h-40 overflow-auto">
                    <pre>{JSON.stringify(selectedPattern.metadata, null, 2)}</pre>
                  </div>
                </div>
              )}
            </div>

            <div className="mt-6 flex justify-end gap-2">
              <button
                onClick={() => setSelectedPattern(null)}
                className="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
              >
                Close
              </button>
              <button className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700">
                View Historical Data
              </button>
            </div>
          </div>
        </div>
      )}
    </BaseCard>
  );
}

export default PatternOverlay;

