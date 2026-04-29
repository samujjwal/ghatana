/**
 * Automation View
 *
 * Automation view for the Unified Content Studio.
 *
 * @doc.type component
 * @doc.purpose Automated content generation management for content studio
 * @doc.layer product
 * @doc.pattern View Component
 */

import { Play, Pause, Square, Settings, Clock, Activity, CheckCircle, Sparkles } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface AutomatedGenerationJob {
  id: string;
  name: string;
  description: string;
  schedule: string;
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

export interface JobQueue {
  pending: number;
  running: number;
  completed: number;
  failed: number;
  averageWaitTime: number;
}

export interface GenerationMetrics {
  jobsPerHour: number;
  successRate: number;
  averageDuration: number;
  contentGenerated: number;
  errorsByType: Record<string, number>;
}

export interface AutomationViewProps {
  automatedJobs: AutomatedGenerationJob[];
  jobQueue: JobQueue | null;
  generationMetrics: GenerationMetrics | null;
}

export function AutomationView({
  automatedJobs,
  jobQueue,
  generationMetrics,
}: AutomationViewProps) {
  return (
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
}
