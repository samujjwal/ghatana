import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  RadarChart,
  Radar,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  LineChart,
  Line,
  CartesianGrid,
  Legend,
  Cell,
} from "recharts";
import { CheckCircle2, AlertTriangle, XCircle, TrendingUp, TrendingDown, BookOpen, Target, Award } from "lucide-react";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type EvidenceLevel = "strong" | "moderate" | "weak" | "insufficient";
type ContentType = "video" | "quiz" | "simulation" | "animation" | "reading" | "exercise";

interface EvidenceItem {
  contentId: string;
  contentTitle: string;
  contentType: ContentType;
  evidenceLevel: EvidenceLevel;
  qualityScore: number; // 0-100
  completionRate: number; // 0-100
  learnerCount: number;
  avgEngagementScore: number; // 0-100
  learningOutcomeCoverage: number; // 0-100
  suggestions: string[];
}

interface LearningPathNode {
  id: string;
  title: string;
  type: ContentType;
  difficulty: 1 | 2 | 3 | 4 | 5;
  prerequisiteIds: string[];
  masteryRate: number; // 0-100
}

interface AssessmentResult {
  week: string;
  averageScore: number;
  passRate: number;
  attempts: number;
}

interface AnalyticsData {
  evidence: EvidenceItem[];
  learningPath: LearningPathNode[];
  assessmentTrend: AssessmentResult[];
  overallQuality: number;
  totalLearners: number;
  activeContent: number;
  improvementsNeeded: number;
}

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------

