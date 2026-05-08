/**
 * Publish Workflow Panel Component
 *
 * Schema-backed component for managing the publish workflow.
 * Provides UI for review queue management, approval workflow,
 * and publishing controls with proper audit trail.
 *
 * @doc.type component
 * @doc.purpose Manage content publishing workflow
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState } from "react";
import { CheckCircle, Clock, XCircle, User, Calendar, FileText, Send, Eye, ArrowRight } from "lucide-react";
import { Button } from "@ghatana/design-system";

export interface WorkflowStep {
  id: string;
  name: string;
  status: "pending" | "in_progress" | "completed" | "skipped" | "failed";
  completedBy?: string;
  completedAt?: string;
  notes?: string;
}

export interface ReviewComment {
  id: string;
  authorId: string;
  authorName: string;
  content: string;
  timestamp: string;
  status: "open" | "resolved";
}

export interface PublishWorkflow {
  experienceId: string;
  experienceTitle: string;
  currentStatus: "draft" | "in_review" | "approved" | "rejected" | "published";
  workflowSteps: WorkflowStep[];
  reviewers: Array<{
    id: string;
    name: string;
    role: string;
    status: "pending" | "approved" | "rejected";
  }>;
  comments: ReviewComment[];
  scheduledPublishAt?: string;
  autoPublishEnabled: boolean;
  lastModified: string;
  modifiedBy: string;
}

interface PublishWorkflowPanelProps {
  workflow: PublishWorkflow;
  onChange: (workflow: PublishWorkflow) => void;
  readonly?: boolean;
}

const WORKFLOW_STEP_DEFINITIONS = [
  { id: "self_review", name: "Self Review", required: true },
  { id: "peer_review", name: "Peer Review", required: true },
  { id: "sme_review", name: "SME Review", required: false },
  { id: "accessibility_check", name: "Accessibility Check", required: true },
  { id: "final_approval", name: "Final Approval", required: true },
] as const;

export function PublishWorkflowPanel({
  workflow,
  onChange,
  readonly = false,
}: PublishWorkflowPanelProps) {
  const [selectedStep, setSelectedStep] = useState<string | null>(null);
  const [newComment, setNewComment] = useState("");

  const updateWorkflow = (updates: Partial<PublishWorkflow>) => {
    onChange({ ...workflow, ...updates });
  };

  const advanceStep = (stepId: string) => {
    const stepIndex = workflow.workflowSteps.findIndex((s) => s.id === stepId);
    if (stepIndex < 0) return;

    const updatedSteps = [...workflow.workflowSteps];
    updatedSteps[stepIndex] = {
      ...updatedSteps[stepIndex],
      status: "completed",
      completedAt: new Date().toISOString(),
    };

    // Mark next step as in_progress if there is one
    if (stepIndex + 1 < updatedSteps.length) {
      updatedSteps[stepIndex + 1] = {
        ...updatedSteps[stepIndex + 1],
        status: "in_progress",
      };
    }

    updateWorkflow({ workflowSteps: updatedSteps });
  };

  const rejectStep = (stepId: string, reason: string) => {
    const updatedSteps = workflow.workflowSteps.map((step) =>
      step.id === stepId
        ? { ...step, status: "failed" as const, notes: reason }
        : step,
    );
    updateWorkflow({ workflowSteps: updatedSteps, currentStatus: "rejected" });
  };

  const addComment = () => {
    if (!newComment.trim()) return;

    const comment: ReviewComment = {
      id: `comment-${Date.now()}`,
      authorId: "current-user", // Would come from auth context
      authorName: "Current User", // Would come from auth context
      content: newComment,
      timestamp: new Date().toISOString(),
      status: "open",
    };

    updateWorkflow({ comments: [...workflow.comments, comment] });
    setNewComment("");
  };

  const resolveComment = (commentId: string) => {
    updateWorkflow({
      comments: workflow.comments.map((c) =>
        c.id === commentId ? { ...c, status: "resolved" as const } : c,
      ),
    });
  };

  const handlePublish = () => {
    updateWorkflow({ currentStatus: "published" });
  };

  const handleSchedulePublish = (datetime: string) => {
    updateWorkflow({ scheduledPublishAt: datetime, autoPublishEnabled: true });
  };

  const canPublish = workflow.workflowSteps.every(
    (step) => step.status === "completed" || step.status === "skipped",
  );

  const getStatusColor = (status: WorkflowStep["status"]) => {
    switch (status) {
      case "completed":
      case "skipped":
        return "text-green-600";
      case "in_progress":
        return "text-blue-600";
      case "failed":
        return "text-red-600";
      default:
        return "text-gray-400";
    }
  };

  const getStatusIcon = (status: WorkflowStep["status"]) => {
    switch (status) {
      case "completed":
        return <CheckCircle className="w-5 h-5" />;
      case "in_progress":
        return <Clock className="w-5 h-5 animate-spin" />;
      case "failed":
        return <XCircle className="w-5 h-5" />;
      default:
        return <Clock className="w-5 h-5" />;
    }
  };

  const openComments = workflow.comments.filter((c) => c.status === "open");

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold">Publish Workflow</h3>
          <p className="text-sm text-gray-500">
            {workflow.experienceTitle}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span
            className={`px-3 py-1 rounded-full text-sm font-medium ${
              workflow.currentStatus === "published"
                ? "bg-green-100 text-green-700"
                : workflow.currentStatus === "rejected"
                ? "bg-red-100 text-red-700"
                : workflow.currentStatus === "approved"
                ? "bg-blue-100 text-blue-700"
                : "bg-gray-100 text-gray-700"
            }`}
          >
            {workflow.currentStatus.toUpperCase()}
          </span>
        </div>
      </div>

      {/* Workflow Steps */}
      <div className="border rounded-lg p-4">
        <h4 className="font-medium mb-4">Review Steps</h4>
        <div className="space-y-3">
          {workflow.workflowSteps.map((step, index) => (
            <div
              key={step.id}
              className={`flex items-center gap-3 p-3 rounded border ${
                selectedStep === step.id ? "border-purple-500 bg-purple-50" : "border-gray-200"
              }`}
            >
              <div className={getStatusColor(step.status)}>
                {getStatusIcon(step.status)}
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{step.name}</span>
                  {step.completedBy && (
                    <span className="text-xs text-gray-500">
                      by {step.completedBy}
                    </span>
                  )}
                </div>
                {step.completedAt && (
                  <span className="text-xs text-gray-500">
                    {new Date(step.completedAt).toLocaleString()}
                  </span>
                )}
                {step.notes && (
                  <p className="text-sm text-red-600 mt-1">{step.notes}</p>
                )}
              </div>
              {!readonly && step.status === "in_progress" && (
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    onClick={() => advanceStep(step.id)}
                  >
                    <CheckCircle className="w-4 h-4 mr-1" />
                    Approve
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      const reason = prompt("Enter reason for rejection:");
                      if (reason) rejectStep(step.id, reason);
                    }}
                  >
                    <XCircle className="w-4 h-4 mr-1" />
                    Reject
                  </Button>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Reviewers */}
      <div className="border rounded-lg p-4">
        <h4 className="font-medium mb-4">Reviewers</h4>
        <div className="space-y-2">
          {workflow.reviewers.map((reviewer) => (
            <div
              key={reviewer.id}
              className="flex items-center justify-between p-2 bg-gray-50 rounded"
            >
              <div className="flex items-center gap-2">
                <User className="w-4 h-4 text-gray-400" />
                <div>
                  <span className="font-medium">{reviewer.name}</span>
                  <span className="text-xs text-gray-500 ml-2">
                    {reviewer.role}
                  </span>
                </div>
              </div>
              <span
                className={`px-2 py-1 rounded text-xs ${
                  reviewer.status === "approved"
                    ? "bg-green-100 text-green-700"
                    : reviewer.status === "rejected"
                    ? "bg-red-100 text-red-700"
                    : "bg-gray-100 text-gray-700"
                }`}
              >
                {reviewer.status.toUpperCase()}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Comments */}
      <div className="border rounded-lg p-4">
        <div className="flex items-center justify-between mb-4">
          <h4 className="font-medium">
            Comments ({openComments.length} open)
          </h4>
        </div>
        <div className="space-y-3">
          {workflow.comments.map((comment) => (
            <div
              key={comment.id}
              className={`p-3 rounded border ${
                comment.status === "open" ? "border-yellow-300 bg-yellow-50" : "border-gray-200 bg-gray-50"
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-2">
                  <User className="w-4 h-4 text-gray-400 mt-1" />
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{comment.authorName}</span>
                      <span className="text-xs text-gray-500">
                        {new Date(comment.timestamp).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-sm mt-1">{comment.content}</p>
                  </div>
                </div>
                {comment.status === "open" && !readonly && (
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => resolveComment(comment.id)}
                  >
                    <CheckCircle className="w-4 h-4" />
                  </Button>
                )}
              </div>
            </div>
          ))}
        </div>
        {!readonly && (
          <div className="mt-4 flex gap-2">
            <input
              type="text"
              value={newComment}
              onChange={(e) => setNewComment(e.target.value)}
              placeholder="Add a comment..."
              className="flex-1 px-3 py-2 border rounded"
              onKeyPress={(e) => e.key === "Enter" && addComment()}
            />
            <Button onClick={addComment} size="sm">
              Add
            </Button>
          </div>
        )}
      </div>

      {/* Publish Controls */}
      <div className="border rounded-lg p-4 bg-gray-50">
        <h4 className="font-medium mb-4">Publish Actions</h4>
        <div className="flex items-center gap-4">
          {canPublish && workflow.currentStatus !== "published" ? (
            <>
              <Button onClick={handlePublish} size="sm">
                <Send className="w-4 h-4 mr-2" />
                Publish Now
              </Button>
              {!readonly && (
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    const datetime = prompt(
                      "Enter publish datetime (ISO format):",
                      new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
                    );
                    if (datetime) handleSchedulePublish(datetime);
                  }}
                >
                  <Clock className="w-4 h-4 mr-2" />
                  Schedule Publish
                </Button>
              )}
            </>
          ) : (
            <div className="flex items-center gap-2 text-gray-500">
              <XCircle className="w-5 h-5" />
              <span className="text-sm">
                {workflow.currentStatus === "published"
                  ? "Already published"
                  : "Complete all review steps to publish"}
              </span>
            </div>
          )}
        </div>

        {workflow.scheduledPublishAt && (
          <div className="mt-3 flex items-center gap-2 text-sm text-blue-600">
            <Clock className="w-4 h-4" />
            <span>
              Scheduled for {new Date(workflow.scheduledPublishAt).toLocaleString()}
            </span>
            {!readonly && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() =>
                  updateWorkflow({ scheduledPublishAt: undefined, autoPublishEnabled: false })
                }
              >
                Cancel
              </Button>
            )}
          </div>
        )}
      </div>

      {/* Audit Trail */}
      <div className="border rounded-lg p-4">
        <h4 className="font-medium mb-4 flex items-center gap-2">
          <FileText className="w-4 h-4" />
          Audit Trail
        </h4>
        <div className="space-y-2 text-sm">
          <div className="flex items-center gap-2 text-gray-600">
            <Calendar className="w-4 h-4" />
            <span>
              Last modified by {workflow.modifiedBy} on{" "}
              {new Date(workflow.lastModified).toLocaleString()}
            </span>
          </div>
          {workflow.workflowSteps
            .filter((s) => s.completedAt)
            .map((step) => (
              <div key={step.id} className="flex items-center gap-2 text-gray-600">
                <CheckCircle className="w-4 h-4 text-green-600" />
                <span>
                  {step.name} completed{" "}
                  {step.completedBy ? `by ${step.completedBy}` : ""} on{" "}
                  {step.completedAt ? new Date(step.completedAt).toLocaleString() : ""}
                </span>
              </div>
            ))}
        </div>
      </div>
    </div>
  );
}
