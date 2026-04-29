/**
 * Dashboard View
 *
 * Dashboard view for the Unified Content Studio.
 *
 * @doc.type component
 * @doc.purpose Dashboard overview for content studio
 * @doc.layer product
 * @doc.pattern View Component
 */

import { FileText, Cpu, Activity } from "lucide-react";
import { SmartDashboard } from "../ai";

export interface ContentCreationStats {
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

export interface TemplateStats {
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

export interface DashboardViewProps {
  contentStats: ContentCreationStats | null;
  templateStats: TemplateStats | null;
  onNavigate: (view: string) => void;
}

export function DashboardView({
  contentStats,
  templateStats,
  onNavigate,
}: DashboardViewProps) {
  return (
    <div className="space-y-6">
      {/* AI-Enhanced Smart Dashboard */}
      <SmartDashboard
        contentData={[]}
        onInsightAction={(action, data) => {
          console.log("AI Insight Action:", action, data);
          if (action === "create") {
            onNavigate("create");
          } else if (action === "analyze") {
            onNavigate("analytics");
          } else if (action === "optimize") {
            onNavigate("automation");
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
}
