import { AlertCircle, TrendingUp, AlertTriangle, Clock, CheckCircle2, Target, Lightbulb } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { cn } from "../theme";

interface ClaimMasteryState {
  claimId: string;
  masteryScore: number;
  evidenceCount: number;
  status: "not_started" | "developing" | "proficient" | "mastered";
  lastEvidenceAt?: string;
}

interface LearnerActionState {
  currentClaimMastery?: ClaimMasteryState[];
  nextBestLesson?: {
    moduleId: string;
    moduleSlug?: string;
    moduleTitle: string;
    reason: string;
    targetClaimId?: string;
  } | null;
  unresolvedMisconceptions?: Array<{ description: string; claimId?: string }>;
  overdueSpacedRepetitionItems?: Array<{ claimId: string; reason: string }>;
  simulationAttemptsNeedingReview?: Array<{ reason: string; claimId?: string }>;
  recommendedRemediation?: Array<{ title: string; reason: string; claimId?: string }>;
  offlineResumeState?: { pendingItems: number; lastSyncedAt?: string } | null;
}

interface LearnerInsightPanelProps {
  actionState: LearnerActionState;
  className?: string;
}

export function LearnerInsightPanel({ actionState, className }: LearnerInsightPanelProps) {
  const {
    currentClaimMastery = [],
    nextBestLesson,
    unresolvedMisconceptions = [],
    overdueSpacedRepetitionItems = [],
    simulationAttemptsNeedingReview = [],
    recommendedRemediation = [],
    offlineResumeState,
  } = actionState;

  const hasActionableItems =
    unresolvedMisconceptions.length > 0 ||
    overdueSpacedRepetitionItems.length > 0 ||
    simulationAttemptsNeedingReview.length > 0 ||
    recommendedRemediation.length > 0 ||
    (offlineResumeState?.pendingItems ?? 0) > 0;

  const getMasteryColor = (status: ClaimMasteryState["status"]) => {
    switch (status) {
      case "mastered":
        return "text-green-600";
      case "proficient":
        return "text-blue-600";
      case "developing":
        return "text-amber-600";
      default:
        return "text-gray-500";
    }
  };

  const getMasteryLabel = (status: ClaimMasteryState["status"]) => {
    switch (status) {
      case "mastered":
        return "Mastered";
      case "proficient":
        return "Proficient";
      case "developing":
        return "Developing";
      default:
        return "Not Started";
    }
  };

  const sortedClaims = [...currentClaimMastery].sort((a, b) => a.masteryScore - b.masteryScore);
  const weakestClaim = sortedClaims[0];

  return (
    <div className={cn("space-y-4", className)}>
      {/* Weakest Claim */}
      {weakestClaim && (
        <Card className="p-4 border-l-4 border-l-amber-500">
          <div className="flex items-start gap-3">
            <Target className="w-5 h-5 text-amber-500 mt-0.5" />
            <div className="flex-1">
              <h3 className="font-semibold text-sm mb-1">Focus Area: {weakestClaim.claimId}</h3>
              <div className="flex items-center gap-2 text-sm text-gray-600 mb-2">
                <span className={getMasteryColor(weakestClaim.status)}>
                  {getMasteryLabel(weakestClaim.status)}
                </span>
                <span>•</span>
                <span>Mastery: {Math.round(weakestClaim.masteryScore * 100)}%</span>
                <span>•</span>
                <span>{weakestClaim.evidenceCount} evidence items</span>
              </div>
              {nextBestLesson && (
                <div className="text-xs text-gray-500 bg-gray-50 p-2 rounded">
                  <span className="font-medium">Next:</span> {nextBestLesson.reason}
                </div>
              )}
            </div>
          </div>
        </Card>
      )}

      {/* Actionable Items */}
      {hasActionableItems && (
        <Card className="p-4">
          <h3 className="font-semibold text-sm mb-3 flex items-center gap-2">
            <Lightbulb className="w-4 h-4 text-blue-500" />
            Recommended Actions
          </h3>
          <div className="space-y-2">
            {/* Misconceptions */}
            {unresolvedMisconceptions.length > 0 && (
              <div className="flex items-start gap-2 text-sm">
                <AlertCircle className="w-4 h-4 text-red-500 mt-0.5 flex-shrink-0" />
                <div>
                  <span className="font-medium">Resolve {unresolvedMisconceptions.length} misconception{unresolvedMisconceptions.length > 1 ? 's' : ''}:</span>
                  <ul className="mt-1 space-y-1 text-gray-600">
                    {unresolvedMisconceptions.slice(0, 2).map((m, i) => (
                      <li key={i} className="text-xs">• {m.description}</li>
                    ))}
                    {unresolvedMisconceptions.length > 2 && (
                      <li className="text-xs">• +{unresolvedMisconceptions.length - 2} more</li>
                    )}
                  </ul>
                </div>
              </div>
            )}

            {/* Spaced Repetition */}
            {overdueSpacedRepetitionItems.length > 0 && (
              <div className="flex items-start gap-2 text-sm">
                <Clock className="w-4 h-4 text-orange-500 mt-0.5 flex-shrink-0" />
                <div>
                  <span className="font-medium">Review {overdueSpacedRepetitionItems.length} claim{overdueSpacedRepetitionItems.length > 1 ? 's' : ''} for spaced repetition</span>
                </div>
              </div>
            )}

            {/* Simulation Reviews */}
            {simulationAttemptsNeedingReview.length > 0 && (
              <div className="flex items-start gap-2 text-sm">
                <TrendingUp className="w-4 h-4 text-purple-500 mt-0.5 flex-shrink-0" />
                <div>
                  <span className="font-medium">{simulationAttemptsNeedingReview.length} simulation attempt{simulationAttemptsNeedingReview.length > 1 ? 's' : ''} needs review</span>
                </div>
              </div>
            )}

            {/* Remediation */}
            {recommendedRemediation.length > 0 && (
              <div className="flex items-start gap-2 text-sm">
                <CheckCircle2 className="w-4 h-4 text-green-500 mt-0.5 flex-shrink-0" />
                <div>
                  <span className="font-medium">{recommendedRemediation.length} remediation action{recommendedRemediation.length > 1 ? 's' : ''} available</span>
                </div>
              </div>
            )}

            {/* Offline Sync */}
            {(offlineResumeState?.pendingItems ?? 0) > 0 && (
              <div className="flex items-start gap-2 text-sm bg-blue-50 p-2 rounded">
                <AlertTriangle className="w-4 h-4 text-blue-600 mt-0.5 flex-shrink-0" />
                <div>
                  <span className="font-medium">{offlineResumeState.pendingItems} offline item{offlineResumeState.pendingItems > 1 ? 's' : ''} pending sync</span>
                  {offlineResumeState.lastSyncedAt && (
                    <span className="text-xs text-gray-500 ml-2">
                      (last synced: {new Date(offlineResumeState.lastSyncedAt).toLocaleTimeString()})
                    </span>
                  )}
                </div>
              </div>
            )}
          </div>
        </Card>
      )}

      {/* Claim Mastery Summary */}
      {currentClaimMastery.length > 0 && (
        <Card className="p-4">
          <h3 className="font-semibold text-sm mb-3">Claim Mastery Progress</h3>
          <div className="space-y-2">
            {sortedClaims.slice(0, 5).map((claim) => (
              <div key={claim.claimId} className="flex items-center gap-3">
                <div className="flex-1">
                  <div className="flex items-center justify-between text-sm mb-1">
                    <span className="font-medium text-gray-700">{claim.claimId}</span>
                    <span className={cn("text-xs", getMasteryColor(claim.status))}>
                      {getMasteryLabel(claim.status)}
                    </span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className={cn(
                        "h-2 rounded-full transition-all",
                        claim.masteryScore >= 0.8 ? "bg-green-500" :
                        claim.masteryScore >= 0.6 ? "bg-blue-500" :
                        claim.masteryScore >= 0.4 ? "bg-amber-500" : "bg-gray-400"
                      )}
                      style={{ width: `${claim.masteryScore * 100}%` }}
                    />
                  </div>
                </div>
                <span className="text-xs text-gray-500 w-16 text-right">
                  {Math.round(claim.masteryScore * 100)}%
                </span>
              </div>
            ))}
            {currentClaimMastery.length > 5 && (
              <div className="text-xs text-gray-500 text-center pt-2">
                +{currentClaimMastery.length - 5} more claims
              </div>
            )}
          </div>
        </Card>
      )}
    </div>
  );
}

