/**
 * Smart Content Dashboard Component
 *
 * AI-enhanced dashboard that provides intelligent insights and recommendations
 * Reuses existing dashboard patterns and adds AI intelligence layer
 *
 * @doc.type component
 * @doc.purpose AI-powered content dashboard
 * @doc.layer component
 * @doc.pattern Smart Dashboard, AI Insights
 */

import { useState, useEffect } from "react";
import { Card, Button, Badge } from "@ghatana/ui";
import {
  Brain,
  TrendingUp,
  AlertTriangle,
  CheckCircle,
  Zap,
  Eye,
  Target,
  BarChart3,
  Sparkles,
  ArrowUp,
  ArrowDown,
  Minus,
} from "lucide-react";
import {
  aiServiceManager,
  type ContentIntelligence,
} from "../../services/aiServiceManager";

interface SmartDashboardProps {
  contentData?: any[];
  onInsightAction?: (action: string, data: any) => void;
  className?: string;
}

interface AIInsight {
  id: string;
  type: "opportunity" | "warning" | "success" | "trend";
  title: string;
  description: string;
  impact: "high" | "medium" | "low";
  action?: {
    label: string;
    type: "create" | "optimize" | "analyze" | "review";
    data?: any;
  };
  metrics?: {
    current: number;
    target: number;
    trend: "up" | "down" | "stable";
  };
}

interface SmartMetric {
  label: string;
  value: string | number;
  trend: "up" | "down" | "stable";
  trendValue?: number;
  insight: string;
  color: "green" | "yellow" | "red" | "blue";
}

