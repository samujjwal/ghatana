/**
 * RegistryCandidatePanel Module
 *
 * @doc.type component
 * @doc.purpose Display registry candidates promoted from residual islands
 * @doc.layer product
 * @doc.pattern Widget
 */

import React from 'react';
import { ChevronRight, CheckCircle, Clock } from 'lucide-react';
import { Button } from '@ghatana/design-system';
import { Typography } from '@ghatana/design-system';
import type { RegistryCandidatePromotionResponse } from '../../../../services/canvas/commands/RegistryCandidatePromotionService';

export interface RegistryCandidateSummary {
  readonly candidateId: string;
  readonly artifactId: string;
  readonly residualIslandId: string;
  readonly proposedContractName: string;
  readonly status: RegistryCandidatePromotionResponse['status'];
  readonly auditRecordId: string;
  readonly createdAt: string;
}

export interface RegistryCandidatePanelProps {
  readonly candidates: readonly RegistryCandidateSummary[];
}

export function RegistryCandidatePanel({
  candidates,
}: RegistryCandidatePanelProps): React.JSX.Element | null {
  if (candidates.length === 0) {
    return null;
  }

  const getStatusIcon = (status: RegistryCandidatePromotionResponse['status']) => {
    // RegistryCandidateStatus only includes 'NEEDS_REVIEW'
    return <Clock className="h-4 w-4 text-yellow-600 dark:text-yellow-400" />;
  };

  const getStatusColor = (status: RegistryCandidatePromotionResponse['status']) => {
    // RegistryCandidateStatus only includes 'NEEDS_REVIEW'
    return 'border-yellow-200 bg-yellow-50 dark:border-yellow-900/50 dark:bg-yellow-950/20';
  };

  const getStatusText = (status: RegistryCandidatePromotionResponse['status']) => {
    // RegistryCandidateStatus only includes 'NEEDS_REVIEW'
    return 'Pending approval';
  };

  return (
    <div className="rounded-lg border border-purple-200 bg-purple-50 p-4 dark:border-purple-900/50 dark:bg-purple-950/20">
      <div className="mb-3 flex items-center gap-2">
        <ChevronRight className="h-5 w-5 text-purple-600 dark:text-purple-400" />
        <Typography variant="h3" className="text-base font-semibold text-purple-800 dark:text-purple-200">
          Registry Candidates
        </Typography>
      </div>

      <p className="mb-4 text-sm text-purple-700 dark:text-purple-300">
        {candidates.length} residual island{candidates.length !== 1 ? 's have' : ' has'} been promoted to registry candidates for review.
      </p>

      <div className="space-y-3">
        {candidates.map((candidate) => (
          <div
            key={candidate.candidateId}
            className={`rounded border p-3 ${getStatusColor(candidate.status)}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2">
                  <Typography variant="body1" className="font-medium">
                    {candidate.proposedContractName}
                  </Typography>
                  {getStatusIcon(candidate.status)}
                </div>

                <div className="mt-1 text-xs text-gray-600 dark:text-gray-400">
                  <div>Residual Island: {candidate.residualIslandId}</div>
                  <div>Artifact ID: {candidate.artifactId}</div>
                  <div>
                    Created: {new Date(candidate.createdAt).toLocaleString()}
                  </div>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
                  {getStatusText(candidate.status)}
                </span>
              </div>
            </div>

            {candidate.auditRecordId && (
              <div className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                Audit Record: {candidate.auditRecordId}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
