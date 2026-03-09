/**
 * LanguageEvolutionPanel Component
 * Displays comprehensive language evolution insights
 * Week 13 Day 62 - Surface evolution insights in UI
 */

import React, { useState } from 'react';
import {
  useLanguageEvolution,
  useReturnToMeaningRate,
  useCrossTimeReferencing,
} from '../../hooks/use-api';
import { format } from 'date-fns';
import {
  TrendingUp,
  TrendingDown,
  Minus,
  BookOpen,
  Heart,
  MessageCircle,
  ArrowRight,
  Calendar,
  Link2,
  Sparkles,
  BarChart3,
  Loader2,
  AlertCircle,
  Info,
} from 'lucide-react';

interface TopicShift {
  from: string;
  to: string;
  shiftStrength: number;
}

interface ExpressionPattern {
  pattern: string;
  frequency: number;
  trend: 'increasing' | 'decreasing' | 'stable';
}

interface TemporalArc {
  arcId: string;
  startDate: string;
  endDate: string;
  momentCount: number;
  theme?: string;
}

interface LanguageEvolutionPanelProps {
  sphereId?: string;
  periodDays?: number;
}

export const LanguageEvolutionPanel: React.FC<LanguageEvolutionPanelProps> = ({
  sphereId,
  periodDays = 30,
}) => {
  const [selectedPeriod, setSelectedPeriod] = useState(periodDays);

  const {
    data: languageData,
    isLoading: languageLoading,
    error: languageError,
  } = useLanguageEvolution({ periodDays: selectedPeriod, sphereId });

  const {
    data: returnData,
    isLoading: returnLoading,
  } = useReturnToMeaningRate({ periodDays: selectedPeriod, sphereId });

  const {
    data: crossTimeData,
    isLoading: crossTimeLoading,
  } = useCrossTimeReferencing({ periodDays: selectedPeriod, sphereId });

  const isLoading = languageLoading || returnLoading || crossTimeLoading;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500" />
      </div>
    );
  }

  if (languageError) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-6">
        <div className="flex items-center gap-3">
          <AlertCircle className="h-5 w-5 text-red-600" />
          <div>
            <h3 className="font-semibold text-red-900">Error Loading Language Evolution</h3>
            <p className="text-sm text-red-700">
              {languageError instanceof Error ? languageError.message : 'Unknown error'}
            </p>
          </div>
        </div>
      </div>
    );
  }

  if (!languageData) {
    return (
      <div className="rounded-lg border border-gray-200 bg-gray-50 p-6">
        <div className="flex items-center gap-3">
          <Info className="h-5 w-5 text-gray-600" />
          <p className="text-gray-700">No language evolution data available.</p>
        </div>
      </div>
    );
  }

  const getTrendIcon = (trend: 'increasing' | 'decreasing' | 'stable') => {
    if (trend === 'increasing') return <TrendingUp className="h-4 w-4 text-green-600" />;
    if (trend === 'decreasing') return <TrendingDown className="h-4 w-4 text-red-600" />;
    return <Minus className="h-4 w-4 text-gray-600" />;
  };

  const getTrendColor = (trend: 'increasing' | 'decreasing' | 'stable') => {
    if (trend === 'increasing') return 'text-green-700 bg-green-50 border-green-200';
    if (trend === 'decreasing') return 'text-red-700 bg-red-50 border-red-200';
    return 'text-gray-700 bg-gray-50 border-gray-200';
  };

  return (
    <div className="space-y-6">
      {/* Header with Period Selector */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Sparkles className="h-6 w-6 text-purple-600" />
          <h2 className="text-2xl font-bold text-gray-900">Language Evolution</h2>
        </div>

        <div className="flex items-center gap-2 rounded-lg border border-gray-300 bg-white p-1">
          {[7, 30, 90, 180].map((days) => (
            <button
              key={days}
              onClick={() => setSelectedPeriod(days)}
              className={`rounded px-3 py-1.5 text-sm font-medium transition-colors ${
                selectedPeriod === days
                  ? 'bg-purple-600 text-white'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              {days}d
            </button>
          ))}
        </div>
      </div>

      {/* Info Banner */}
      <div className="rounded-lg border border-blue-200 bg-blue-50 p-4">
        <div className="flex items-start gap-3">
          <Info className="h-5 w-5 text-blue-600 mt-0.5 flex-shrink-0" />
          <div className="text-sm text-blue-900">
            <p className="font-medium mb-1">How Your Language is Evolving</p>
            <p className="text-blue-700">
              Track how your vocabulary, emotions, and expression patterns change over time.
              This helps you understand your personal growth and communication patterns.
            </p>
          </div>
        </div>
      </div>

      {/* Key Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Vocabulary Growth */}
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="rounded-full bg-blue-100 p-2">
              <BookOpen className="h-5 w-5 text-blue-600" />
            </div>
            <h3 className="font-semibold text-gray-900">Vocabulary Growth</h3>
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-blue-600">
              {languageData.vocabularyGrowth}
            </span>
            <span className="text-sm text-gray-600">new words</span>
          </div>
          <p className="mt-2 text-xs text-gray-500">
            Unique words added in last {selectedPeriod} days
          </p>
        </div>

        {/* Emotion Diversity */}
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="rounded-full bg-pink-100 p-2">
              <Heart className="h-5 w-5 text-pink-600" />
            </div>
            <h3 className="font-semibold text-gray-900">Emotion Diversity</h3>
          </div>
          <div className="flex items-baseline gap-2">
            <span className="text-3xl font-bold text-pink-600">
              {languageData.emotionDiversity}
            </span>
            <span className="text-sm text-gray-600">emotions</span>
          </div>
          <p className="mt-2 text-xs text-gray-500">
            Different emotions expressed recently
          </p>
        </div>

        {/* Return to Meaning Rate */}
        {returnData && (
          <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
            <div className="flex items-center gap-3 mb-4">
              <div className="rounded-full bg-green-100 p-2">
                <Calendar className="h-5 w-5 text-green-600" />
              </div>
              <h3 className="font-semibold text-gray-900">Revisit Rate</h3>
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-3xl font-bold text-green-600">
                {Math.round(returnData.rate)}%
              </span>
              <span className="text-sm text-gray-600">moments</span>
            </div>
            <p className="mt-2 text-xs text-gray-500">
              Revisited in last {selectedPeriod} days
            </p>
          </div>
        )}
      </div>

      {/* Topic Shifts */}
      {languageData.topicShift && languageData.topicShift.length > 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="rounded-full bg-orange-100 p-2">
              <ArrowRight className="h-5 w-5 text-orange-600" />
            </div>
            <h3 className="font-semibold text-gray-900">Topic Shifts</h3>
          </div>

          <div className="space-y-3">
            {languageData.topicShift.map((shift: TopicShift, idx: number) => (
              <div
                key={idx}
                className="flex items-center justify-between rounded-lg border border-gray-200 bg-gray-50 p-4"
              >
                <div className="flex items-center gap-3">
                  <span className="rounded bg-red-100 px-3 py-1 text-sm font-medium text-red-700">
                    {shift.from}
                  </span>
                  <ArrowRight className="h-4 w-4 text-gray-400" />
                  <span className="rounded bg-green-100 px-3 py-1 text-sm font-medium text-green-700">
                    {shift.to}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="h-2 w-32 rounded-full bg-gray-200">
                    <div
                      className="h-2 rounded-full bg-orange-500"
                      style={{ width: `${shift.shiftStrength * 100}%` }}
                    />
                  </div>
                  <span className="text-sm font-medium text-gray-700">
                    {Math.round(shift.shiftStrength * 100)}%
                  </span>
                </div>
              </div>
            ))}
          </div>

          <p className="mt-3 text-xs text-gray-500">
            Topics that decreased in usage and their potential replacements
          </p>
        </div>
      )}

      {/* Expression Patterns */}
      {languageData.expressionPatterns && languageData.expressionPatterns.length > 0 && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="rounded-full bg-purple-100 p-2">
              <MessageCircle className="h-5 w-5 text-purple-600" />
            </div>
            <h3 className="font-semibold text-gray-900">Expression Patterns</h3>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {languageData.expressionPatterns.map((pattern: ExpressionPattern, idx: number) => (
              <div
                key={idx}
                className={`rounded-lg border p-4 ${getTrendColor(pattern.trend)}`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="font-mono text-sm font-medium">
                    {pattern.pattern}
                  </span>
                  {getTrendIcon(pattern.trend)}
                </div>
                <div className="flex items-baseline gap-2">
                  <span className="text-2xl font-bold">{pattern.frequency}</span>
                  <span className="text-xs">occurrences</span>
                </div>
                <p className="mt-1 text-xs capitalize">{pattern.trend}</p>
              </div>
            ))}
          </div>

          <p className="mt-3 text-xs text-gray-500">
            Words with significant frequency changes in your recent moments
          </p>
        </div>
      )}

      {/* Cross-Time Referencing */}
      {crossTimeData && (
        <div className="rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-3 mb-4">
            <div className="rounded-full bg-indigo-100 p-2">
              <Link2 className="h-5 w-5 text-indigo-600" />
            </div>
            <h3 className="font-semibold text-gray-900">Temporal Connections</h3>
          </div>

          <div className="grid grid-cols-3 gap-4 mb-4">
            <div className="text-center">
              <p className="text-2xl font-bold text-indigo-600">
                {crossTimeData.linksAcrossMonths}
              </p>
              <p className="text-xs text-gray-600">Across months</p>
            </div>
            <div className="text-center">
              <p className="text-2xl font-bold text-blue-600">
                {crossTimeData.linksAcrossWeeks}
              </p>
              <p className="text-xs text-gray-600">Across weeks</p>
            </div>
            <div className="text-center">
              <p className="text-2xl font-bold text-gray-600">
                {crossTimeData.linksWithinWeek}
              </p>
              <p className="text-xs text-gray-600">Within week</p>
            </div>
          </div>

          {crossTimeData.temporalArcs && crossTimeData.temporalArcs.length > 0 && (
            <>
              <div className="mt-4 space-y-2">
                {crossTimeData.temporalArcs.map((arc: TemporalArc) => (
                  <div
                    key={arc.arcId}
                    className="rounded-lg border border-gray-200 bg-gray-50 p-3"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <BarChart3 className="h-4 w-4 text-indigo-600" />
                        <span className="font-medium text-gray-900">
                          {arc.theme || 'Untitled Arc'}
                        </span>
                      </div>
                      <span className="rounded bg-indigo-100 px-2 py-1 text-xs font-medium text-indigo-700">
                        {arc.momentCount} moments
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-gray-600">
                      {format(new Date(arc.startDate), 'MMM d, yyyy')} →{' '}
                      {format(new Date(arc.endDate), 'MMM d, yyyy')}
                    </p>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs text-gray-500">
                Temporal arcs connect moments across significant time spans
              </p>
            </>
          )}
        </div>
      )}

      {/* Empty State */}
      {(!languageData.topicShift || languageData.topicShift.length === 0) &&
        (!languageData.expressionPatterns || languageData.expressionPatterns.length === 0) && (
          <div className="rounded-lg border border-gray-200 bg-gray-50 p-8 text-center">
            <Sparkles className="mx-auto h-12 w-12 text-gray-400 mb-3" />
            <h3 className="font-semibold text-gray-900 mb-2">
              Not Enough Data Yet
            </h3>
            <p className="text-sm text-gray-600 max-w-md mx-auto">
              Keep adding moments to see your language evolution patterns. We need more data
              to detect topic shifts and expression patterns.
            </p>
          </div>
        )}
    </div>
  );
};