const MOCK: AnalyticsData = {
  overallQuality: 74,
  totalLearners: 12_847,
  activeContent: 143,
  improvementsNeeded: 18,
  assessmentTrend: [
    { week: "W1", averageScore: 61, passRate: 72, attempts: 840 },
    { week: "W2", averageScore: 64, passRate: 75, attempts: 920 },
    { week: "W3", averageScore: 67, passRate: 78, attempts: 1100 },
    { week: "W4", averageScore: 70, passRate: 81, attempts: 1050 },
    { week: "W5", averageScore: 73, passRate: 84, attempts: 1230 },
    { week: "W6", averageScore: 76, passRate: 87, attempts: 1310 },
  ],
  learningPath: [
    { id: "lp-1", title: "Intro to Forces", type: "video", difficulty: 1, prerequisiteIds: [], masteryRate: 91 },
    { id: "lp-2", title: "Newton's Laws", type: "reading", difficulty: 2, prerequisiteIds: ["lp-1"], masteryRate: 83 },
    { id: "lp-3", title: "Gravity Simulation", type: "simulation", difficulty: 3, prerequisiteIds: ["lp-2"], masteryRate: 71 },
    { id: "lp-4", title: "Forces Quiz", type: "quiz", difficulty: 3, prerequisiteIds: ["lp-2"], masteryRate: 68 },
    { id: "lp-5", title: "Motion Animation", type: "animation", difficulty: 4, prerequisiteIds: ["lp-3"], masteryRate: 56 },
    { id: "lp-6", title: "Advanced Problems", type: "exercise", difficulty: 5, prerequisiteIds: ["lp-4", "lp-5"], masteryRate: 42 },
  ],
  evidence: [
    {
      contentId: "c-1",
      contentTitle: "Intro to Forces (Video)",
      contentType: "video",
      evidenceLevel: "strong",
      qualityScore: 91,
      completionRate: 88,
      learnerCount: 4200,
      avgEngagementScore: 87,
      learningOutcomeCoverage: 95,
      suggestions: [],
    },
    {
      contentId: "c-2",
      contentTitle: "Newton's Laws Reading",
      contentType: "reading",
      evidenceLevel: "moderate",
      qualityScore: 72,
      completionRate: 63,
      learnerCount: 3800,
      avgEngagementScore: 61,
      learningOutcomeCoverage: 78,
      suggestions: [
        "Add visual diagrams to improve retention",
        "Break into shorter sections with comprehension checks",
      ],
    },
    {
      contentId: "c-3",
      contentTitle: "Gravity Simulation",
      contentType: "simulation",
      evidenceLevel: "strong",
      qualityScore: 88,
      completionRate: 79,
      learnerCount: 2900,
      avgEngagementScore: 93,
      learningOutcomeCoverage: 87,
      suggestions: ["Add guided mode for first-time users"],
    },
    {
      contentId: "c-4",
      contentTitle: "Forces Quiz",
      contentType: "quiz",
      evidenceLevel: "weak",
      qualityScore: 54,
      completionRate: 71,
      learnerCount: 3100,
      avgEngagementScore: 55,
      learningOutcomeCoverage: 52,
      suggestions: [
        "Rewrite 3 ambiguous questions",
        "Add explanations to wrong-answer feedback",
        "Include more scenario-based questions",
      ],
    },
    {
      contentId: "c-5",
      contentTitle: "Motion Animation",
      contentType: "animation",
      evidenceLevel: "moderate",
      qualityScore: 67,
      completionRate: 74,
      learnerCount: 2400,
      avgEngagementScore: 79,
      learningOutcomeCoverage: 63,
      suggestions: ["Slow down key transition at 0:45", "Add pause-and-reflect prompts"],
    },
    {
      contentId: "c-6",
      contentTitle: "Advanced Problems",
      contentType: "exercise",
      evidenceLevel: "insufficient",
      qualityScore: 38,
      completionRate: 31,
      learnerCount: 1200,
      avgEngagementScore: 42,
      learningOutcomeCoverage: 35,
      suggestions: [
        "Difficulty spike — add intermediate step exercises",
        "Provide worked examples before problems",
        "Add progress checkpoints",
        "Review prerequisite alignment",
      ],
    },
  ],
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const EVIDENCE_CONFIGS: Record<EvidenceLevel, { label: string; icon: React.ElementType; color: string; bg: string }> = {
  strong: { label: "Strong", icon: CheckCircle2, color: "text-green-600 dark:text-green-400", bg: "bg-green-50 dark:bg-green-900/20" },
  moderate: { label: "Moderate", icon: TrendingUp, color: "text-blue-600 dark:text-blue-400", bg: "bg-blue-50 dark:bg-blue-900/20" },
  weak: { label: "Weak", icon: AlertTriangle, color: "text-amber-600 dark:text-amber-400", bg: "bg-amber-50 dark:bg-amber-900/20" },
  insufficient: { label: "Insufficient", icon: XCircle, color: "text-red-600 dark:text-red-400", bg: "bg-red-50 dark:bg-red-900/20" },
};

const CONTENT_TYPE_ICONS: Record<ContentType, string> = {
  video: "🎬",
  quiz: "📝",
  simulation: "⚙️",
  animation: "🎨",
  reading: "📖",
  exercise: "💪",
};

const RADAR_DIMENSIONS = [
  { label: "Quality Score", key: "qualityScore" },
  { label: "Completion", key: "completionRate" },
  { label: "Engagement", key: "avgEngagementScore" },
  { label: "Outcome Coverage", key: "learningOutcomeCoverage" },
];

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function StatCard({ label, value, delta, icon: Icon, unit }: {
  label: string;
  value: number | string;
  delta?: number;
  icon: React.ElementType;
  unit?: string;
}) {
  return (
    <div className="flex flex-col gap-1 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4">
      <div className="flex items-center gap-2">
        <Icon className="h-4 w-4 text-indigo-500" aria-hidden />
        <span className="text-xs text-gray-500 dark:text-gray-400">{label}</span>
      </div>
      <div className="text-2xl font-bold text-gray-800 dark:text-gray-100">
        {value}{unit}
      </div>
      {delta !== undefined && (
        <div className={`flex items-center gap-1 text-xs ${delta >= 0 ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400"}`}>
          {delta >= 0 ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
          {Math.abs(delta)}% vs last month
        </div>
      )}
    </div>
  );
}

function EvidenceMatrix({ items }: { items: EvidenceItem[] }) {
  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4">
      <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">Evidence Matrix</h2>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="border-b border-gray-100 dark:border-gray-800 text-gray-400 dark:text-gray-500">
              <th className="text-left py-2 pr-3 font-medium">Content</th>
              <th className="text-center py-2 px-2 font-medium">Quality</th>
              <th className="text-center py-2 px-2 font-medium">Completion</th>
              <th className="text-center py-2 px-2 font-medium">Engagement</th>
              <th className="text-center py-2 px-2 font-medium">Coverage</th>
              <th className="text-left py-2 pl-3 font-medium">Evidence</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => {
              const cfg = EVIDENCE_CONFIGS[item.evidenceLevel];
              const Icon = cfg.icon;
              return (
                <tr key={item.contentId} className="border-b border-gray-50 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50">
                  <td className="py-2 pr-3">
                    <div className="flex items-center gap-1.5">
                      <span>{CONTENT_TYPE_ICONS[item.contentType]}</span>
                      <span className="font-medium text-gray-700 dark:text-gray-300 truncate max-w-32">{item.contentTitle}</span>
                    </div>
                  </td>
                  {([item.qualityScore, item.completionRate, item.avgEngagementScore, item.learningOutcomeCoverage] as number[]).map((score, i) => (
                    <td key={i} className="py-2 px-2 text-center">
                      <span className={`inline-block w-10 text-center font-semibold rounded px-1 py-0.5 ${
                        score >= 80
                          ? "bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400"
                          : score >= 60
                            ? "bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-400"
                            : score >= 40
                              ? "bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-400"
                              : "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400"
                      }`}>
                        {score}
                      </span>
                    </td>
                  ))}
                  <td className="py-2 pl-3">
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${cfg.bg} ${cfg.color}`}>
                      <Icon className="h-3 w-3" aria-hidden />
                      {cfg.label}
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function AssessmentTrendChart({ data }: { data: AssessmentResult[] }) {
  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4">
      <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">Assessment Trend</h2>
      <ResponsiveContainer width="100%" height={200}>
        <LineChart data={data}>
          <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.06)" />
          <XAxis dataKey="week" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} domain={[0, 100]} />
          <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
          <Legend iconSize={10} wrapperStyle={{ fontSize: 11 }} />
          <Line type="monotone" dataKey="averageScore" stroke="#6366f1" strokeWidth={2} dot={{ r: 3 }} name="Avg Score" />
          <Line type="monotone" dataKey="passRate" stroke="#10b981" strokeWidth={2} dot={{ r: 3 }} name="Pass Rate %" />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function LearningPathways({ nodes }: { nodes: LearningPathNode[] }) {
  const difficultyColor = (d: number) =>
    d <= 2 ? "#10b981" : d === 3 ? "#f59e0b" : d >= 4 ? "#ef4444" : "#6366f1";

  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4">
      <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">Learning Pathways</h2>
      <div className="space-y-2">
        {nodes.map((node) => (
          <div key={node.id} className="flex items-center gap-3">
            <span className="text-sm">{CONTENT_TYPE_ICONS[node.type]}</span>
            <div className="flex-1 min-w-0">
              <div className="flex items-center justify-between gap-2 mb-0.5">
                <span className="text-xs font-medium text-gray-700 dark:text-gray-300 truncate">{node.title}</span>
                <span className="text-xs text-gray-400 dark:text-gray-500 flex-shrink-0">{node.masteryRate}% mastery</span>
              </div>
              <div className="w-full bg-gray-100 dark:bg-gray-800 rounded-full h-1.5">
                <div
                  className="h-1.5 rounded-full transition-all"
                  style={{ width: `${node.masteryRate}%`, backgroundColor: difficultyColor(node.difficulty) }}
                />
              </div>
            </div>
            <span
              className="flex-shrink-0 text-xs px-1.5 py-0.5 rounded font-medium"
              style={{
                backgroundColor: `${difficultyColor(node.difficulty)}20`,
                color: difficultyColor(node.difficulty),
              }}
              title={`Difficulty ${node.difficulty}/5`}
            >
              D{node.difficulty}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ImprovementSuggestions({ items }: { items: EvidenceItem[] }) {
  const needsWork = items.filter((i) => i.suggestions.length > 0).sort((a, b) => a.qualityScore - b.qualityScore);

  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4">
      <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">Improvement Suggestions</h2>
      <div className="space-y-3">
        {needsWork.map((item) => {
          const cfg = EVIDENCE_CONFIGS[item.evidenceLevel];
          return (
            <div key={item.contentId} className={`rounded-lg p-3 ${cfg.bg}`}>
              <div className="flex items-center gap-2 mb-1.5">
                <span>{CONTENT_TYPE_ICONS[item.contentType]}</span>
                <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">{item.contentTitle}</span>
                <span className={`ml-auto text-xs font-medium ${cfg.color}`}>{item.qualityScore}/100</span>
              </div>
              <ul className="space-y-1">
                {item.suggestions.map((s, i) => (
                  <li key={i} className="flex items-start gap-1.5 text-xs text-gray-600 dark:text-gray-400">
                    <span className="mt-0.5 flex-shrink-0">•</span>
                    {s}
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function QualityRadar({ items }: { items: EvidenceItem[] }) {
  // Aggregate average across all content for radar
  const radarData = RADAR_DIMENSIONS.map(({ label, key }) => ({
    metric: label,
    value: Math.round(items.reduce((sum, it) => sum + (it[key as keyof EvidenceItem] as number), 0) / items.length),
  }));

  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4">
      <h2 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-1">Quality Metrics (Portfolio Avg)</h2>
      <ResponsiveContainer width="100%" height={220}>
        <RadarChart data={radarData}>
          <PolarGrid stroke="rgba(0,0,0,0.08)" />
          <PolarAngleAxis dataKey="metric" tick={{ fontSize: 10 }} />
          <PolarRadiusAxis angle={30} domain={[0, 100]} tick={{ fontSize: 9 }} />
          <Radar name="Average" dataKey="value" fill="#6366f1" fillOpacity={0.25} stroke="#6366f1" strokeWidth={2} />
          <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
        </RadarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

/**
 * EvidenceAnalyticsPage — Phase 2 learning evidence dashboard for Tutorputor.
 *
 * Provides educators and content managers with:
 *  - Evidence matrix showing content quality across multiple dimensions
 *  - Assessment trend charts over time
 *  - Learning pathway mastery visualisation
 *  - Portfolio-level radar quality metrics
 *  - Ranked improvement suggestions per content item
 */
export function EvidenceAnalyticsPage(): React.ReactElement {
  const { data = MOCK, isLoading } = useQuery<AnalyticsData>({
    queryKey: ['evidence-analytics'],
    queryFn: async () => {
      const res = await fetch('/api/evidence/analytics');
      if (!res.ok) throw new Error(`Failed to fetch analytics: ${res.status}`);
      return res.json() as Promise<AnalyticsData>;
    },
    staleTime: 5 * 60 * 1000,
    placeholderData: MOCK,
  });
  const [evidenceFilter, setEvidenceFilter] = useState<EvidenceLevel | "all">("all");

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64 text-gray-500">
        Loading analytics…
      </div>
    );
  }

  const filteredEvidence =
    evidenceFilter === "all"
      ? data.evidence
      : data.evidence.filter((e) => e.evidenceLevel === evidenceFilter);

  return (
    <div className="flex flex-col gap-5 p-5 overflow-auto">
      {/* Page header */}
      <div>
        <h1 className="text-xl font-bold text-gray-800 dark:text-gray-100 flex items-center gap-2">
          <BookOpen className="h-5 w-5 text-indigo-500" aria-hidden />
          Evidence Analytics
        </h1>
        <p className="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
          Learning evidence quality, assessment trends, and improvement recommendations.
        </p>
      </div>

      {/* Summary stats */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatCard label="Overall Quality" value={data.overallQuality} delta={+6} icon={Award} unit="%" />
        <StatCard label="Total Learners" value={data.totalLearners.toLocaleString()} delta={+12} icon={Target} />
        <StatCard label="Active Content" value={data.activeContent} icon={BookOpen} />
        <StatCard label="Needs Improvement" value={data.improvementsNeeded} delta={-3} icon={AlertTriangle} />
      </div>

      {/* Evidence filter pills */}
      <div className="flex flex-wrap gap-2">
        {(["all", "strong", "moderate", "weak", "insufficient"] as const).map((f) => {
          const cfg = f === "all" ? null : EVIDENCE_CONFIGS[f];
          return (
            <button
              key={f}
              onClick={() => setEvidenceFilter(f)}
              className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
                evidenceFilter === f
                  ? "bg-indigo-600 border-indigo-600 text-white"
                  : "border-gray-200 dark:border-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800"
              }`}
            >
              {f === "all" ? "All" : cfg?.label}
            </button>
          );
        })}
      </div>

      {/* Main grid */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        {/* Full-width evidence matrix */}
        <div className="lg:col-span-3">
          <EvidenceMatrix items={filteredEvidence} />
        </div>

        {/* Assessment trend — 2 cols */}
        <div className="lg:col-span-2">
          <AssessmentTrendChart data={data.assessmentTrend} />
        </div>

        {/* Radar — 1 col */}
        <div className="lg:col-span-1">
          <QualityRadar items={data.evidence} />
        </div>

        {/* Learning pathways — 1 col */}
        <div className="lg:col-span-1">
          <LearningPathways nodes={data.learningPath} />
        </div>

        {/* Improvement suggestions — 2 cols */}
        <div className="lg:col-span-2">
          <ImprovementSuggestions items={filteredEvidence} />
        </div>
      </div>
    </div>
  );
}
