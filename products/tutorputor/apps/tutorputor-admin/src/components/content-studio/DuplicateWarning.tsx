/**
 * Duplicate Content Warning Component
 *
 * Displays warnings when similar content exists during content generation.
 * Allows users to review similar content before proceeding.
 *
 * @doc.type component
 * @doc.purpose Warn users about duplicate/similar content during generation
 * @doc.layer product
 * @doc.pattern Alert
 */
import { useState } from 'react';
import { AlertTriangle, X, ExternalLink, ChevronDown, ChevronUp, FileText } from 'lucide-react';
import { Button } from '@ghatana/design-system';

export interface SimilarContentMatch {
  assetId: string;
  assetTitle: string;
  assetType: string;
  similarityScore: number; // 0-1
  matchReason: string;
  chunkText: string;
}

export interface DuplicateWarningProps {
  matches: SimilarContentMatch[];
  highestSimilarity: number;
  recommendedAction: 'proceed' | 'review' | 'block';
  onDismiss: () => void;
  onProceed: () => void;
  onViewMatch: (assetId: string) => void;
}

export function DuplicateWarning({
  matches,
  highestSimilarity,
  recommendedAction,
  onDismiss,
  onProceed,
  onViewMatch,
}: DuplicateWarningProps) {
  const [expandedMatchId, setExpandedMatchId] = useState<string | null>(null);
  const [isExpanded, setIsExpanded] = useState(true);

  // Format similarity as percentage
  const formatSimilarity = (score: number): string => {
    return `${Math.round(score * 100)}%`;
  };

  // Get severity color based on similarity
  const getSeverityColor = (similarity: number): string => {
    if (similarity >= 0.95) return 'red';
    if (similarity >= 0.85) return 'orange';
    return 'yellow';
  };

  // Get severity label
  const getSeverityLabel = (similarity: number): string => {
    if (similarity >= 0.95) return 'Very Similar';
    if (similarity >= 0.85) return 'Highly Similar';
    return 'Similar';
  };

  // Get action button configuration
  const getActionConfig = () => {
    switch (recommendedAction) {
      case 'block':
        return {
          primaryText: 'Cannot Proceed',
          primaryDisabled: true,
          primaryVariant: 'destructive' as const,
          secondaryText: 'Go Back',
          message: 'This content appears to be a duplicate of existing material.',
        };
      case 'review':
        return {
          primaryText: 'Proceed Anyway',
          primaryDisabled: false,
          primaryVariant: 'default' as const,
          secondaryText: 'Review Matches',
          message: 'Similar content exists. Please review before proceeding.',
        };
      default:
        return {
          primaryText: 'Continue',
          primaryDisabled: false,
          primaryVariant: 'default' as const,
          secondaryText: 'Dismiss',
          message: 'Some similar content was found but is different enough to proceed.',
        };
    }
  };

  const actionConfig = getActionConfig();
  const severityColor = getSeverityColor(highestSimilarity);

  // Color mapping
  const colorClasses: Record<string, { bg: string; border: string; text: string }> = {
    red: { bg: 'bg-red-50', border: 'border-red-200', text: 'text-red-800' },
    orange: { bg: 'bg-orange-50', border: 'border-orange-200', text: 'text-orange-800' },
    yellow: { bg: 'bg-yellow-50', border: 'border-yellow-200', text: 'text-yellow-800' },
  };

  const colors = colorClasses[severityColor];

  return (
    <div className={`rounded-lg border ${colors.border} ${colors.bg} p-4`}>
      {/* Header */}
      <div className="flex items-start gap-3">
        <AlertTriangle className={`h-5 w-5 ${colors.text} mt-0.5 flex-shrink-0`} />
        <div className="flex-1">
          <h3 className={`font-semibold ${colors.text}`}>
            {matches.length} Similar {matches.length === 1 ? 'Item' : 'Items'} Found
          </h3>
          <p className={`text-sm ${colors.text} mt-1`}>
            {actionConfig.message} Highest similarity: {formatSimilarity(highestSimilarity)}
          </p>
        </div>
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className={`p-1 hover:bg-white/50 rounded ${colors.text}`}
          aria-label={isExpanded ? 'Collapse' : 'Expand'}
        >
          {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </button>
        <button
          onClick={onDismiss}
          className={`p-1 hover:bg-white/50 rounded ${colors.text}`}
          aria-label="Dismiss warning"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {/* Expandable content matches */}
      {isExpanded && (
        <div className="mt-4 space-y-3">
          {matches.map((match) => (
            <div
              key={match.assetId}
              className="bg-white rounded border border-gray-200 overflow-hidden"
            >
              {/* Match header */}
              <div
                className="p-3 flex items-center justify-between cursor-pointer hover:bg-gray-50"
                onClick={() => setExpandedMatchId(expandedMatchId === match.assetId ? null : match.assetId)}
              >
                <div className="flex items-center gap-3">
                  <FileText className="h-4 w-4 text-gray-400" />
                  <div>
                    <p className="font-medium text-gray-900">{match.assetTitle}</p>
                    <p className="text-sm text-gray-500">
                      {match.assetType} • {match.matchReason}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span
                    className={`px-2 py-1 rounded text-sm font-medium ${
                      match.similarityScore >= 0.95
                        ? 'bg-red-100 text-red-700'
                        : match.similarityScore >= 0.85
                          ? 'bg-orange-100 text-orange-700'
                          : 'bg-yellow-100 text-yellow-700'
                    }`}
                  >
                    {formatSimilarity(match.similarityScore)} {getSeverityLabel(match.similarityScore)}
                  </span>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={(e) => {
                      e.stopPropagation();
                      onViewMatch(match.assetId);
                    }}
                  >
                    <ExternalLink className="h-4 w-4" />
                  </Button>
                </div>
              </div>

              {/* Expanded match details */}
              {expandedMatchId === match.assetId && (
                <div className="px-3 pb-3 border-t border-gray-100">
                  <p className="text-sm text-gray-600 mt-2">Similar content excerpt:</p>
                  <blockquote className="mt-1 p-2 bg-gray-50 rounded text-sm text-gray-700 italic">
                    "{match.chunkText}"
                  </blockquote>
                </div>
              )}
            </div>
          ))}

          {/* Action buttons */}
          <div className="flex gap-3 pt-2">
            <Button
              variant={actionConfig.primaryVariant}
              onClick={onProceed}
              disabled={actionConfig.primaryDisabled}
              className="flex-1"
            >
              {actionConfig.primaryText}
            </Button>
            {recommendedAction !== 'block' && (
              <Button variant="outline" onClick={onDismiss}>
                {actionConfig.secondaryText}
              </Button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
