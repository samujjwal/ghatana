/**
 * Unified Content Studio
 *
 * Comprehensive content management hub that combines:
 * - AI-powered content creation (learning experiences)
 * - Content creation system administration (infrastructure, analytics)
 * - Template management and monitoring
 *
 * Features:
 * - Multi-tab interface for different content aspects
 * - Real-time infrastructure monitoring
 * - Content analytics and templates
 * - AI-powered learning experience creation
 *
 * @doc.type component
 * @doc.purpose Unified content creation and management
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState, useCallback, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Sparkles,
  FileText,
  BarChart3,
  Cpu,
  Database,
  Activity,
  TrendingUp,
  Layers,
  Clock,
  Play,
  Pause,
  Square,
  Settings,
  Calendar,
  Zap,
  CheckCircle,
  XCircle,
  AlertCircle,
} from "lucide-react";
import { Button } from "@ghatana/ui";
import { useContentStudioApi } from "../services/contentStudioApi";
import { AIAssistant, SmartDashboard, aiServiceManager } from "./ai";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  AreaChart,
  Area,
} from "recharts";

// Types from AdminContentCreationDashboard
interface ContentCreationStats {
  totalContent: number;
  byType: {
    examples: number;
    simulations: number;
    animations: number;
    assessments: number;
    explanations: number;
  };
  byDomain: {
    physics: number;
    chemistry: number;
    biology: number;
    mathematics: number;
    computerScience: number;
    economics: number;
    medicine: number;
    general: number;
  };
  qualityDistribution: {
    excellent: number;
    good: number;
    fair: number;
    poor: number;
  };
}

interface InfrastructureStatus {
  aiModels: {
    openai: { status: string; latency: number; successRate: number };
    ollama: { status: string; latency: number; successRate: number };
    claude: { status: string; latency: number; successRate: number };
  };
  databases: {
    postgres: { status: string; connections: number; queryTime: number };
    redis: { status: string; memory: number; hitRate: number };
  };
  storage: {
    s3: { status: string; size: number; objects: number };
    local: { status: string; size: number; objects: number };
  };
}

interface TemplateStats {
  totalTemplates: number;
  byDomain: Record<string, number>;
  usageStats: {
    mostUsed: string;
    leastUsed: string;
    avgUsage: number;
  };
  recentTemplates: Array<{
    id: string;
    name: string;
    domain: string;
    usageCount: number;
    lastUsed: string;
  }>;
}

// Content Studio Types
interface GradeAdaptation {
  gradeRange: string;
  mathLevel: string;
  rigorLevel: string;
  scaffoldingLevel: string;
  vocabularyComplexity: number;
  readingLevel: number;
  prerequisiteConcepts: string[];
}

interface LearningClaim {
  id: string;
  text: string;
  bloom: string;
  masteryThreshold: number;
  orderIndex: number;
  evidenceRequirements: LearningEvidence[];
  tasks: ExperienceTask[];
}

interface LearningEvidence {
  id: string;
  claimId: string;
  type: string;
  description: string;
  minimumScore: number;
  weight: number;
}

interface ExperienceTask {
  id: string;
  claimId: string;
  type: string;
  title: string;
  instructions: string;
  evidenceIds: string[];
  estimatedMinutes: number;
  orderIndex: number;
}

interface LearningExperience {
  id: string;
  tenantId: string;
  slug: string;
  title: string;
  description: string;
  status: string;
  version: number;
  gradeAdaptation: GradeAdaptation;
  claims: LearningClaim[];
  estimatedTimeMinutes: number;
  keywords: string[];
  moduleId?: string;
  simulationId?: string;
  authorId?: string;
  createdAt: Date;
  updatedAt: Date;
}

type ContentStudioView =
  | "dashboard" // Main content creation dashboard
  | "infrastructure" // AI models, databases, storage monitoring
  | "analytics" // Content analytics and metrics
  | "templates" // Template management
  | "automation" // Automated content generation management
  | "create" // Create new learning experience
  | "edit" // Edit existing experience
  | "validate"; // Validate content

// Automated Content Generation Types
interface AutomatedGenerationJob {
  id: string;
  name: string;
  description: string;
  schedule: string; // cron expression
  status: "active" | "paused" | "stopped" | "failed";
  lastRun: string;
  nextRun: string;
  totalRuns: number;
  successRate: number;
  averageDuration: number;
  config: {
    domains: string[];
    gradeLevels: string[];
    contentTypes: string[];
    batchSize: number;
    aiProvider: string;
  };
}

interface JobQueue {
  pending: number;
  running: number;
  completed: number;
  failed: number;
  averageWaitTime: number;
}

interface GenerationMetrics {
  jobsPerHour: number;
  successRate: number;
  averageDuration: number;
  contentGenerated: number;
  errorsByType: Record<string, number>;
}

export function UnifiedContentStudio() {
  const [searchParams, setSearchParams] = useSearchParams();
  const experienceId = searchParams.get("id");
  const viewParam = searchParams.get("view") as ContentStudioView | null;

  const [currentView, setCurrentView] = useState<ContentStudioView>(
    viewParam || "dashboard",
  );
  const [currentExperience, setCurrentExperience] =
    useState<LearningExperience | null>(null);
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationRequest, setGenerationRequest] = useState({
    topic: "",
    gradeLevel: "",
    subject: "",
    includeRealWorldUseCases: true,
    includePracticeWorksheets: true,
    includeQuizzes: true,
  });

  // Content creation system state
  const [contentStats, setContentStats] = useState<ContentCreationStats | null>(
    null,
  );
  const [infrastructureStatus, setInfrastructureStatus] =
    useState<InfrastructureStatus | null>(null);
  const [templateStats, setTemplateStats] = useState<TemplateStats | null>(
    null,
  );

  // Automated generation state
  const [automatedJobs, setAutomatedJobs] = useState<AutomatedGenerationJob[]>(
    [],
  );
  const [jobQueue, setJobQueue] = useState<JobQueue | null>(null);
  const [generationMetrics, setGenerationMetrics] =
    useState<GenerationMetrics | null>(null);

  const contentStudioApi = useContentStudioApi();

  // Navigation tabs
  const navigationTabs = [
    {
      id: "dashboard",
      name: "Dashboard",
      icon: BarChart3,
      description: "Content creation overview",
    },
    {
      id: "infrastructure",
      name: "Infrastructure",
      icon: Cpu,
      description: "AI models & systems",
    },
    {
      id: "analytics",
      name: "Analytics",
      icon: TrendingUp,
      description: "Content metrics",
    },
    {
      id: "templates",
      name: "Templates",
      icon: FileText,
      description: "Template library",
    },
    {
      id: "automation",
      name: "Automation",
      icon: Zap,
      description: "Automated generation",
    },
    {
      id: "create",
      name: "Create Content",
      icon: Sparkles,
      description: "AI content creation",
    },
  ];

  // Simulate real-time data for content creation system
  useEffect(() => {
    const interval = setInterval(() => {
      setContentStats({
        totalContent: Math.floor(Math.random() * 10000) + 5000,
        byType: {
          examples: Math.floor(Math.random() * 3000) + 1500,
          simulations: Math.floor(Math.random() * 2000) + 1000,
          animations: Math.floor(Math.random() * 1500) + 800,
          assessments: Math.floor(Math.random() * 2500) + 1200,
          explanations: Math.floor(Math.random() * 3000) + 1500,
        },
        byDomain: {
          physics: Math.floor(Math.random() * 1500) + 800,
          chemistry: Math.floor(Math.random() * 1200) + 600,
          biology: Math.floor(Math.random() * 1000) + 500,
          mathematics: Math.floor(Math.random() * 2000) + 1000,
          computerScience: Math.floor(Math.random() * 1300) + 700,
          economics: Math.floor(Math.random() * 800) + 400,
          medicine: Math.floor(Math.random() * 900) + 450,
          general: Math.floor(Math.random() * 1000) + 500,
        },
        qualityDistribution: {
          excellent: Math.floor(Math.random() * 3000) + 2000,
          good: Math.floor(Math.random() * 4000) + 2500,
          fair: Math.floor(Math.random() * 1500) + 800,
          poor: Math.floor(Math.random() * 200) + 100,
        },
      });

      setInfrastructureStatus({
        aiModels: {
          openai: {
            status: Math.random() > 0.1 ? "healthy" : "degraded",
            latency: Math.floor(Math.random() * 2000) + 500,
            successRate: Math.random() * 10 + 90,
          },
          ollama: {
            status: Math.random() > 0.05 ? "healthy" : "degraded",
            latency: Math.floor(Math.random() * 3000) + 1000,
            successRate: Math.random() * 5 + 95,
          },
          claude: {
            status: Math.random() > 0.15 ? "healthy" : "degraded",
            latency: Math.floor(Math.random() * 1500) + 800,
            successRate: Math.random() * 8 + 92,
          },
        },
        databases: {
          postgres: {
            status: "healthy",
            connections: Math.floor(Math.random() * 50) + 20,
            queryTime: Math.floor(Math.random() * 100) + 10,
          },
          redis: {
            status: "healthy",
            memory: Math.floor(Math.random() * 70) + 20,
            hitRate: Math.random() * 20 + 80,
          },
        },
        storage: {
          s3: {
            status: "healthy",
            size: Math.floor(Math.random() * 100) + 50,
            objects: Math.floor(Math.random() * 50000) + 10000,
          },
          local: {
            status: "healthy",
            size: Math.floor(Math.random() * 20) + 5,
            objects: Math.floor(Math.random() * 5000) + 1000,
          },
        },
      });

      setTemplateStats({
        totalTemplates: Math.floor(Math.random() * 100) + 50,
        byDomain: {
          physics: Math.floor(Math.random() * 20) + 10,
          chemistry: Math.floor(Math.random() * 15) + 8,
          biology: Math.floor(Math.random() * 12) + 6,
          mathematics: Math.floor(Math.random() * 25) + 15,
          computerScience: Math.floor(Math.random() * 18) + 10,
        },
        usageStats: {
          mostUsed: "Pendulum Simulation",
          leastUsed: "Economics Supply-Demand",
          avgUsage: Math.floor(Math.random() * 100) + 50,
        },
        recentTemplates: [
          {
            id: "1",
            name: "Projectile Motion",
            domain: "physics",
            usageCount: 245,
            lastUsed: "2 hours ago",
          },
          {
            id: "2",
            name: "Chemical Reactions",
            domain: "chemistry",
            usageCount: 189,
            lastUsed: "5 hours ago",
          },
          {
            id: "3",
            name: "Algorithm Sorting",
            domain: "computerScience",
            usageCount: 156,
            lastUsed: "1 day ago",
          },
        ],
      });

      setAutomatedJobs([
        {
          id: "1",
          name: "Physics Content Generation",
          description:
            "Generate physics simulations and examples for grades 9-12",
          schedule: "0 2 * * *", // Daily at 2 AM
          status: Math.random() > 0.2 ? "active" : "paused",
          lastRun: "2 hours ago",
          nextRun: "22 hours from now",
          totalRuns: Math.floor(Math.random() * 100) + 50,
          successRate: Math.random() * 10 + 90,
          averageDuration: Math.floor(Math.random() * 300) + 120,
          config: {
            domains: ["physics"],
            gradeLevels: ["Grade 9", "Grade 10", "Grade 11", "Grade 12"],
            contentTypes: ["simulations", "examples", "animations"],
            batchSize: 10,
            aiProvider: "openai",
          },
        },
        {
          id: "2",
          name: "Mathematics Weekly Batch",
          description: "Generate math content for all grade levels",
          schedule: "0 3 * * 1", // Weekly on Monday at 3 AM
          status: Math.random() > 0.3 ? "active" : "paused",
          lastRun: "3 days ago",
          nextRun: "4 days from now",
          totalRuns: Math.floor(Math.random() * 50) + 25,
          successRate: Math.random() * 15 + 85,
          averageDuration: Math.floor(Math.random() * 450) + 200,
          config: {
            domains: ["mathematics"],
            gradeLevels: [
              "Grade 1",
              "Grade 2",
              "Grade 3",
              "Grade 4",
              "Grade 5",
              "Grade 6",
              "Grade 7",
              "Grade 8",
            ],
            contentTypes: ["examples", "assessments"],
            batchSize: 20,
            aiProvider: "claude",
          },
        },
      ]);

      setJobQueue({
        pending: Math.floor(Math.random() * 10) + 5,
        running: Math.floor(Math.random() * 3) + 1,
        completed: Math.floor(Math.random() * 100) + 50,
        failed: Math.floor(Math.random() * 5) + 1,
        averageWaitTime: Math.floor(Math.random() * 300) + 60,
      });

      setGenerationMetrics({
        jobsPerHour: Math.floor(Math.random() * 20) + 5,
        successRate: Math.random() * 10 + 90,
        averageDuration: Math.floor(Math.random() * 200) + 100,
        contentGenerated: Math.floor(Math.random() * 500) + 200,
        errorsByType: {
          AI_TIMEOUT: Math.floor(Math.random() * 5) + 1,
          TEMPLATE_NOT_FOUND: Math.floor(Math.random() * 3) + 1,
          VALIDATION_FAILED: Math.floor(Math.random() * 2) + 1,
        },
      });
    }, 3000);

    return () => clearInterval(interval);
  }, []);

  // Navigation handlers
  const navigateToView = useCallback(
    (view: ContentStudioView) => {
      setCurrentView(view);
      setSearchParams({ view });
    },
    [setSearchParams],
  );

  // Experience creation handler
  const handleExperienceCreated = useCallback(
    async (request: any) => {
      setIsGenerating(true);
      try {
        const result = await contentStudioApi.generateContent(request);
        setCurrentExperience(result.experience);
        setCurrentView("edit");
        setSearchParams({ id: result.experience.id, view: "edit" });
      } catch (error) {
        console.error("Failed to create experience:", error);
      } finally {
        setIsGenerating(false);
      }
    },
    [contentStudioApi, setSearchParams],
  );

  // Render navigation tabs
  const renderNavigation = () => (
    <div className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
      <div className="flex space-x-8 px-6">
        {navigationTabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => navigateToView(tab.id as ContentStudioView)}
            className={`flex items-center gap-2 py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
              currentView === tab.id
                ? "border-purple-500 text-purple-600 dark:text-purple-400"
                : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300"
            }`}
          >
            <tab.icon className="h-5 w-5" />
            <span>{tab.name}</span>
          </button>
        ))}
      </div>
    </div>
  );

  // Render dashboard view with AI intelligence
  const renderDashboard = () => (
    <div className="space-y-6">
      {/* AI-Enhanced Smart Dashboard */}
      <SmartDashboard
        contentData={[]} // Would pass actual content data
        onInsightAction={(action, data) => {
          // Handle AI insight actions
          console.log("AI Insight Action:", action, data);
          if (action === "create") {
            navigateToView("create");
          } else if (action === "analyze") {
            navigateToView("analytics");
          } else if (action === "optimize") {
            navigateToView("automation");
          }
        }}
      />

      {/* Traditional dashboard metrics as fallback */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                Total Content
              </p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {contentStats?.totalContent || "--"}
              </p>
            </div>
            <FileText className="h-8 w-8 text-blue-500" />
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                AI Models
              </p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                3 Active
              </p>
            </div>
            <Cpu className="h-8 w-8 text-blue-500" />
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                Templates
              </p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {templateStats?.totalTemplates || "--"}
              </p>
            </div>
            <FileText className="h-8 w-8 text-green-500" />
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                System Health
              </p>
              <p className="text-2xl font-bold text-green-600">98%</p>
            </div>
            <Activity className="h-8 w-8 text-green-500" />
          </div>
        </div>
      </div>
    </div>
  );

  // Render infrastructure view
  const renderInfrastructure = () => (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* AI Models */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <Cpu className="h-5 w-5" />
            AI Models
          </h3>
          <div className="space-y-4">
            {infrastructureStatus?.aiModels &&
              Object.entries(infrastructureStatus.aiModels).map(
                ([model, status]) => (
                  <div key={model} className="border-l-4 border-green-500 pl-4">
                    <div className="flex items-center justify-between">
                      <span className="font-medium capitalize">{model}</span>
                      <span
                        className={`px-2 py-1 text-xs rounded-full ${
                          status.status === "healthy"
                            ? "bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400"
                            : "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400"
                        }`}
                      >
                        {status.status}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      Latency: {status.latency}ms | Success:{" "}
                      {status.successRate.toFixed(1)}%
                    </div>
                  </div>
                ),
              )}
          </div>
        </div>

        {/* Databases */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <Database className="h-5 w-5" />
            Databases
          </h3>
          <div className="space-y-4">
            {infrastructureStatus?.databases &&
              Object.entries(infrastructureStatus.databases).map(
                ([db, status]) => (
                  <div key={db} className="border-l-4 border-blue-500 pl-4">
                    <div className="flex items-center justify-between">
                      <span className="font-medium capitalize">{db}</span>
                      <span className="px-2 py-1 text-xs rounded-full bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400">
                        {status.status}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      {db === "postgres"
                        ? `Connections: ${status.connections} | Query: ${status.queryTime}ms`
                        : `Memory: ${status.memory}% | Hit Rate: ${status.hitRate.toFixed(1)}%`}
                    </div>
                  </div>
                ),
              )}
          </div>
        </div>

        {/* Storage */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4 flex items-center gap-2">
            <Layers className="h-5 w-5" />
            Storage
          </h3>
          <div className="space-y-4">
            {infrastructureStatus?.storage &&
              Object.entries(infrastructureStatus.storage).map(
                ([storage, status]) => (
                  <div
                    key={storage}
                    className="border-l-4 border-purple-500 pl-4"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium capitalize">{storage}</span>
                      <span className="px-2 py-1 text-xs rounded-full bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400">
                        {status.status}
                      </span>
                    </div>
                    <div className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                      Size: {status.size}GB | Objects:{" "}
                      {status.objects.toLocaleString()}
                    </div>
                  </div>
                ),
              )}
          </div>
        </div>
      </div>
    </div>
  );

  // Render analytics view
  const renderAnalytics = () => (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Content by Domain */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Content by Domain
          </h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart
              data={
                contentStats
                  ? Object.entries(contentStats.byDomain).map(
                      ([domain, count]) => ({
                        domain:
                          domain.charAt(0).toUpperCase() + domain.slice(1),
                        count,
                      }),
                    )
                  : []
              }
            >
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="domain" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="count" fill="#8b5cf6" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Quality Distribution */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Quality Distribution
          </h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={
                  contentStats
                    ? [
                        {
                          name: "Excellent",
                          value: contentStats.qualityDistribution.excellent,
                        },
                        {
                          name: "Good",
                          value: contentStats.qualityDistribution.good,
                        },
                        {
                          name: "Fair",
                          value: contentStats.qualityDistribution.fair,
                        },
                        {
                          name: "Poor",
                          value: contentStats.qualityDistribution.poor,
                        },
                      ]
                    : []
                }
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, percent }) =>
                  `${name} ${(percent * 100).toFixed(0)}%`
                }
                outerRadius={80}
                fill="#8884d8"
                dataKey="value"
              >
                <Cell fill="#10b981" />
                <Cell fill="#3b82f6" />
                <Cell fill="#f59e0b" />
                <Cell fill="#ef4444" />
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );

  // Render templates view
  const renderTemplates = () => (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Recent Templates
            </h3>
            <div className="space-y-4">
              {templateStats?.recentTemplates.map((template) => (
                <div
                  key={template.id}
                  className="flex items-center justify-between p-4 border border-gray-200 dark:border-gray-700 rounded-lg"
                >
                  <div>
                    <h4 className="font-medium text-gray-900 dark:text-white">
                      {template.name}
                    </h4>
                    <p className="text-sm text-gray-600 dark:text-gray-400">
                      {template.domain} • Used {template.usageCount} times
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-sm text-gray-500 dark:text-gray-400">
                      {template.lastUsed}
                    </p>
                    <Button variant="outline" size="sm">
                      Use Template
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div>
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Template Stats
            </h3>
            <div className="space-y-4">
              <div>
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                  Total Templates
                </p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">
                  {templateStats?.totalTemplates}
                </p>
              </div>
              <div>
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                  Most Used
                </p>
                <p className="text-lg font-medium text-gray-900 dark:text-white">
                  {templateStats?.usageStats.mostUsed}
                </p>
              </div>
              <div>
                <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                  Avg Usage
                </p>
                <p className="text-lg font-medium text-gray-900 dark:text-white">
                  {templateStats?.usageStats.avgUsage} times
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );

  // Render create content view (simplified version of original)
  const renderCreateContent = () => (
    <div className="space-y-6">
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Generate Comprehensive Content
        </h3>
        <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
          Create comprehensive educational content with real-world use cases,
          practice worksheets, and quizzes.
        </p>

        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
              Topic
            </label>
            <input
              type="text"
              value={generationRequest.topic}
              onChange={(e) =>
                setGenerationRequest({
                  ...generationRequest,
                  topic: e.target.value,
                })
              }
              className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
              placeholder="e.g., Photosynthesis, Fractions, World War II"
            />
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Grade Level
              </label>
              <select
                value={generationRequest.gradeLevel}
                onChange={(e) =>
                  setGenerationRequest({
                    ...generationRequest,
                    gradeLevel: e.target.value,
                  })
                }
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
              >
                <option value="">Select grade level</option>
                <option value="Grade 1">Grade 1</option>
                <option value="Grade 2">Grade 2</option>
                <option value="Grade 3">Grade 3</option>
                <option value="Grade 4">Grade 4</option>
                <option value="Grade 5">Grade 5</option>
                <option value="Grade 6">Grade 6</option>
                <option value="Grade 7">Grade 7</option>
                <option value="Grade 8">Grade 8</option>
                <option value="Grade 9">Grade 9</option>
                <option value="Grade 10">Grade 10</option>
                <option value="Grade 11">Grade 11</option>
                <option value="Grade 12">Grade 12</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                Subject
              </label>
              <select
                value={generationRequest.subject}
                onChange={(e) =>
                  setGenerationRequest({
                    ...generationRequest,
                    subject: e.target.value,
                  })
                }
                className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-transparent dark:bg-gray-700 dark:text-white"
              >
                <option value="">Select subject</option>
                <option value="Mathematics">Mathematics</option>
                <option value="Science">Science</option>
                <option value="English">English</option>
                <option value="History">History</option>
                <option value="Geography">Geography</option>
                <option value="Physics">Physics</option>
                <option value="Chemistry">Chemistry</option>
                <option value="Biology">Biology</option>
                <option value="Computer Science">Computer Science</option>
              </select>
            </div>
          </div>

          <div className="flex justify-end gap-2">
            <Button
              variant="outline"
              onClick={() => navigateToView("dashboard")}
            >
              Cancel
            </Button>
            <Button
              onClick={() => handleExperienceCreated(generationRequest)}
              disabled={
                !generationRequest.topic ||
                !generationRequest.gradeLevel ||
                !generationRequest.subject ||
                isGenerating
              }
              className="bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600"
            >
              {isGenerating ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                  Generating...
                </>
              ) : (
                <>
                  <Sparkles className="h-4 w-4 mr-2" />
                  Generate Content
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );

  // Render automation view
  const renderAutomation = () => (
    <div className="space-y-6">
      {/* Automation Overview Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                Active Jobs
              </p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {automatedJobs.filter((job) => job.status === "active").length}
              </p>
            </div>
            <Play className="h-8 w-8 text-green-500" />
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                Jobs/Hour
              </p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {generationMetrics?.jobsPerHour || "--"}
              </p>
            </div>
            <Clock className="h-8 w-8 text-blue-500" />
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                Success Rate
              </p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {generationMetrics?.successRate.toFixed(1) || "--"}%
              </p>
            </div>
            <CheckCircle className="h-8 w-8 text-green-500" />
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-600 dark:text-gray-400">
                Queue Size
              </p>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {jobQueue?.pending || "--"}
              </p>
            </div>
            <Activity className="h-8 w-8 text-orange-500" />
          </div>
        </div>
      </div>

      {/* Job Queue Status */}
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Job Queue Status
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <div className="text-center p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
            <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">
              {jobQueue?.pending || 0}
            </p>
            <p className="text-sm text-blue-600 dark:text-blue-400">Pending</p>
          </div>
          <div className="text-center p-4 bg-green-50 dark:bg-green-900/20 rounded-lg">
            <p className="text-2xl font-bold text-green-600 dark:text-green-400">
              {jobQueue?.running || 0}
            </p>
            <p className="text-sm text-green-600 dark:text-green-400">
              Running
            </p>
          </div>
          <div className="text-center p-4 bg-gray-50 dark:bg-gray-900/20 rounded-lg">
            <p className="text-2xl font-bold text-gray-600 dark:text-gray-400">
              {jobQueue?.completed || 0}
            </p>
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Completed
            </p>
          </div>
          <div className="text-center p-4 bg-red-50 dark:bg-red-900/20 rounded-lg">
            <p className="text-2xl font-bold text-red-600 dark:text-red-400">
              {jobQueue?.failed || 0}
            </p>
            <p className="text-sm text-red-600 dark:text-red-400">Failed</p>
          </div>
        </div>
      </div>

      {/* Automated Jobs Management */}
      <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
            Automated Jobs
          </h3>
          <Button className="bg-gradient-to-r from-purple-500 to-blue-500 hover:from-purple-600 hover:to-blue-600">
            <Sparkles className="h-4 w-4 mr-2" />
            Create New Job
          </Button>
        </div>
        <div className="space-y-4">
          {automatedJobs.map((job) => (
            <div
              key={job.id}
              className="border border-gray-200 dark:border-gray-700 rounded-lg p-4"
            >
              <div className="flex items-center justify-between mb-3">
                <div>
                  <h4 className="font-medium text-gray-900 dark:text-white">
                    {job.name}
                  </h4>
                  <p className="text-sm text-gray-600 dark:text-gray-400">
                    {job.description}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span
                    className={`px-2 py-1 text-xs rounded-full ${
                      job.status === "active"
                        ? "bg-green-100 text-green-800 dark:bg-green-900/20 dark:text-green-400"
                        : job.status === "paused"
                          ? "bg-yellow-100 text-yellow-800 dark:bg-yellow-900/20 dark:text-yellow-400"
                          : "bg-red-100 text-red-800 dark:bg-red-900/20 dark:text-red-400"
                    }`}
                  >
                    {job.status}
                  </span>
                  <div className="flex items-center gap-1">
                    {job.status === "active" ? (
                      <Pause className="h-4 w-4 text-gray-500 cursor-pointer hover:text-gray-700" />
                    ) : (
                      <Play className="h-4 w-4 text-gray-500 cursor-pointer hover:text-gray-700" />
                    )}
                    <Square className="h-4 w-4 text-red-500 cursor-pointer hover:text-red-700" />
                    <Settings className="h-4 w-4 text-gray-500 cursor-pointer hover:text-gray-700" />
                  </div>
                </div>
              </div>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                <div>
                  <p className="text-gray-500 dark:text-gray-400">Schedule</p>
                  <p className="font-medium">{job.schedule}</p>
                </div>
                <div>
                  <p className="text-gray-500 dark:text-gray-400">Last Run</p>
                  <p className="font-medium">{job.lastRun}</p>
                </div>
                <div>
                  <p className="text-gray-500 dark:text-gray-400">
                    Success Rate
                  </p>
                  <p className="font-medium">{job.successRate.toFixed(1)}%</p>
                </div>
                <div>
                  <p className="text-gray-500 dark:text-gray-400">Total Runs</p>
                  <p className="font-medium">{job.totalRuns}</p>
                </div>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {job.config.domains.map((domain) => (
                  <span
                    key={domain}
                    className="px-2 py-1 bg-purple-100 text-purple-800 dark:bg-purple-900/20 dark:text-purple-400 text-xs rounded"
                  >
                    {domain}
                  </span>
                ))}
                {job.config.contentTypes.map((type) => (
                  <span
                    key={type}
                    className="px-2 py-1 bg-blue-100 text-blue-800 dark:bg-blue-900/20 dark:text-blue-400 text-xs rounded"
                  >
                    {type}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Performance Metrics */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Performance Metrics
          </h3>
          <div className="space-y-3">
            <div className="flex justify-between">
              <span className="text-gray-600 dark:text-gray-400">
                Average Duration
              </span>
              <span className="font-medium">
                {generationMetrics?.averageDuration || "--"}s
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600 dark:text-gray-400">
                Content Generated
              </span>
              <span className="font-medium">
                {generationMetrics?.contentGenerated || "--"}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-gray-600 dark:text-gray-400">
                Average Wait Time
              </span>
              <span className="font-medium">
                {jobQueue?.averageWaitTime || "--"}s
              </span>
            </div>
          </div>
        </div>
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Error Breakdown
          </h3>
          <div className="space-y-3">
            {generationMetrics?.errorsByType &&
              Object.entries(generationMetrics.errorsByType).map(
                ([errorType, count]) => (
                  <div key={errorType} className="flex justify-between">
                    <span className="text-gray-600 dark:text-gray-400">
                      {errorType.replace("_", " ")}
                    </span>
                    <span className="font-medium text-red-600">{count}</span>
                  </div>
                ),
              )}
          </div>
        </div>
      </div>
    </div>
  );

  // Render main content based on current view
  const renderContent = () => {
    switch (currentView) {
      case "dashboard":
        return renderDashboard();
      case "infrastructure":
        return renderInfrastructure();
      case "analytics":
        return renderAnalytics();
      case "templates":
        return renderTemplates();
      case "automation":
        return renderAutomation();
      case "create":
        return renderCreateContent();
      case "edit":
        return (
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Edit Experience
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              Experience editing would be implemented here. For now, this is a
              placeholder.
            </p>
            <div className="mt-4">
              <Button onClick={() => navigateToView("dashboard")}>
                Back to Dashboard
              </Button>
            </div>
          </div>
        );
      case "validate":
        return (
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Validation
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              Content validation would be implemented here. For now, this is a
              placeholder.
            </p>
            <div className="mt-4">
              <Button onClick={() => navigateToView("dashboard")}>
                Back to Dashboard
              </Button>
            </div>
          </div>
        );
      default:
        return renderDashboard();
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      {/* AI Assistant - Always Available */}
      <AIAssistant
        initialContext={currentView}
        onActionSuggestion={(action) => {
          console.log("AI Action Suggestion:", action);
          // Handle AI suggestions
        }}
        onContentGenerated={(content) => {
          console.log("AI Generated Content:", content);
          // Handle AI-generated content
        }}
      />

      {/* Header */}
      <div className="bg-white dark:bg-gray-800 shadow-sm border-b border-gray-200 dark:border-gray-700">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-6">
            <div>
              <h1 className="text-3xl font-bold text-gray-900 dark:text-white">
                Content Studio
              </h1>
              <p className="mt-1 text-sm text-gray-500">
                AI-powered content creation and management
              </p>
            </div>
            <div className="flex items-center space-x-4">
              <div className="flex items-center space-x-2">
                <div className="w-3 h-3 bg-green-500 rounded-full animate-pulse"></div>
                <span className="text-sm text-gray-600">AI Active</span>
              </div>
              <div className="text-sm text-gray-600">
                <span className="font-semibold">98%</span> Success Rate
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      {renderNavigation()}

      {/* Main Content */}
      <div className="p-6">{renderContent()}</div>
    </div>
  );
}
