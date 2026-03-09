/**
 * ExpansionResultCard - Display memory expansion results with explainability
 * Phase 1 Week 12 (Day 59): Audit + Explainability Overlays
 */

import { useState } from 'react';
import { useExpansionById } from '../../hooks/use-api';
import {
  Sparkles,
  TrendingUp,
  Clock,
  AlertCircle,
  ChevronDown,
  ChevronUp,
  Lightbulb,
  Link2,
  Activity,
  FileText,
  Loader2,
  Info,
  Eye,
} from 'lucide-react';
import { format } from 'date-fns';

export interface ExpansionResultCardProps {
  expansionId: string;
  onClose?: () => void;
}

export default function ExpansionResultCard({ expansionId, onClose }: ExpansionResultCardProps) {
  const { data: expansion, isLoading, error } = useExpansionById(expansionId);
  const [showReasoning, setShowReasoning] = useState(false);
  const [expandedThemeId, setExpandedThemeId] = useState<string | null>(null);
  const [expandedPatternId, setExpandedPatternId] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="card">
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-purple-500" />
        </div>
      </div>
    );
  }

  if (error || !expansion) {
    return (
      <div className="card">
        <div className="flex items-center gap-3 text-red-600">
          <AlertCircle className="h-5 w-5" />
          <span>Failed to load expansion result</span>
        </div>
      </div>
    );
  }

  const getTypeIcon = () => {
    switch (expansion.type) {
      case 'summarize':
        return <FileText className="h-5 w-5" />;
      case 'extract_themes':
        return <TrendingUp className="h-5 w-5" />;
      case 'identify_patterns':
        return <Activity className="h-5 w-5" />;
      case 'find_connections':
        return <Link2 className="h-5 w-5" />;
      default:
        return <Sparkles className="h-5 w-5" />;
    }
  };

  const getTypeLabel = () => {
    switch (expansion.type) {
      case 'summarize':
        return 'Summary';
      case 'extract_themes':
        return 'Themes';
      case 'identify_patterns':
        return 'Patterns';
      case 'find_connections':
        return 'Connections';
      default:
        return 'Expansion';
    }
  };

  const getSentimentColor = (sentiment: string) => {
    switch (sentiment) {
      case 'positive':
        return 'bg-green-100 text-green-700';
      case 'negative':
        return 'bg-red-100 text-red-700';
      case 'mixed':
        return 'bg-yellow-100 text-yellow-700';
      default:
        return 'bg-gray-100 text-gray-700';
    }
  };

  const getConfidenceColor = (confidence: number) => {
    if (confidence >= 0.8) return 'text-green-600';
    if (confidence >= 0.6) return 'text-yellow-600';
    return 'text-gray-600';
  };

  return (
    <div className="card">
      {/* Header */}
      <div className="mb-6 flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-purple-100 p-3 text-purple-600">{getTypeIcon()}</div>
          <div>
            <h3 className="text-lg font-semibold text-gray-900">{getTypeLabel()} Analysis</h3>
            <div className="mt-1 flex items-center gap-4 text-sm text-gray-500">
              <span className="flex items-center gap-1">
                <Clock className="h-4 w-4" />
                {format(new Date(expansion.createdAt), 'MMM d, yyyy')}
              </span>
              <span>{expansion.metadata.momentsAnalyzed} moments analyzed</span>
              <span>{expansion.metadata.processingTimeMs}ms</span>
            </div>
          </div>
        </div>
        {onClose && (
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
            ×
          </button>
        )}
      </div>

      {/* AI Transparency Banner */}
      <div className="mb-6 rounded-lg border-2 border-purple-200 bg-purple-50 p-4">
        <div className="flex items-start gap-3">
          <Sparkles className="h-5 w-5 flex-shrink-0 text-purple-600" />
          <div className="flex-1">
            <div className="flex items-center justify-between">
              <div>
                <p className="font-medium text-purple-900">AI-Generated Insights</p>
                <p className="mt-1 text-sm text-purple-700">
                  Model: {expansion.metadata.model} • Confidence:{' '}
                  <span className={getConfidenceColor(expansion.metadata.confidence)}>
                    {Math.round(expansion.metadata.confidence * 100)}%
                  </span>
                </p>
              </div>
              <button
                onClick={() => setShowReasoning(!showReasoning)}
                className="flex items-center gap-1 text-sm text-purple-700 hover:text-purple-900"
              >
                <Eye className="h-4 w-4" />
                {showReasoning ? 'Hide' : 'Show'} Reasoning
              </button>
            </div>
            {showReasoning && expansion.metadata.reasoning.length > 0 && (
              <div className="mt-3 space-y-2 border-t border-purple-200 pt-3">
                {expansion.metadata.reasoning.map((reason, idx) => (
                  <div key={idx} className="flex items-start gap-2 text-sm text-purple-800">
                    <Info className="mt-0.5 h-4 w-4 flex-shrink-0" />
                    <span>{reason}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Summary Results */}
      {expansion.summary && (
        <div className="space-y-4">
          <div>
            <h4 className="mb-2 font-medium text-gray-900">Overview</h4>
            <p className="text-gray-700">{expansion.summary.overview}</p>
          </div>

          {expansion.summary.keyPoints.length > 0 && (
            <div>
              <h4 className="mb-2 font-medium text-gray-900">Key Points</h4>
              <ul className="space-y-2">
                {expansion.summary.keyPoints.map((point, idx) => (
                  <li key={idx} className="flex items-start gap-2 text-gray-700">
                    <span className="mt-1 flex h-5 w-5 flex-shrink-0 items-center justify-center rounded-full bg-purple-100 text-xs font-medium text-purple-700">
                      {idx + 1}
                    </span>
                    <span>{point}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {expansion.summary.emotionalTone && (
            <div>
              <h4 className="mb-2 font-medium text-gray-900">Emotional Tone</h4>
              <p className="rounded-lg bg-gray-50 p-3 text-gray-700">
                {expansion.summary.emotionalTone}
              </p>
            </div>
          )}
        </div>
      )}

      {/* Themes Results */}
      {expansion.themes && expansion.themes.length > 0 && (
        <div className="space-y-3">
          <h4 className="font-medium text-gray-900">Identified Themes ({expansion.themes.length})</h4>
          {expansion.themes.map((theme, idx) => (
            <div key={idx} className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="flex items-start justify-between">
                <div className="flex-1">
                  <div className="mb-2 flex items-center gap-2">
                    <h5 className="font-medium text-gray-900">{theme.name}</h5>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${getSentimentColor(
                        theme.sentiment
                      )}`}
                    >
                      {theme.sentiment}
                    </span>
                    <span className="text-xs text-gray-500">
                      {theme.occurrences} occurrence{theme.occurrences !== 1 ? 's' : ''}
                    </span>
                  </div>
                  <p className="text-sm text-gray-600">{theme.description}</p>

                  {/* Explainability: Show confidence and source moments */}
                  <button
                    onClick={() =>
                      setExpandedThemeId(expandedThemeId === `theme-${idx}` ? null : `theme-${idx}`)
                    }
                    className="mt-2 flex items-center gap-1 text-xs text-purple-600 hover:text-purple-800"
                  >
                    {expandedThemeId === `theme-${idx}` ? (
                      <ChevronUp className="h-3 w-3" />
                    ) : (
                      <ChevronDown className="h-3 w-3" />
                    )}
                    View details (confidence: {Math.round(theme.confidence * 100)}%)
                  </button>

                  {expandedThemeId === `theme-${idx}` && (
                    <div className="mt-3 rounded-lg bg-gray-50 p-3">
                      <p className="mb-2 text-xs font-medium text-gray-700">
                        Based on {theme.sourceMomentIds.length} moment
                        {theme.sourceMomentIds.length !== 1 ? 's' : ''}
                      </p>
                      <div className="flex flex-wrap gap-1">
                        {theme.sourceMomentIds.slice(0, 5).map((momentId) => (
                          <span
                            key={momentId}
                            className="rounded bg-gray-200 px-2 py-1 text-xs text-gray-600"
                            title={momentId}
                          >
                            #{momentId.slice(0, 8)}
                          </span>
                        ))}
                        {theme.sourceMomentIds.length > 5 && (
                          <span className="rounded bg-gray-200 px-2 py-1 text-xs text-gray-600">
                            +{theme.sourceMomentIds.length - 5} more
                          </span>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Patterns Results */}
      {expansion.patterns && expansion.patterns.length > 0 && (
        <div className="space-y-3">
          <h4 className="font-medium text-gray-900">
            Identified Patterns ({expansion.patterns.length})
          </h4>
          {expansion.patterns.map((pattern, idx) => (
            <div key={idx} className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="mb-2 flex items-center gap-2">
                <Activity className="h-4 w-4 text-purple-600" />
                <h5 className="font-medium text-gray-900">{pattern.name}</h5>
                <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700">
                  {pattern.frequency}
                </span>
              </div>
              <p className="mb-2 text-sm text-gray-600">{pattern.description}</p>

              {/* Explainability: Show examples */}
              <button
                onClick={() =>
                  setExpandedPatternId(
                    expandedPatternId === `pattern-${idx}` ? null : `pattern-${idx}`
                  )
                }
                className="flex items-center gap-1 text-xs text-purple-600 hover:text-purple-800"
              >
                {expandedPatternId === `pattern-${idx}` ? (
                  <ChevronUp className="h-3 w-3" />
                ) : (
                  <ChevronDown className="h-3 w-3" />
                )}
                View examples (significance: {Math.round(pattern.significance * 100)}%)
              </button>

              {expandedPatternId === `pattern-${idx}` && pattern.examples.length > 0 && (
                <div className="mt-3 space-y-2">
                  {pattern.examples.map((example, exIdx) => (
                    <div key={exIdx} className="rounded-lg bg-gray-50 p-3">
                      <div className="mb-1 flex items-center justify-between text-xs text-gray-500">
                        <span>#{example.momentId.slice(0, 8)}</span>
                        <span>{format(new Date(example.timestamp), 'MMM d, yyyy')}</span>
                      </div>
                      <p className="text-sm italic text-gray-700">"{example.excerpt}"</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Connections Results */}
      {expansion.connections && expansion.connections.length > 0 && (
        <div className="space-y-3">
          <h4 className="font-medium text-gray-900">
            Found Connections ({expansion.connections.length})
          </h4>
          {expansion.connections.map((connection, idx) => (
            <div key={idx} className="rounded-lg border border-gray-200 bg-white p-4">
              <div className="mb-2 flex items-center gap-2">
                <Link2 className="h-4 w-4 text-purple-600" />
                <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-700">
                  {connection.connectionType}
                </span>
                <span className="text-xs text-gray-500">
                  Strength: {Math.round(connection.strength * 100)}%
                </span>
              </div>
              <p className="mb-2 text-sm text-gray-700">{connection.description}</p>
              <div className="flex flex-wrap gap-1">
                {connection.momentIds.map((momentId) => (
                  <span
                    key={momentId}
                    className="rounded bg-purple-100 px-2 py-1 text-xs text-purple-700"
                    title={momentId}
                  >
                    #{momentId.slice(0, 8)}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Action Suggestions */}
      <div className="mt-6 rounded-lg border-2 border-dashed border-gray-200 bg-gray-50 p-4">
        <div className="flex items-start gap-3">
          <Lightbulb className="h-5 w-5 flex-shrink-0 text-yellow-600" />
          <div>
            <p className="font-medium text-gray-900">What's Next?</p>
            <p className="mt-1 text-sm text-gray-600">
              You can create links between related moments, add tags to organize themes, or request
              another expansion to explore different aspects of your memories.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