export function SmartDashboard({
  contentData = [],
  onInsightAction,
  className = "",
}: SmartDashboardProps) {
  const [insights, setInsights] = useState<AIInsight[]>([]);
  const [metrics, setMetrics] = useState<SmartMetric[]>([]);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [lastAnalysis, setLastAnalysis] = useState<Date | null>(null);

  // Analyze content and generate AI insights
  useEffect(() => {
    const analyzeContent = async () => {
      setIsAnalyzing(true);

      try {
        // Get AI intelligence for content
        const intelligence = await aiServiceManager.analyzeContent(contentData);

        // Generate insights from intelligence
        const generatedInsights = generateInsights(intelligence);
        setInsights(generatedInsights);

        // Generate smart metrics
        const generatedMetrics = generateMetrics(intelligence);
        setMetrics(generatedMetrics);

        setLastAnalysis(new Date());
      } catch (error) {
        console.error("Content analysis failed:", error);
        // Fallback to mock insights
        setInsights(getMockInsights());
        setMetrics(getMockMetrics());
      } finally {
        setIsAnalyzing(false);
      }
    };

    if (contentData.length > 0) {
      analyzeContent();
    } else {
      // Show initial insights for empty state
      setInsights(getEmptyStateInsights());
      setMetrics(getMockMetrics());
    }
  }, [contentData]);

  // Generate AI insights from content intelligence
  const generateInsights = (intelligence: ContentIntelligence): AIInsight[] => {
    const insights: AIInsight[] = [];

    // Quality insights
    if (intelligence.qualityScore < 70) {
      insights.push({
        id: "quality-low",
        type: "warning",
        title: "Content Quality Needs Attention",
        description: `Average quality score is ${intelligence.qualityScore}%. Consider reviewing and improving content structure.`,
        impact: "high",
        action: {
          label: "Improve Quality",
          type: "optimize",
          data: { qualityScore: intelligence.qualityScore },
        },
        metrics: {
          current: intelligence.qualityScore,
          target: 85,
          trend: "down",
        },
      });
    } else if (intelligence.qualityScore > 85) {
      insights.push({
        id: "quality-high",
        type: "success",
        title: "Excellent Content Quality",
        description: `Content quality is outstanding at ${intelligence.qualityScore}%. Keep up the great work!`,
        impact: "low",
        metrics: {
          current: intelligence.qualityScore,
          target: 90,
          trend: "up",
        },
      });
    }

    // Engagement insights
    if (intelligence.engagementPrediction < 60) {
      insights.push({
        id: "engagement-low",
        type: "opportunity",
        title: "Boost Student Engagement",
        description:
          "AI predicts low engagement. Add interactive elements and real-world applications.",
        impact: "medium",
        action: {
          label: "Enhance Engagement",
          type: "create",
          data: { currentEngagement: intelligence.engagementPrediction },
        },
        metrics: {
          current: intelligence.engagementPrediction,
          target: 75,
          trend: "down",
        },
      });
    }

    // Knowledge gap insights
    if (intelligence.knowledgeGapAnalysis.length > 0) {
      insights.push({
        id: "knowledge-gaps",
        type: "opportunity",
        title: "Address Knowledge Gaps",
        description: `Found ${intelligence.knowledgeGapAnalysis.length} knowledge gaps that need attention.`,
        impact: "medium",
        action: {
          label: "Fill Gaps",
          type: "create",
          data: { gaps: intelligence.knowledgeGapAnalysis },
        },
      });
    }

    // Optimization suggestions
    if (intelligence.optimizationSuggestions.length > 0) {
      insights.push({
        id: "optimization",
        type: "trend",
        title: "AI Optimization Available",
        description: `AI has ${intelligence.optimizationSuggestions.length} suggestions to improve content effectiveness.`,
        impact: "low",
        action: {
          label: "View Suggestions",
          type: "analyze",
          data: { suggestions: intelligence.optimizationSuggestions },
        },
      });
    }

    return insights;
  };

  // Generate smart metrics from intelligence
  const generateMetrics = (
    intelligence: ContentIntelligence,
  ): SmartMetric[] => {
    return [
      {
        label: "Content Quality",
        value: `${intelligence.qualityScore}%`,
        trend:
          intelligence.qualityScore > 80
            ? "up"
            : intelligence.qualityScore < 60
              ? "down"
              : "stable",
        trendValue: intelligence.qualityScore - 75,
        insight:
          intelligence.qualityScore > 80
            ? "Excellent quality"
            : "Room for improvement",
        color:
          intelligence.qualityScore > 80
            ? "green"
            : intelligence.qualityScore < 60
              ? "red"
              : "yellow",
      },
      {
        label: "Engagement Prediction",
        value: `${Math.round(intelligence.engagementPrediction)}%`,
        trend: intelligence.engagementPrediction > 70 ? "up" : "down",
        trendValue: intelligence.engagementPrediction - 65,
        insight: "AI-predicted student engagement",
        color: intelligence.engagementPrediction > 70 ? "green" : "yellow",
      },
      {
        label: "Knowledge Gaps",
        value: intelligence.knowledgeGapAnalysis.length,
        trend: intelligence.knowledgeGapAnalysis.length > 0 ? "down" : "stable",
        insight: "Identified learning gaps",
        color: intelligence.knowledgeGapAnalysis.length > 0 ? "red" : "green",
      },
      {
        label: "Optimization Opportunities",
        value: intelligence.optimizationSuggestions.length,
        trend: "stable",
        insight: "AI improvement suggestions",
        color: "blue",
      },
    ];
  };

  // Mock data for development
  const getMockInsights = (): AIInsight[] => [
    {
      id: "mock-1",
      type: "opportunity",
      title: "Create Interactive Physics Content",
      description:
        "AI suggests adding more simulations for physics topics to improve engagement.",
      impact: "medium",
      action: {
        label: "Create Content",
        type: "create",
        data: { subject: "physics", type: "simulation" },
      },
    },
    {
      id: "mock-2",
      type: "warning",
      title: "Math Content Needs Updates",
      description: "Some math content shows declining engagement scores.",
      impact: "high",
      action: {
        label: "Review Content",
        type: "analyze",
        data: { subject: "mathematics" },
      },
    },
  ];

  const getMockMetrics = (): SmartMetric[] => [
    {
      label: "Content Quality",
      value: "82%",
      trend: "up",
      trendValue: 7,
      insight: "Above average quality",
      color: "green",
    },
    {
      label: "Student Engagement",
      value: "73%",
      trend: "up",
      trendValue: 5,
      insight: "Good engagement levels",
      color: "green",
    },
    {
      label: "Completion Rate",
      value: "68%",
      trend: "stable",
      insight: "Steady completion rates",
      color: "yellow",
    },
    {
      label: "AI Suggestions",
      value: 12,
      trend: "down",
      insight: "Optimization opportunities",
      color: "blue",
    },
  ];

  const getEmptyStateInsights = (): AIInsight[] => [
    {
      id: "empty-1",
      type: "opportunity",
      title: "Start Creating Content",
      description:
        "Begin by creating your first learning experience with AI assistance.",
      impact: "high",
      action: {
        label: "Create Content",
        type: "create",
      },
    },
  ];

  // Handle insight actions
  const handleInsightAction = (insight: AIInsight) => {
    if (insight.action && onInsightAction) {
      onInsightAction(insight.action.type, insight.action.data);
    }
  };

  // Get trend icon
  const getTrendIcon = (trend: "up" | "down" | "stable") => {
    switch (trend) {
      case "up":
        return <ArrowUp className="h-4 w-4 text-green-500" />;
      case "down":
        return <ArrowDown className="h-4 w-4 text-red-500" />;
      default:
        return <Minus className="h-4 w-4 text-gray-400" />;
    }
  };

  // Get insight icon and color
  const getInsightIcon = (type: AIInsight["type"]) => {
    switch (type) {
      case "opportunity":
        return { icon: <Target className="h-5 w-5" />, color: "blue" };
      case "warning":
        return { icon: <AlertTriangle className="h-5 w-5" />, color: "yellow" };
      case "success":
        return { icon: <CheckCircle className="h-5 w-5" />, color: "green" };
      case "trend":
        return { icon: <TrendingUp className="h-5 w-5" />, color: "purple" };
      default:
        return { icon: <Brain className="h-5 w-5" />, color: "gray" };
    }
  };

  const getImpactColor = (impact: AIInsight["impact"]) => {
    switch (impact) {
      case "high":
        return "bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400";
      case "medium":
        return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400";
      case "low":
        return "bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-400";
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-900/20 dark:text-gray-400";
    }
  };

  return (
    <div className={`space-y-6 ${className}`}>
      {/* AI Analysis Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex items-center justify-center w-10 h-10 bg-gradient-to-r from-purple-500 to-blue-500 rounded-full">
            <Brain className="h-5 w-5 text-white" />
          </div>
          <div>
            <h2 className="text-xl font-semibold text-gray-900 dark:text-white">
              AI Content Intelligence
            </h2>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              {lastAnalysis
                ? `Last analyzed: ${lastAnalysis.toLocaleTimeString()}`
                : "Ready to analyze"}
            </p>
          </div>
        </div>
        <Button
          onClick={() => window.location.reload()}
          disabled={isAnalyzing}
          className="flex items-center gap-2"
        >
          <Sparkles className="h-4 w-4" />
          {isAnalyzing ? "Analyzing..." : "Refresh Analysis"}
        </Button>
      </div>

      {/* Smart Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {metrics.map((metric, index) => (
          <Card key={index} className="p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium text-gray-600 dark:text-gray-400">
                {metric.label}
              </span>
              {getTrendIcon(metric.trend)}
            </div>
            <div className="flex items-baseline gap-2">
              <span className="text-2xl font-bold text-gray-900 dark:text-white">
                {metric.value}
              </span>
              {metric.trendValue && (
                <span
                  className={`text-sm ${
                    metric.trend === "up"
                      ? "text-green-600"
                      : metric.trend === "down"
                        ? "text-red-600"
                        : "text-gray-500"
                  }`}
                >
                  {metric.trendValue > 0 ? "+" : ""}
                  {metric.trendValue}%
                </span>
              )}
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
              {metric.insight}
            </p>
          </Card>
        ))}
      </div>

      {/* AI Insights */}
      <div className="space-y-4">
        <div className="flex items-center gap-2">
          <Eye className="h-5 w-5 text-purple-500" />
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            AI Insights & Recommendations
          </h3>
        </div>

        <div className="grid gap-4">
          {insights.map((insight) => {
            const { icon, color } = getInsightIcon(insight.type);
            return (
              <Card key={insight.id} className="p-4">
                <div className="flex items-start gap-3">
                  <div
                    className={`flex-shrink-0 w-10 h-10 bg-${color}-100 dark:bg-${color}-900/20 rounded-full flex items-center justify-center text-${color}-600 dark:text-${color}-400`}
                  >
                    {icon}
                  </div>

                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <h4 className="font-medium text-gray-900 dark:text-white">
                        {insight.title}
                      </h4>
                      <Badge className={getImpactColor(insight.impact)}>
                        {insight.impact} impact
                      </Badge>
                    </div>

                    <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                      {insight.description}
                    </p>

                    {/* Metrics Display */}
                    {insight.metrics && (
                      <div className="flex items-center gap-4 mb-3 text-sm">
                        <div className="flex items-center gap-2">
                          <span className="text-gray-500">Current:</span>
                          <span className="font-medium">
                            {insight.metrics.current}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="text-gray-500">Target:</span>
                          <span className="font-medium">
                            {insight.metrics.target}
                          </span>
                        </div>
                        <div className="flex items-center gap-1">
                          {getTrendIcon(insight.metrics.trend)}
                        </div>
                      </div>
                    )}

                    {/* Action Button */}
                    {insight.action && (
                      <Button
                        onClick={() => handleInsightAction(insight)}
                        variant="outline"
                        size="sm"
                        className="flex items-center gap-2"
                      >
                        <Zap className="h-4 w-4" />
                        {insight.action.label}
                      </Button>
                    )}
                  </div>
                </div>
              </Card>
            );
          })}
        </div>
      </div>

      {/* Empty State */}
      {insights.length === 0 && !isAnalyzing && (
        <Card className="p-8 text-center">
          <Brain className="h-12 w-12 text-gray-400 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
            No Content Available
          </h3>
          <p className="text-gray-600 dark:text-gray-400 mb-4">
            Start creating content to see AI-powered insights and
            recommendations.
          </p>
          <Button className="bg-gradient-to-r from-purple-500 to-blue-500">
            <Sparkles className="h-4 w-4 mr-2" />
            Create Your First Content
          </Button>
        </Card>
      )}
    </div>
  );
}

export default SmartDashboard;
