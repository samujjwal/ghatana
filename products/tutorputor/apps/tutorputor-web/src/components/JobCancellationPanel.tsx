/**
 * Job Cancellation Panel
 *
 * UI for viewing and cancelling active jobs including:
 * - List of user's active jobs
 * - Progress indicators
 * - Cancel action with confirmation
 * - Job status notifications
 *
 * @doc.type component
 * @doc.purpose Allow users to manage and cancel active jobs
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState, useEffect } from "react";
import { X, AlertCircle, Loader2, CheckCircle2, Clock, Ban } from "lucide-react";
import { Button, Card, Badge, AlertDialog } from "@ghatana/design-system";

export interface ActiveJob {
  jobId: string;
  jobType: string;
  status: "pending" | "processing" | "stuck";
  progress: number;
  startedAt: Date;
  estimatedCompletion?: Date;
  canCancel: boolean;
}

interface JobCancellationPanelProps {
  jobs: ActiveJob[];
  onCancelJob: (jobId: string) => Promise<void>;
  onRefresh: () => Promise<void>;
  isLoading?: boolean;
}

export function JobCancellationPanel({
  jobs,
  onCancelJob,
  onRefresh,
  isLoading = false,
}: JobCancellationPanelProps) {
  const [cancellingJobId, setCancellingJobId] = useState<string | null>(null);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const [selectedJob, setSelectedJob] = useState<ActiveJob | null>(null);
  const [lastRefresh, setLastRefresh] = useState<Date>(new Date());

  // Auto-refresh every 30 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      onRefresh().then(() => setLastRefresh(new Date()));
    }, 30000);

    return () => clearInterval(interval);
  }, [onRefresh]);

  const handleCancelClick = (job: ActiveJob) => {
    setSelectedJob(job);
    setShowConfirmDialog(true);
  };

  const handleConfirmCancel = async () => {
    if (!selectedJob) return;

    setCancellingJobId(selectedJob.jobId);
    setShowConfirmDialog(false);

    try {
      await onCancelJob(selectedJob.jobId);
    } finally {
      setCancellingJobId(null);
      setSelectedJob(null);
    }
  };

  const getStatusIcon = (status: ActiveJob["status"]) => {
    switch (status) {
      case "processing":
        return <Loader2 className="w-4 h-4 animate-spin text-blue-500" />;
      case "pending":
        return <Clock className="w-4 h-4 text-yellow-500" />;
      case "stuck":
        return <AlertCircle className="w-4 h-4 text-red-500" />;
      default:
        return null;
    }
  };

  const getStatusBadge = (status: ActiveJob["status"]) => {
    const variants: Record<ActiveJob["status"], { variant: "default" | "secondary" | "destructive" | "outline"; label: string }> = {
      processing: { variant: "default", label: "Processing" },
      pending: { variant: "secondary", label: "Pending" },
      stuck: { variant: "destructive", label: "Stuck" },
    };

    const { variant, label } = variants[status];
    return <Badge variant={variant}>{label}</Badge>;
  };

  if (jobs.length === 0) {
    return (
      <Card className="p-8">
        <div className="text-center space-y-4">
          <CheckCircle2 className="w-12 h-12 mx-auto text-green-500" />
          <div>
            <h3 className="text-lg font-semibold text-gray-900">No Active Jobs</h3>
            <p className="text-gray-500 mt-1">
              You don't have any jobs running right now.
            </p>
          </div>
          <Button variant="outline" onClick={onRefresh} disabled={isLoading}>
            {isLoading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
            Refresh
          </Button>
        </div>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-gray-900">Active Jobs</h2>
          <p className="text-sm text-gray-500">
            {jobs.length} job{jobs.length !== 1 ? "s" : ""} running • Refreshed{" "}
            {lastRefresh.toLocaleTimeString()}
          </p>
        </div>
        <Button variant="outline" onClick={onRefresh} disabled={isLoading}>
          {isLoading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
          Refresh
        </Button>
      </div>

      <div className="space-y-3">
        {jobs.map((job) => (
          <Card key={job.jobId} className="p-4">
            <div className="flex items-start justify-between">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  {getStatusIcon(job.status)}
                  <span className="font-medium text-gray-900 truncate">
                    {job.jobType}
                  </span>
                  {getStatusBadge(job.status)}
                </div>

                <div className="text-sm text-gray-500 space-y-1">
                  <p>Started {job.startedAt.toLocaleString()}</p>
                  {job.estimatedCompletion && (
                    <p>Est. completion: {job.estimatedCompletion.toLocaleString()}</p>
                  )}
                  <p className="font-mono text-xs">ID: {job.jobId}</p>
                </div>

                {/* Progress Bar */}
                <div className="mt-3">
                  <div className="flex justify-between text-xs text-gray-500 mb-1">
                    <span>Progress</span>
                    <span>{job.progress}%</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className={`h-2 rounded-full transition-all ${
                        job.status === "stuck"
                          ? "bg-red-500"
                          : job.status === "processing"
                          ? "bg-blue-500"
                          : "bg-yellow-500"
                      }`}
                      style={{ width: `${job.progress}%` }}
                    />
                  </div>
                </div>
              </div>

              {/* Cancel Button */}
              {job.canCancel && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="ml-4 text-red-600 hover:text-red-700 hover:bg-red-50"
                  onClick={() => handleCancelClick(job)}
                  disabled={cancellingJobId === job.jobId}
                >
                  {cancellingJobId === job.jobId ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <>
                      <Ban className="w-4 h-4 mr-1" />
                      Cancel
                    </>
                  )}
                </Button>
              )}
            </div>

            {/* Stuck Job Warning */}
            {job.status === "stuck" && (
              <div className="mt-3 p-3 bg-red-50 border border-red-200 rounded-md">
                <div className="flex items-start gap-2">
                  <AlertCircle className="w-4 h-4 text-red-500 mt-0.5" />
                  <div className="text-sm">
                    <p className="font-medium text-red-800">
                      This job appears to be stuck
                    </p>
                    <p className="text-red-600">
                      Our team has been notified. You can cancel this job and try again.
                    </p>
                  </div>
                </div>
              </div>
            )}
          </Card>
        ))}
      </div>

      {/* Confirmation Dialog */}
      <AlertDialog open={showConfirmDialog} onOpenChange={setShowConfirmDialog}>
        <AlertDialog.Content>
          <AlertDialog.Header>
            <AlertDialog.Title className="flex items-center gap-2">
              <X className="w-5 h-5 text-red-500" />
              Cancel Job?
            </AlertDialog.Title>
            <AlertDialog.Description className="space-y-2">
              <p>
                Are you sure you want to cancel{" "}
                <strong>{selectedJob?.jobType}</strong>?
              </p>
              <p className="text-sm text-gray-500">
                This action cannot be undone. Any progress will be lost.
              </p>
            </AlertDialog.Description>
          </AlertDialog.Header>
          <AlertDialog.Footer>
            <Button variant="outline" onClick={() => setShowConfirmDialog(false)}>
              Keep Running
            </Button>
            <Button variant="destructive" onClick={handleConfirmCancel}>
              <Ban className="w-4 h-4 mr-1" />
              Yes, Cancel Job
            </Button>
          </AlertDialog.Footer>
        </AlertDialog.Content>
      </AlertDialog>
    </div>
  );
}

// Hook for managing job cancellation
export function useJobCancellation(tenantId: string, userId: string) {
  const [jobs, setJobs] = useState<ActiveJob[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const fetchJobs = async () => {
    setIsLoading(true);
    try {
      // This would be replaced with actual API call
      const response = await fetch(`/api/jobs/active?tenantId=${tenantId}&userId=${userId}`);
      const data = await response.json();
      setJobs(data.jobs);
    } catch (error) {
      console.error("Failed to fetch jobs:", error);
    } finally {
      setIsLoading(false);
    }
  };

  const cancelJob = async (jobId: string) => {
    try {
      const response = await fetch(`/api/jobs/${jobId}/cancel`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantId, userId }),
      });

      if (!response.ok) {
        throw new Error("Failed to cancel job");
      }

      // Remove cancelled job from list
      setJobs((prev) => prev.filter((j) => j.jobId !== jobId));
    } catch (error) {
      console.error("Failed to cancel job:", error);
      throw error;
    }
  };

  useEffect(() => {
    fetchJobs();
  }, [tenantId, userId]);

  return {
    jobs,
    isLoading,
    refreshJobs: fetchJobs,
    cancelJob,
  };
}
