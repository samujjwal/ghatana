/**
 * SummaryViewer Component for Flashit Web
 * Displays AI-generated summaries with charts and export options
 *
 * @doc.type component
 * @doc.purpose View and interact with generated summaries
 * @doc.layer product
 * @doc.pattern ViewerComponent
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'framer-motion';
import {
  FileText,
  Download,
  Calendar,
  TrendingUp,
  TrendingDown,
  Minus,
  Tag,
  Heart,
  Lightbulb,
  Target,
  Award,
  Star,
  Clock,
  BarChart3,
  PieChart,
  RefreshCw,
  Share2,
  ChevronLeft,
  ChevronRight,
  Loader2,
  AlertCircle,
  CheckCircle2,
  Info,
} from 'lucide-react';
import { api } from '../lib/api';

// ============================================================================
// Types
// ============================================================================

type SummaryPeriod = 'daily' | 'weekly' | 'monthly' | 'yearly' | 'custom';

interface EmotionAnalysis {
  dominantEmotion: string;
  emotionDistribution: Record<string, number>;
  emotionTrend: 'improving' | 'stable' | 'declining';
  moodScore: number;
  insights: string[];
}

interface TopicCluster {
  topic: string;
  momentCount: number;
  keywords: string[];
  summary: string;
}

interface Highlight {
  momentId: string;
  content: string;
  reason: string;
  category: 'achievement' | 'milestone' | 'insight' | 'memorable' | 'growth';
}

interface Recommendation {
  type: 'action' | 'reflection' | 'goal' | 'habit';
  title: string;
  description: string;
  priority: 'high' | 'medium' | 'low';
}

interface MomentSummary {
  id: string;
  content: string;
  capturedAt: string;
  emotions: string[];
  importance: number;
  sphereName: string;
}

interface GeneratedSummary {
  id: string;
  userId: string;
  period: SummaryPeriod;
  startDate: string;
  endDate: string;
  title: string;
  overview: string;
  momentCount: number;
  sphereBreakdown: Record<string, number>;
  emotionAnalysis?: EmotionAnalysis;
  topicClusters?: TopicCluster[];
  highlights?: Highlight[];
  recommendations?: Recommendation[];
  keyMoments: MomentSummary[];
  wordCloud?: Record<string, number>;
  generatedAt: string;
}

interface SummaryViewerProps {
  summaryId?: string;
  period?: SummaryPeriod;
  startDate?: Date;
  endDate?: Date;
  sphereIds?: string[];
  onClose?: () => void;
  className?: string;
}

// ============================================================================
// API Functions
// ============================================================================

const generateSummary = async (params: {
  period: SummaryPeriod;
  startDate?: Date;
  endDate?: Date;
  sphereIds?: string[];
}): Promise<GeneratedSummary> => {
  const response = await api.post('/summaries/generate', params);
  return response.data;
};

const getSummary = async (id: string): Promise<GeneratedSummary> => {
  const response = await api.get(`/summaries/${id}`);
  return response.data;
};

const downloadPdf = async (id: string): Promise<Blob> => {
  const response = await api.get(`/summaries/${id}/pdf`, {
    responseType: 'blob',
  });
  return response.data;
};

// ============================================================================
// Sub-components
// ============================================================================

const StatCard: React.FC<{
  label: string;
  value: string | number;
  icon: React.ReactNode;
  color?: string;
}> = ({ label, value, icon, color = 'indigo' }) => (
  <div className="bg-white dark:bg-gray-800 rounded-xl p-4 shadow-sm border border-gray-200 dark:border-gray-700">
    <div className={`w-10 h-10 rounded-lg bg-${color}-100 dark:bg-${color}-900/30 flex items-center justify-center mb-3`}>
      <span className={`text-${color}-600 dark:text-${color}-400`}>{icon}</span>
    </div>
    <p className="text-2xl font-bold text-gray-900 dark:text-white">{value}</p>
    <p className="text-sm text-gray-500 dark:text-gray-400">{label}</p>
  </div>
);

const MoodIndicator: React.FC<{ score: number; trend: EmotionAnalysis['emotionTrend'] }> = ({
  score,
  trend,
}) => {
  const trendIcon = {
    improving: <TrendingUp className="w-5 h-5 text-green-500" />,
    stable: <Minus className="w-5 h-5 text-gray-500" />,
    declining: <TrendingDown className="w-5 h-5 text-red-500" />,
  };

  const scoreColor = score >= 7 ? 'text-green-500' : score >= 4 ? 'text-yellow-500' : 'text-red-500';

  return (
    <div className="flex items-center gap-4">
      <div className="relative">
        <svg className="w-24 h-24 transform -rotate-90">
          <circle
            cx="48"
            cy="48"
            r="40"
            stroke="currentColor"
            strokeWidth="8"
            fill="none"
            className="text-gray-200 dark:text-gray-700"
          />
          <circle
            cx="48"
            cy="48"
            r="40"
            stroke="currentColor"
            strokeWidth="8"
            fill="none"
            strokeDasharray={`${score * 25.13} 251.3`}
            className={scoreColor}
            strokeLinecap="round"
          />
        </svg>
        <div className="absolute inset-0 flex items-center justify-center">
          <span className={`text-2xl font-bold ${scoreColor}`}>{score}</span>
        </div>
      </div>
      <div>
        <div className="flex items-center gap-2">
          {trendIcon[trend]}
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {trend.charAt(0).toUpperCase() + trend.slice(1)}
          </span>
        </div>
        <p className="text-xs text-gray-500 mt-1">Mood Score (1-10)</p>
      </div>
    </div>
  );
};

const EmotionDistributionChart: React.FC<{ distribution: Record<string, number> }> = ({
  distribution,
}) => {
  const emotions = Object.entries(distribution)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6);

  const colors = ['bg-indigo-500', 'bg-cyan-500', 'bg-green-500', 'bg-yellow-500', 'bg-red-500', 'bg-purple-500'];

  return (
    <div className="space-y-3">
      {emotions.map(([emotion, percentage], i) => (
        <div key={emotion} className="flex items-center gap-3">
          <span className="w-20 text-sm text-gray-600 dark:text-gray-400 truncate">{emotion}</span>
          <div className="flex-1 h-4 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
            <motion.div
              initial={{ width: 0 }}
              animate={{ width: `${percentage}%` }}
              transition={{ duration: 0.5, delay: i * 0.1 }}
              className={`h-full ${colors[i % colors.length]} rounded-full`}
            />
          </div>
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300 w-12 text-right">
            {percentage}%
          </span>
        </div>
      ))}
    </div>
  );
};

const SphereBreakdownChart: React.FC<{ breakdown: Record<string, number> }> = ({ breakdown }) => {
  const spheres = Object.entries(breakdown).sort((a, b) => b[1] - a[1]);
  const total = spheres.reduce((sum, [, count]) => sum + count, 0);

  const colors = [
    'bg-indigo-500',
    'bg-cyan-500',
    'bg-green-500',
    'bg-yellow-500',
    'bg-red-500',
    'bg-purple-500',
    'bg-pink-500',
    'bg-orange-500',
  ];

  return (
    <div className="space-y-4">
      {/* Stacked bar */}
      <div className="h-8 rounded-full overflow-hidden flex">
        {spheres.map(([sphere, count], i) => (
          <motion.div
            key={sphere}
            initial={{ width: 0 }}
            animate={{ width: `${(count / total) * 100}%` }}
            transition={{ duration: 0.5, delay: i * 0.1 }}
            className={`${colors[i % colors.length]}`}
            title={`${sphere}: ${count}`}
          />
        ))}
      </div>

      {/* Legend */}
      <div className="flex flex-wrap gap-3">
        {spheres.map(([sphere, count], i) => (
          <div key={sphere} className="flex items-center gap-2">
            <div className={`w-3 h-3 rounded-full ${colors[i % colors.length]}`} />
            <span className="text-sm text-gray-600 dark:text-gray-400">
              {sphere} ({count})
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

const TopicCard: React.FC<{ topic: TopicCluster; index: number }> = ({ topic, index }) => {
  const colors = ['bg-indigo-50 dark:bg-indigo-900/20', 'bg-cyan-50 dark:bg-cyan-900/20', 'bg-green-50 dark:bg-green-900/20'];

  return (
    <div className={`${colors[index % colors.length]} rounded-lg p-4`}>
      <div className="flex items-center justify-between mb-2">
        <h4 className="font-semibold text-gray-900 dark:text-white">{topic.topic}</h4>
        <span className="text-xs bg-white dark:bg-gray-800 px-2 py-1 rounded-full text-gray-600 dark:text-gray-400">
          {topic.momentCount} moments
        </span>
      </div>
      <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">{topic.summary}</p>
      <div className="flex flex-wrap gap-1">
        {topic.keywords.map((keyword) => (
          <span
            key={keyword}
            className="text-xs bg-white dark:bg-gray-800 px-2 py-0.5 rounded text-gray-500"
          >
            {keyword}
          </span>
        ))}
      </div>
    </div>
  );
};

const HighlightCard: React.FC<{ highlight: Highlight }> = ({ highlight }) => {
  const categoryConfig = {
    achievement: { icon: <Award className="w-4 h-4" />, color: 'text-green-500', bg: 'bg-green-50 dark:bg-green-900/20' },
    milestone: { icon: <Star className="w-4 h-4" />, color: 'text-yellow-500', bg: 'bg-yellow-50 dark:bg-yellow-900/20' },
    insight: { icon: <Lightbulb className="w-4 h-4" />, color: 'text-purple-500', bg: 'bg-purple-50 dark:bg-purple-900/20' },
    memorable: { icon: <Heart className="w-4 h-4" />, color: 'text-pink-500', bg: 'bg-pink-50 dark:bg-pink-900/20' },
    growth: { icon: <TrendingUp className="w-4 h-4" />, color: 'text-cyan-500', bg: 'bg-cyan-50 dark:bg-cyan-900/20' },
  };

  const config = categoryConfig[highlight.category];

  return (
    <div className={`${config.bg} rounded-lg p-4`}>
      <div className={`flex items-center gap-2 ${config.color} mb-2`}>
        {config.icon}
        <span className="text-xs font-medium uppercase tracking-wide">
          {highlight.category}
        </span>
      </div>
      <p className="text-sm text-gray-700 dark:text-gray-300 italic">
        "{highlight.content}"
      </p>
    </div>
  );
};

const RecommendationCard: React.FC<{ recommendation: Recommendation }> = ({ recommendation }) => {
  const typeIcons = {
    action: <Target className="w-4 h-4" />,
    reflection: <Lightbulb className="w-4 h-4" />,
    goal: <Star className="w-4 h-4" />,
    habit: <RefreshCw className="w-4 h-4" />,
  };

  const priorityColors = {
    high: 'border-red-500 bg-red-50 dark:bg-red-900/10',
    medium: 'border-yellow-500 bg-yellow-50 dark:bg-yellow-900/10',
    low: 'border-green-500 bg-green-50 dark:bg-green-900/10',
  };

  return (
    <div className={`border-l-4 ${priorityColors[recommendation.priority]} rounded-r-lg p-4`}>
      <div className="flex items-center gap-2 mb-1">
        <span className="text-gray-500">{typeIcons[recommendation.type]}</span>
        <h4 className="font-medium text-gray-900 dark:text-white">{recommendation.title}</h4>
      </div>
      <p className="text-sm text-gray-600 dark:text-gray-400">{recommendation.description}</p>
    </div>
  );
};

const WordCloud: React.FC<{ words: Record<string, number> }> = ({ words }) => {
  const sortedWords = Object.entries(words)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 30);

  const maxCount = sortedWords[0]?.[1] || 1;
  const minCount = sortedWords[sortedWords.length - 1]?.[1] || 1;

  const getSize = (count: number) => {
    const normalized = (count - minCount) / (maxCount - minCount || 1);
    return 12 + normalized * 24; // 12px to 36px
  };

  const colors = [
    'text-indigo-600 dark:text-indigo-400',
    'text-cyan-600 dark:text-cyan-400',
    'text-green-600 dark:text-green-400',
    'text-purple-600 dark:text-purple-400',
    'text-pink-600 dark:text-pink-400',
  ];

  return (
    <div className="flex flex-wrap gap-3 justify-center items-center p-4">
      {sortedWords.map(([word, count], i) => (
        <span
          key={word}
          style={{ fontSize: getSize(count) }}
          className={`${colors[i % colors.length]} font-medium hover:opacity-80 cursor-default transition-opacity`}
          title={`${word}: ${count} occurrences`}
        >
          {word}
        </span>
      ))}
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const SummaryViewer: React.FC<SummaryViewerProps> = ({
  summaryId,
  period = 'weekly',
  startDate,
  endDate,
  sphereIds,
  onClose,
  className = '',
}) => {
  const queryClient = useQueryClient();
  const [isDownloading, setIsDownloading] = useState(false);

  // Fetch or generate summary
  const {
    data: summary,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['summary', summaryId, period, startDate, endDate, sphereIds],
    queryFn: async () => {
      if (summaryId) {
        return getSummary(summaryId);
      }
      return generateSummary({ period, startDate, endDate, sphereIds });
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });

  // Download PDF
  const handleDownload = useCallback(async () => {
    if (!summary) return;

    setIsDownloading(true);
    try {
      const blob = await downloadPdf(summary.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `flashit-${summary.period}-summary.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Download failed:', error);
    } finally {
      setIsDownloading(false);
    }
  }, [summary]);

  // Format date range
  const dateRange = useMemo(() => {
    if (!summary) return '';
    const start = new Date(summary.startDate);
    const end = new Date(summary.endDate);
    return `${start.toLocaleDateString()} - ${end.toLocaleDateString()}`;
  }, [summary]);

  if (isLoading) {
    return (
      <div className={`flex items-center justify-center min-h-[400px] ${className}`}>
        <div className="text-center">
          <Loader2 className="w-12 h-12 text-indigo-600 animate-spin mx-auto mb-4" />
          <p className="text-gray-600 dark:text-gray-400">Generating your summary...</p>
          <p className="text-sm text-gray-500 mt-1">This may take a moment</p>
        </div>
      </div>
    );
  }

  if (error || !summary) {
    return (
      <div className={`flex items-center justify-center min-h-[400px] ${className}`}>
        <div className="text-center">
          <AlertCircle className="w-12 h-12 text-red-500 mx-auto mb-4" />
          <p className="text-gray-900 dark:text-white font-medium">Failed to load summary</p>
          <p className="text-sm text-gray-500 mt-1">
            {error instanceof Error ? error.message : 'Please try again'}
          </p>
          <button
            onClick={() => refetch()}
            className="mt-4 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={`bg-gray-50 dark:bg-gray-900 min-h-screen ${className}`}>
      {/* Header */}
      <div className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 sticky top-0 z-10">
        <div className="max-w-5xl mx-auto px-4 py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
                {summary.title}
              </h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 flex items-center gap-2 mt-1">
                <Calendar className="w-4 h-4" />
                {dateRange}
                <span className="mx-2">•</span>
                <Clock className="w-4 h-4" />
                Generated {new Date(summary.generatedAt).toLocaleDateString()}
              </p>
            </div>
            <div className="flex items-center gap-3">
              <button
                onClick={() => refetch()}
                className="p-2 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
                title="Regenerate"
              >
                <RefreshCw className="w-5 h-5" />
              </button>
              <button
                onClick={handleDownload}
                disabled={isDownloading}
                className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50"
              >
                {isDownloading ? (
                  <Loader2 className="w-4 h-4 animate-spin" />
                ) : (
                  <Download className="w-4 h-4" />
                )}
                Download PDF
              </button>
              {onClose && (
                <button
                  onClick={onClose}
                  className="p-2 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
                >
                  ✕
                </button>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="max-w-5xl mx-auto px-4 py-8 space-y-8">
        {/* Stats Grid */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <StatCard
            label="Moments"
            value={summary.momentCount}
            icon={<FileText className="w-5 h-5" />}
          />
          <StatCard
            label="Spheres"
            value={Object.keys(summary.sphereBreakdown).length}
            icon={<PieChart className="w-5 h-5" />}
            color="cyan"
          />
          <StatCard
            label="Topics"
            value={summary.topicClusters?.length || 0}
            icon={<Tag className="w-5 h-5" />}
            color="green"
          />
          <StatCard
            label="Highlights"
            value={summary.highlights?.length || 0}
            icon={<Star className="w-5 h-5" />}
            color="yellow"
          />
        </div>

        {/* Overview */}
        <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Overview</h2>
          <p className="text-gray-700 dark:text-gray-300 leading-relaxed whitespace-pre-line">
            {summary.overview}
          </p>
        </section>

        {/* Two Column Layout */}
        <div className="grid md:grid-cols-2 gap-6">
          {/* Emotion Analysis */}
          {summary.emotionAnalysis && (
            <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                Emotional Insights
              </h2>
              <MoodIndicator
                score={summary.emotionAnalysis.moodScore}
                trend={summary.emotionAnalysis.emotionTrend}
              />
              <div className="mt-6">
                <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
                  Emotion Distribution
                </h3>
                <EmotionDistributionChart distribution={summary.emotionAnalysis.emotionDistribution} />
              </div>
              {summary.emotionAnalysis.insights.length > 0 && (
                <div className="mt-6 bg-indigo-50 dark:bg-indigo-900/20 rounded-lg p-4">
                  <h3 className="text-sm font-medium text-indigo-700 dark:text-indigo-300 mb-2 flex items-center gap-2">
                    <Info className="w-4 h-4" />
                    Insights
                  </h3>
                  <ul className="space-y-2">
                    {summary.emotionAnalysis.insights.map((insight, i) => (
                      <li key={i} className="text-sm text-indigo-600 dark:text-indigo-400">
                        {insight}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </section>
          )}

          {/* Sphere Breakdown */}
          <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Moments by Sphere
            </h2>
            <SphereBreakdownChart breakdown={summary.sphereBreakdown} />
          </section>
        </div>

        {/* Topics */}
        {summary.topicClusters && summary.topicClusters.length > 0 && (
          <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Key Topics</h2>
            <div className="grid md:grid-cols-3 gap-4">
              {summary.topicClusters.map((topic, i) => (
                <TopicCard key={topic.topic} topic={topic} index={i} />
              ))}
            </div>
          </section>
        )}

        {/* Highlights */}
        {summary.highlights && summary.highlights.length > 0 && (
          <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Highlights</h2>
            <div className="grid md:grid-cols-2 gap-4">
              {summary.highlights.map((highlight) => (
                <HighlightCard key={highlight.momentId} highlight={highlight} />
              ))}
            </div>
          </section>
        )}

        {/* Recommendations */}
        {summary.recommendations && summary.recommendations.length > 0 && (
          <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Recommendations
            </h2>
            <div className="space-y-3">
              {summary.recommendations.map((rec, i) => (
                <RecommendationCard key={i} recommendation={rec} />
              ))}
            </div>
          </section>
        )}

        {/* Word Cloud */}
        {summary.wordCloud && Object.keys(summary.wordCloud).length > 0 && (
          <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Most Used Words
            </h2>
            <WordCloud words={summary.wordCloud} />
          </section>
        )}

        {/* Key Moments */}
        {summary.keyMoments && summary.keyMoments.length > 0 && (
          <section className="bg-white dark:bg-gray-800 rounded-xl p-6 shadow-sm">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Key Moments</h2>
            <div className="space-y-4">
              {summary.keyMoments.slice(0, 5).map((moment) => (
                <div
                  key={moment.id}
                  className="border-l-4 border-indigo-500 pl-4 py-2"
                >
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    {new Date(moment.capturedAt).toLocaleDateString()} • {moment.sphereName}
                  </p>
                  <p className="text-gray-700 dark:text-gray-300 mt-1">
                    {moment.content.substring(0, 200)}
                    {moment.content.length > 200 && '...'}
                  </p>
                  {moment.emotions.length > 0 && (
                    <div className="flex gap-2 mt-2">
                      {moment.emotions.map((emotion) => (
                        <span
                          key={emotion}
                          className="text-xs bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 px-2 py-0.5 rounded"
                        >
                          {emotion}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
};

export default SummaryViewer;
