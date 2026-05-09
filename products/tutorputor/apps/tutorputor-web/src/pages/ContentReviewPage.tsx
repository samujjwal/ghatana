/**
 * Content Review Page
 *
 * Provides a production-grade UI for reviewing AI-generated content artifacts.
 * Enables reviewers to view, validate, approve, or reject generated content with
 * detailed audit trails and validation feedback.
 *
 * @doc.type component
 * @doc.purpose Content review interface for AI-generated artifacts
 * @doc.layer frontend
 * @doc.pattern Page
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card } from '@ghatana/design-system';
import { Button } from '@ghatana/design-system';
import { Badge } from '@ghatana/design-system';
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';

// ============================================================================
// Types
// ============================================================================

export type ArtifactId = string;

export interface ContentArtifact {
  id: ArtifactId;
  artifactType: 'ASSESSMENT' | 'SIMULATION' | 'ANIMATION' | 'EXAMPLE' | 'EXPLANATION';
  title: string;
  content: string | null;
  status: 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED';
  validationStatus: 'PASSED' | 'FAILED' | 'PENDING';
  validationErrors: string[] | null;
  metadata: Record<string, unknown> | null;
  generatedAt: string;
  generatedBy: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
  reviewComments: string | null;
}

export interface ContentArtifactReview {
  artifactId: ArtifactId;
  approved: boolean;
  comments: string;
  validationOverrides: string[];
}

// ============================================================================
// API Functions
// ============================================================================

async function fetchArtifact(artifactId: ArtifactId): Promise<ContentArtifact> {
  const response = await fetch(`/api/content/artifacts/${artifactId}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch artifact: ${response.statusText}`);
  }
  return response.json();
}

async function submitReview(review: ContentArtifactReview): Promise<void> {
  const response = await fetch(`/api/content/artifacts/${review.artifactId}/review`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(review),
  });
  if (!response.ok) {
    throw new Error(`Failed to submit review: ${response.statusText}`);
  }
}

// ============================================================================
// Component
// ============================================================================

export function ContentReviewPage() {
  const { artifactId } = useParams<{ artifactId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [comments, setComments] = useState('');
  const [validationOverrides, setValidationOverrides] = useState<string[]>([]);

  const {
    data: artifact,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['artifact', artifactId],
    queryFn: () => fetchArtifact(artifactId as ArtifactId),
    enabled: !!artifactId,
  });

  const approveMutation = useMutation({
    mutationFn: () =>
      submitReview({
        artifactId: artifactId as ArtifactId,
        approved: true,
        comments,
        validationOverrides,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['artifact', artifactId] });
      navigate('/dashboard');
    },
  });

  const rejectMutation = useMutation({
    mutationFn: () =>
      submitReview({
        artifactId: artifactId as ArtifactId,
        approved: false,
        comments,
        validationOverrides,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['artifact', artifactId] });
      navigate('/dashboard');
    },
  });

  if (isLoading) {
    return (
      <div className="container mx-auto p-6">
        <div className="animate-pulse">
          <div className="h-8 bg-gray-200 rounded mb-4" />
          <div className="h-64 bg-gray-200 rounded" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container mx-auto p-6">
        <div className="bg-red-50 border border-red-200 text-red-800 p-4 rounded-lg">
          Error loading artifact: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  if (!artifact) {
    return (
      <div className="container mx-auto p-6">
        <div className="bg-red-50 border border-red-200 text-red-800 p-4 rounded-lg">
          Artifact not found
        </div>
      </div>
    );
  }

  const getStatusColor = (status: ContentArtifact['status']) => {
    switch (status) {
      case 'PUBLISHED':
        return 'bg-green-100 text-green-800';
      case 'PENDING_REVIEW':
        return 'bg-yellow-100 text-yellow-800';
      case 'REJECTED':
        return 'bg-red-100 text-red-800';
      case 'DRAFT':
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getValidationStatusColor = (status: ContentArtifact['validationStatus']) => {
    switch (status) {
      case 'PASSED':
        return 'bg-green-100 text-green-800';
      case 'FAILED':
        return 'bg-red-100 text-red-800';
      case 'PENDING':
      default:
        return 'bg-yellow-100 text-yellow-800';
    }
  };

  return (
    <div className="container mx-auto p-6 max-w-6xl">
      <div className="mb-6">
        <Button variant="ghost" onClick={() => navigate(-1)}>
          ← Back
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Content */}
        <div className="lg:col-span-2 space-y-6">
          <Card>
            <div className="p-6">
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h1 className="text-2xl font-bold">{artifact.title}</h1>
                  <div className="flex gap-2 mt-2">
                    <Badge className={getStatusColor(artifact.status)}>{artifact.status}</Badge>
                    <Badge className={getValidationStatusColor(artifact.validationStatus)}>
                      {artifact.validationStatus}
                    </Badge>
                    <Badge variant="outline">{artifact.artifactType}</Badge>
                  </div>
                </div>
              </div>

              <div className="text-sm text-gray-600 mt-4">
                <p>Generated: {new Date(artifact.generatedAt).toLocaleString()}</p>
                <p>Generated by: {artifact.generatedBy}</p>
                {artifact.reviewedAt && (
                  <>
                    <p>Reviewed: {new Date(artifact.reviewedAt).toLocaleString()}</p>
                    <p>Reviewed by: {artifact.reviewedBy}</p>
                  </>
                )}
              </div>

              {artifact.reviewComments && (
                <div className="mt-4 p-4 bg-gray-50 rounded-lg">
                  <p className="text-sm font-semibold mb-1">Previous Review Comments:</p>
                  <p className="text-sm">{artifact.reviewComments}</p>
                </div>
              )}
            </div>
          </Card>

          <Card>
            <div className="p-6">
              <h2 className="text-lg font-semibold mb-4">Content</h2>
              <div className="prose max-w-none">
                {artifact.content ? (
                  <pre className="whitespace-pre-wrap bg-gray-50 p-4 rounded-lg text-sm">
                    {artifact.content}
                  </pre>
                ) : (
                  <p className="text-gray-500 italic">No content available</p>
                )}
              </div>
            </div>
          </Card>

          {artifact.metadata && Object.keys(artifact.metadata).length > 0 && (
            <Card>
              <div className="p-6">
                <h2 className="text-lg font-semibold mb-4">Metadata</h2>
                <pre className="whitespace-pre-wrap bg-gray-50 p-4 rounded-lg text-sm overflow-auto max-h-64">
                  {JSON.stringify(artifact.metadata, null, 2)}
                </pre>
              </div>
            </Card>
          )}
        </div>

        {/* Review Panel */}
        <div className="space-y-6">
          <Card>
            <div className="p-6">
              <h2 className="text-lg font-semibold mb-4">Validation Results</h2>
              {artifact.validationStatus === 'PASSED' ? (
                <div className="bg-green-50 border border-green-200 text-green-800 p-4 rounded-lg">
                  All validation checks passed.
                </div>
              ) : artifact.validationStatus === 'FAILED' && artifact.validationErrors ? (
                <div className="bg-red-50 border border-red-200 text-red-800 p-4 rounded-lg">
                  <div className="font-semibold mb-2">Validation Errors:</div>
                  <ul className="list-disc list-inside text-sm">
                    {artifact.validationErrors.map((error, index) => (
                      <li key={index}>{error}</li>
                    ))}
                  </ul>
                </div>
              ) : (
                <div className="bg-yellow-50 border border-yellow-200 text-yellow-800 p-4 rounded-lg">
                  Validation pending.
                </div>
              )}
            </div>
          </Card>

          {artifact.status === 'PENDING_REVIEW' && (
            <Card>
              <div className="p-6">
                <h2 className="text-lg font-semibold mb-4">Review Actions</h2>
                <div className="space-y-4">
                  <div>
                    <label htmlFor="comments" className="block text-sm font-medium mb-2">
                      Review Comments
                    </label>
                    <textarea
                      id="comments"
                      value={comments}
                      onChange={(e) => setComments(e.target.value)}
                      placeholder="Enter your review comments..."
                      className="w-full min-h-[120px] p-3 border rounded-lg text-sm"
                    />
                  </div>

                  <div className="flex gap-2">
                    <Button
                      onClick={() => approveMutation.mutate()}
                      disabled={approveMutation.isPending || rejectMutation.isPending}
                      className="flex-1"
                    >
                      {approveMutation.isPending ? 'Approving...' : 'Approve'}
                    </Button>
                    <Button
                      variant="destructive"
                      onClick={() => rejectMutation.mutate()}
                      disabled={approveMutation.isPending || rejectMutation.isPending}
                      className="flex-1"
                    >
                      {rejectMutation.isPending ? 'Rejecting...' : 'Reject'}
                    </Button>
                  </div>
                </div>
              </div>
            </Card>
          )}

          {artifact.status !== 'PENDING_REVIEW' && (
            <Card>
              <div className="p-6">
                <h2 className="text-lg font-semibold mb-4">Review Status</h2>
                <div className="bg-gray-50 border border-gray-200 text-gray-800 p-4 rounded-lg">
                  This artifact has already been reviewed and is currently in {artifact.status} status.
                </div>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
