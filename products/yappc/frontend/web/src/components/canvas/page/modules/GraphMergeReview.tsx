/**
 * GraphMergeReview Module
 *
 * @doc.type component
 * @doc.purpose Review artifact graph merge conflicts
 * @doc.layer product
 * @doc.pattern Widget
 */

import React from 'react';
import { GitBranch, CheckCircle, XCircle, AlertCircle, RefreshCw } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { Typography } from '@ghatana/design-system';
import type { ArtifactGraphMergeReviewResult } from '../../../../services/canvas/commands/ArtifactGraphMergeReviewService';
import type { PageArtifactGraphSnapshot } from '../pageArtifactDocument';

export type ArtifactGraphMergeReviewStatus = 'required' | 'running' | 'passed' | 'conflicts' | 'failed';

export interface ArtifactGraphMergeReviewState {
  readonly artifactId: string;
  readonly graph: PageArtifactGraphSnapshot;
  readonly status: ArtifactGraphMergeReviewStatus;
  readonly attemptedAt?: string;
  readonly result?: ArtifactGraphMergeReviewResult;
  readonly error?: string;
}

export interface GraphMergeReviewProps {
  readonly reviewState: ArtifactGraphMergeReviewState | null;
  readonly onRunReview: () => Promise<void>;
  readonly canEdit: boolean;
}

export function GraphMergeReview({
  reviewState,
  onRunReview,
  canEdit,
}: GraphMergeReviewProps): React.JSX.Element | null {
  if (!reviewState) {
    return null;
  }

  const { status, result, error, attemptedAt } = reviewState;

  const getStatusIcon = () => {
    switch (status) {
      case 'required':
        return <GitBranch className="h-5 w-5 text-blue-600 dark:text-blue-400" />;
      case 'running':
        return <RefreshCw className="h-5 w-5 text-blue-600 dark:text-blue-400 animate-spin" />;
      case 'passed':
        return <CheckCircle className="h-5 w-5 text-green-600 dark:text-green-400" />;
      case 'conflicts':
        return <XCircle className="h-5 w-5 text-red-600 dark:text-red-400" />;
      case 'failed':
        return <AlertCircle className="h-5 w-5 text-red-600 dark:text-red-400" />;
    }
  };

  const getStatusColor = () => {
    switch (status) {
      case 'required':
        return 'border-blue-200 bg-blue-50 dark:border-blue-900/50 dark:bg-blue-950/20';
      case 'running':
        return 'border-blue-200 bg-blue-50 dark:border-blue-900/50 dark:bg-blue-950/20';
      case 'passed':
        return 'border-green-200 bg-green-50 dark:border-green-900/50 dark:bg-green-950/20';
      case 'conflicts':
        return 'border-red-200 bg-red-50 dark:border-red-900/50 dark:bg-red-950/20';
      case 'failed':
        return 'border-red-200 bg-red-50 dark:border-red-900/50 dark:bg-red-950/20';
    }
  };

  const getStatusText = () => {
    switch (status) {
      case 'required':
        return 'Graph merge review required';
      case 'running':
        return 'Running graph merge review...';
      case 'passed':
        return 'Graph merge review passed';
      case 'conflicts':
        return 'Graph merge conflicts detected';
      case 'failed':
        return 'Graph merge review failed';
    }
  };

  return (
    <div className={`rounded-lg border p-4 ${getStatusColor()}`}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          {getStatusIcon()}
          <Typography variant="h3" className="text-base font-semibold">
            Artifact Graph Merge Review
          </Typography>
        </div>
        {status === 'required' && canEdit && (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={onRunReview}
            className="flex items-center gap-2"
          >
            <RefreshCw className="h-4 w-4" />
            Run Review
          </Button>
        )}
      </div>

      <Typography variant="body1" className="mb-3 text-sm">
        {getStatusText()}
      </Typography>

      {attemptedAt && (
        <Typography variant="body2" className="mb-3 text-xs text-gray-500 dark:text-gray-400">
          Last attempted: {new Date(attemptedAt).toLocaleString()}
        </Typography>
      )}

      {status === 'conflicts' && result && (
        <div className="mt-3 space-y-2">
          <Typography variant="body2" className="font-medium">
            {result.conflictCount} conflict{result.conflictCount !== 1 ? 's' : ''} detected
          </Typography>
          {result.conflicts && result.conflicts.length > 0 && (
            <ul className="ml-4 list-disc space-y-1 text-sm">
              {result.conflicts.map((conflict, index) => (
                <li key={index} className="text-gray-700 dark:text-gray-300">
                  {typeof conflict === 'object' && conflict !== null && 'description' in conflict
                    ? String(conflict.description)
                    : 'Unknown conflict'}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {status === 'failed' && error && (
        <div className="mt-3 rounded border border-red-200 bg-red-100 p-2 text-sm text-red-800 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-200">
          {error}
        </div>
      )}

      {status === 'passed' && result && (
        <div className="mt-3 text-sm text-green-700 dark:text-green-300">
          <CheckCircle className="mr-1 inline h-4 w-4" />
          No conflicts detected. Graph can be safely merged.
        </div>
      )}
    </div>
  );
}
