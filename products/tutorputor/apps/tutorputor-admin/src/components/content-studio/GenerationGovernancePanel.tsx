import { useCallback, useEffect, useMemo, useState } from "react";
import { clsx } from "clsx";
import {
    AlertTriangle,
    CheckCircle2,
    GitBranchPlus,
    RefreshCcw,
    Send,
    ShieldCheck,
    Sparkles,
} from "lucide-react";
import type {
    EvaluationRecord,
    EvaluationScorecard,
    GenerationRequest,
    GenerationRequestWithJobs,
    GenerationReviewDecision,
    RegenerationCandidate,
} from "@tutorputor/contracts/v1/content-studio";
import {
    contentStudioApi,
    type PublishGenerationRequestResult,
} from "../../services/contentStudioApi";
import { RiskBadge } from "./RiskBadge";

interface GenerationGovernancePanelProps {
    className?: string;
}

type ReviewAction = "approved" | "rejected" | "regeneration_requested";

function humanizeLabel(value: string): string {
    return value.replace(/_/g, " ");
}

function formatScore(value?: number): number {
    if (typeof value !== "number" || Number.isNaN(value)) {
        return 0;
    }

    return value <= 1 ? Math.round(value * 100) : Math.round(value);
}

function buildRiskExplanations(input: {
    request: GenerationRequest;
    scorecard: EvaluationScorecard | null;
    matchedCandidates: RegenerationCandidate[];
}): string[] {
    const explanations = new Set<string>();

    input.request.riskFactors?.forEach((factor) => explanations.add(humanizeLabel(factor)));
    input.scorecard?.blockedReasons.forEach((reason) => explanations.add(reason));
    input.scorecard?.issues
        .filter((issue) => issue.severity !== "info")
        .slice(0, 3)
        .forEach((issue) => explanations.add(`${humanizeLabel(issue.dimension)}: ${issue.message}`));
    input.matchedCandidates.forEach((candidate) => explanations.add(candidate.reason));

    if (explanations.size === 0) {
        explanations.add("No elevated risks are currently recorded for this request.");
    }

    return [...explanations];
}

function buildTriageSuggestions(input: {
    request: GenerationRequest;
    scorecard: EvaluationScorecard | null;
    matchedCandidates: RegenerationCandidate[];
    blockedEvaluations: EvaluationRecord[];
}): string[] {
    const steps = new Set<string>();

    if (input.scorecard?.blockedReasons.length) {
        steps.add("Request regeneration for blocked assets before approving publication.");
    }

    if (input.scorecard?.issues.some((issue) => issue.dimension === "evidence")) {
        steps.add("Add evidence-producing tasks or assessments so reviewers can verify learner outcomes.");
    }

    if (
        input.scorecard?.issues.some(
            (issue) => issue.message.includes("simulation") || issue.message.includes("animation"),
        )
    ) {
        steps.add("Queue the missing modality work so claims have the interactive or visual support they requested.");
    }

    if (input.matchedCandidates.length > 0) {
        steps.add("Queue the highest-priority regeneration candidate onto this request to reuse the governed generation path.");
    }

    if (input.request.reviewPath !== "auto_publish") {
        steps.add(`Keep this request in ${humanizeLabel(input.request.reviewPath)} until the blocked reasons are cleared.`);
    }

    if (input.blockedEvaluations.length === 0 && steps.size === 0) {
        steps.add("Run evaluation, confirm the scorecard, and record a reviewer decision.");
    }

    return [...steps];
}

function statusTone(status: string): string {
    switch (status) {
        case "completed":
        case "approved":
        case "published":
            return "bg-emerald-100 text-emerald-700";
        case "failed":
        case "rejected":
            return "bg-rose-100 text-rose-700";
        case "review":
        case "manual_review":
        case "regeneration_requested":
            return "bg-amber-100 text-amber-800";
        default:
            return "bg-slate-100 text-slate-700";
    }
}

function recommendationTone(recommendation?: string): string {
    switch (recommendation) {
        case "auto_publish":
            return "text-emerald-700";
        case "block":
            return "text-rose-700";
        default:
            return "text-amber-700";
    }
}

export function GenerationGovernancePanel({
    className,
}: GenerationGovernancePanelProps) {
    const [requests, setRequests] = useState<GenerationRequest[]>([]);
    const [candidates, setCandidates] = useState<RegenerationCandidate[]>([]);
    const [selectedRequestId, setSelectedRequestId] = useState<string | null>(null);
    const [selectedRequest, setSelectedRequest] =
        useState<GenerationRequestWithJobs | null>(null);
    const [scorecard, setScorecard] = useState<EvaluationScorecard | null>(null);
    const [evaluations, setEvaluations] = useState<EvaluationRecord[]>([]);
    const [decisions, setDecisions] = useState<GenerationReviewDecision[]>([]);
    const [publishSummary, setPublishSummary] =
        useState<PublishGenerationRequestResult | null>(null);
    const [decisionNote, setDecisionNote] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [isBootstrapping, setIsBootstrapping] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [activeAction, setActiveAction] = useState<string | null>(null);

    const loadOverview = useCallback(async () => {
        const [requestResult, candidateResult] = await Promise.all([
            contentStudioApi.getGenerationRequests({ limit: 8 }),
            contentStudioApi.getRegenerationCandidates(),
        ]);

        setRequests(requestResult.items ?? []);
        setCandidates(candidateResult ?? []);
        setSelectedRequestId((current) => current ?? requestResult.items?.[0]?.id ?? null);
    }, []);

    const loadSelectedRequest = useCallback(async (requestId: string) => {
        const [request, requestEvaluations, requestDecisions] = await Promise.all([
            contentStudioApi.getGenerationRequest(requestId),
            contentStudioApi.getGenerationEvaluations(requestId),
            contentStudioApi.getGenerationReviewDecisions(requestId),
        ]);

        setSelectedRequest(request);
        setEvaluations(requestEvaluations);
        setDecisions(requestDecisions);
        setScorecard(null);
        setPublishSummary(null);
    }, []);

    useEffect(() => {
        void (async () => {
            try {
                setError(null);
                setIsBootstrapping(true);
                await loadOverview();
            } catch (loadError) {
                setError(
                    loadError instanceof Error
                        ? loadError.message
                        : "Failed to load governed generation state.",
                );
            } finally {
                setIsBootstrapping(false);
            }
        })();
    }, [loadOverview]);

    useEffect(() => {
        if (!selectedRequestId) {
            setSelectedRequest(null);
            setEvaluations([]);
            setDecisions([]);
            return;
        }

        void (async () => {
            try {
                setError(null);
                setIsRefreshing(true);
                await loadSelectedRequest(selectedRequestId);
            } catch (loadError) {
                setError(
                    loadError instanceof Error
                        ? loadError.message
                        : "Failed to load request detail.",
                );
            } finally {
                setIsRefreshing(false);
            }
        })();
    }, [loadSelectedRequest, selectedRequestId]);

    const latestDecision = decisions[0] ?? null;
    const blockedEvaluations = useMemo(
        () => evaluations.filter((evaluation) => evaluation.recommendation === "block"),
        [evaluations],
    );
    const matchedCandidates = useMemo(
        () =>
            candidates.filter((candidate) =>
                evaluations.some((evaluation) => evaluation.assetId === candidate.assetId),
            ),
        [candidates, evaluations],
    );
    const riskExplanations = useMemo(
        () =>
            selectedRequest
                ? buildRiskExplanations({
                    request: selectedRequest,
                    scorecard,
                    matchedCandidates,
                })
                : [],
        [matchedCandidates, scorecard, selectedRequest],
    );
    const triageSuggestions = useMemo(
        () =>
            selectedRequest
                ? buildTriageSuggestions({
                    request: selectedRequest,
                    scorecard,
                    matchedCandidates,
                    blockedEvaluations,
                })
                : [],
        [blockedEvaluations, matchedCandidates, scorecard, selectedRequest],
    );

    const performAction = useCallback(
        async (actionKey: string, action: () => Promise<void>) => {
            try {
                setError(null);
                setActiveAction(actionKey);
                await action();
                await loadOverview();
                if (selectedRequestId) {
                    await loadSelectedRequest(selectedRequestId);
                }
            } catch (actionError) {
                setError(
                    actionError instanceof Error
                        ? actionError.message
                        : "Governance action failed.",
                );
            } finally {
                setActiveAction(null);
            }
        },
        [loadOverview, loadSelectedRequest, selectedRequestId],
    );

    const submitDecision = useCallback(
        async (status: ReviewAction) => {
            if (!selectedRequestId) return;
            await performAction(`decision:${status}`, async () => {
                await contentStudioApi.submitGenerationReviewDecision(selectedRequestId, {
                    status,
                    decisionNote: decisionNote.trim() || undefined,
                });
                setDecisionNote("");
            });
        },
        [decisionNote, performAction, selectedRequestId],
    );

    const runEvaluation = useCallback(async () => {
        if (!selectedRequestId) return;
        await performAction("evaluate", async () => {
            const nextScorecard = await contentStudioApi.evaluateGenerationRequest(
                selectedRequestId,
            );
            setScorecard(nextScorecard);
        });
    }, [performAction, selectedRequestId]);

    const publishAll = useCallback(async () => {
        if (!selectedRequestId) return;
        await performAction("publish", async () => {
            const summary = await contentStudioApi.publishGenerationRequest(
                selectedRequestId,
            );
            setPublishSummary(summary);
        });
    }, [performAction, selectedRequestId]);

    const detectCandidates = useCallback(async () => {
        await performAction("detect-candidates", async () => {
            await contentStudioApi.detectRegenerationCandidates();
        });
    }, [performAction]);

    const queueCandidate = useCallback(
        async (candidateId: string) => {
            if (!selectedRequestId) return;
            await performAction(`queue:${candidateId}`, async () => {
                await contentStudioApi.queueRegenerationCandidate(
                    candidateId,
                    selectedRequestId,
                );
            });
        },
        [performAction, selectedRequestId],
    );

    const dismissCandidate = useCallback(
        async (candidateId: string) => {
            await performAction(`dismiss:${candidateId}`, async () => {
                await contentStudioApi.dismissRegenerationCandidate(candidateId);
            });
        },
        [performAction],
    );

    return (
        <section className={clsx("rounded-2xl border border-gray-200 bg-white shadow-sm", className)}>
            <div className="border-b border-gray-200 px-6 py-5">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                    <div>
                        <div className="flex items-center gap-2 text-sm font-medium text-sky-700">
                            <ShieldCheck className="h-4 w-4" />
                            Governed Generation Console
                        </div>
                        <h3 className="mt-1 text-xl font-semibold text-gray-900">
                            Review, evaluate, regenerate, and publish from one surface
                        </h3>
                        <p className="mt-1 text-sm text-gray-500">
                            Recent generation requests, scorecards, review decisions, and open candidate queues.
                        </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        <button
                            type="button"
                            onClick={() => void detectCandidates()}
                            disabled={activeAction === "detect-candidates"}
                            className="inline-flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            <Sparkles className="h-4 w-4" />
                            Detect Candidates
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                void performAction("refresh", async () => {
                                    await loadOverview();
                                });
                            }}
                            disabled={activeAction === "refresh"}
                            className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            <RefreshCcw className="h-4 w-4" />
                            Refresh
                        </button>
                    </div>
                </div>
            </div>

            <div className="grid gap-6 px-6 py-6 xl:grid-cols-[1.15fr_1.85fr]">
                <div className="space-y-6">
                    <div className="rounded-xl border border-gray-200 bg-gray-50/70 p-4">
                        <div className="mb-3 flex items-center justify-between">
                            <div>
                                <h4 className="text-sm font-semibold text-gray-900">Recent Requests</h4>
                                <p className="text-xs text-gray-500">
                                    {requests.length} request{requests.length === 1 ? "" : "s"} in the live queue
                                </p>
                            </div>
                            {isBootstrapping || isRefreshing ? (
                                <span className="text-xs text-gray-400">Loading...</span>
                            ) : null}
                        </div>

                        <div className="space-y-3">
                            {requests.length === 0 ? (
                                <div className="rounded-lg border border-dashed border-gray-300 bg-white px-4 py-6 text-sm text-gray-500">
                                    No governed generation requests are available yet.
                                </div>
                            ) : (
                                requests.map((request) => (
                                    <button
                                        key={request.id}
                                        type="button"
                                        onClick={() => setSelectedRequestId(request.id)}
                                        className={clsx(
                                            "w-full rounded-xl border px-4 py-3 text-left transition",
                                            selectedRequestId === request.id
                                                ? "border-sky-400 bg-white shadow-sm"
                                                : "border-gray-200 bg-white hover:border-gray-300",
                                        )}
                                    >
                                        <div className="flex items-start justify-between gap-3">
                                            <div>
                                                <div className="font-medium text-gray-900">{request.title}</div>
                                                <div className="mt-1 text-xs text-gray-500">
                                                    {request.domain} • {request.completedJobs}/{request.totalJobs} jobs complete
                                                </div>
                                            </div>
                                            <span className={clsx("rounded-full px-2 py-1 text-xs font-medium", statusTone(request.status))}>
                                                {request.status.replace(/_/g, " ")}
                                            </span>
                                        </div>
                                        <div className="mt-3 flex items-center justify-between">
                                            <RiskBadge riskLevel={request.riskLevel} size="sm" />
                                            <span className="text-xs text-gray-500">
                                                {humanizeLabel(request.reviewPath)}
                                            </span>
                                        </div>
                                    </button>
                                ))
                            )}
                        </div>
                    </div>

                    <div className="rounded-xl border border-gray-200 bg-gray-50/70 p-4">
                        <div className="mb-3 flex items-center justify-between">
                            <div>
                                <h4 className="text-sm font-semibold text-gray-900">Open Regeneration Candidates</h4>
                                <p className="text-xs text-gray-500">
                                    Governed follow-up triggered by telemetry and evaluation drift.
                                </p>
                            </div>
                            <span className="text-xs font-medium text-gray-500">{candidates.length} open</span>
                        </div>

                        <div className="space-y-3">
                            {candidates.length === 0 ? (
                                <div className="rounded-lg border border-dashed border-gray-300 bg-white px-4 py-6 text-sm text-gray-500">
                                    No open candidates. Detection is up to date.
                                </div>
                            ) : (
                                candidates.slice(0, 5).map((candidate) => (
                                    <div key={candidate.id} className="rounded-xl border border-gray-200 bg-white px-4 py-3">
                                        <div className="flex items-start justify-between gap-3">
                                            <div>
                                                <div className="text-sm font-medium text-gray-900">
                                                    {candidate.assetType ?? "asset"} • {candidate.trigger.replace(/_/g, " ")}
                                                </div>
                                                <p className="mt-1 text-xs text-gray-500">{candidate.reason}</p>
                                            </div>
                                            <RiskBadge riskLevel={candidate.severity} size="sm" />
                                        </div>
                                        <div className="mt-3 flex flex-wrap gap-2">
                                            <button
                                                type="button"
                                                onClick={() => void queueCandidate(candidate.id)}
                                                disabled={!selectedRequestId || activeAction === `queue:${candidate.id}`}
                                                className="inline-flex items-center gap-2 rounded-lg bg-sky-600 px-3 py-2 text-xs font-medium text-white transition hover:bg-sky-500 disabled:cursor-not-allowed disabled:opacity-60"
                                            >
                                                <GitBranchPlus className="h-3.5 w-3.5" />
                                                Queue To Selected Request
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => void dismissCandidate(candidate.id)}
                                                disabled={activeAction === `dismiss:${candidate.id}`}
                                                className="rounded-lg border border-gray-200 px-3 py-2 text-xs font-medium text-gray-700 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                                            >
                                                Dismiss
                                            </button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>

                <div className="space-y-6">
                    {error ? (
                        <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                            {error}
                        </div>
                    ) : null}

                    {!selectedRequest ? (
                        <div className="rounded-xl border border-dashed border-gray-300 bg-gray-50 px-6 py-12 text-center text-sm text-gray-500">
                            Select a generation request to inspect scorecards, review history, and publish actions.
                        </div>
                    ) : (
                        <>
                            <div className="rounded-xl border border-gray-200 bg-white p-5">
                                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                    <div>
                                        <div className="flex items-center gap-3">
                                            <h4 className="text-lg font-semibold text-gray-900">
                                                {selectedRequest.title}
                                            </h4>
                                            <span className={clsx("rounded-full px-2 py-1 text-xs font-medium", statusTone(selectedRequest.status))}>
                                                {selectedRequest.status.replace(/_/g, " ")}
                                            </span>
                                        </div>
                                        <p className="mt-2 text-sm text-gray-500">
                                            {selectedRequest.description || "No author note provided."}
                                        </p>
                                        <div className="mt-3 flex flex-wrap gap-2 text-xs text-gray-500">
                                            <span className="rounded-full bg-slate-100 px-2 py-1">
                                                Domain: {selectedRequest.domain}
                                            </span>
                                            <span className="rounded-full bg-slate-100 px-2 py-1">
                                                Review path: {humanizeLabel(selectedRequest.reviewPath)}
                                            </span>
                                            <span className="rounded-full bg-slate-100 px-2 py-1">
                                                Jobs: {selectedRequest.completedJobs}/{selectedRequest.totalJobs}
                                            </span>
                                            {selectedRequest.riskFactors?.map((factor) => (
                                                <span key={factor} className="rounded-full bg-amber-50 px-2 py-1 text-amber-800">
                                                    Risk: {humanizeLabel(factor)}
                                                </span>
                                            ))}
                                        </div>
                                    </div>

                                    <div className="flex flex-wrap gap-2">
                                        <button
                                            type="button"
                                            onClick={() => void runEvaluation()}
                                            disabled={activeAction === "evaluate"}
                                            className="inline-flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60"
                                        >
                                            <RefreshCcw className="h-4 w-4" />
                                            Evaluate
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => void publishAll()}
                                            disabled={activeAction === "publish"}
                                            className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-60"
                                        >
                                            <Send className="h-4 w-4" />
                                            Publish All Passing Assets
                                        </button>
                                    </div>
                                </div>

                                <div className="mt-5 grid gap-3 md:grid-cols-4">
                                    <div className="rounded-xl bg-slate-50 px-4 py-3">
                                        <div className="text-xs uppercase tracking-wide text-gray-500">Latest Decision</div>
                                        <div className="mt-1 font-medium text-gray-900">
                                            {latestDecision ? latestDecision.status.replace(/_/g, " ") : "Pending"}
                                        </div>
                                    </div>
                                    <div className="rounded-xl bg-slate-50 px-4 py-3">
                                        <div className="text-xs uppercase tracking-wide text-gray-500">Evaluation Records</div>
                                        <div className="mt-1 font-medium text-gray-900">{evaluations.length}</div>
                                    </div>
                                    <div className="rounded-xl bg-slate-50 px-4 py-3">
                                        <div className="text-xs uppercase tracking-wide text-gray-500">Blocked Assets</div>
                                        <div className="mt-1 font-medium text-gray-900">{blockedEvaluations.length}</div>
                                    </div>
                                    <div className="rounded-xl bg-slate-50 px-4 py-3">
                                        <div className="text-xs uppercase tracking-wide text-gray-500">Open Candidate Matches</div>
                                        <div className="mt-1 font-medium text-gray-900">
                                                    {matchedCandidates.length}
                                        </div>
                                    </div>
                                </div>
                            </div>

                                    <div className="rounded-xl border border-gray-200 bg-white p-5">
                                        <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
                                            <div>
                                                <h4 className="text-sm font-semibold text-gray-900">Why This Request Is Risky</h4>
                                                <p className="mt-1 text-xs text-gray-500">
                                                    Review metadata, scorecard blockers, and candidate evidence condensed into reviewer-facing context.
                                                </p>
                                                <ul className="mt-4 space-y-2 text-sm text-gray-700">
                                                    {riskExplanations.map((explanation) => (
                                                        <li key={explanation} className="rounded-lg bg-slate-50 px-3 py-2">
                                                            {explanation}
                                                        </li>
                                                    ))}
                                                </ul>
                                            </div>

                                            <div>
                                                <h4 className="text-sm font-semibold text-gray-900">Recommended Next Steps</h4>
                                                <p className="mt-1 text-xs text-gray-500">
                                                    Suggested reviewer actions based on the current governed generation state.
                                                </p>
                                                <ol className="mt-4 space-y-2 text-sm text-gray-700">
                                                    {triageSuggestions.map((suggestion) => (
                                                        <li key={suggestion} className="rounded-lg border border-sky-100 bg-sky-50 px-3 py-2">
                                                            {suggestion}
                                                        </li>
                                                    ))}
                                                </ol>
                                            </div>
                                        </div>
                                    </div>

                            <div className="grid gap-6 xl:grid-cols-[1.25fr_0.95fr]">
                                <div className="space-y-6">
                                    <div className="rounded-xl border border-gray-200 bg-white p-5">
                                        <div className="flex items-center justify-between">
                                            <h4 className="text-sm font-semibold text-gray-900">Evaluation Scorecards</h4>
                                            {scorecard ? (
                                                <span className={clsx("text-sm font-semibold", recommendationTone(scorecard.recommendation))}>
                                                    {scorecard.recommendation.replace(/_/g, " ")}
                                                </span>
                                            ) : null}
                                        </div>

                                        {scorecard ? (
                                            <div className="mt-4 rounded-xl bg-slate-50 p-4">
                                                <div className="flex items-center justify-between">
                                                    <div>
                                                        <div className="text-xs uppercase tracking-wide text-gray-500">Overall Score</div>
                                                        <div className="mt-1 text-3xl font-semibold text-gray-900">
                                                            {formatScore(scorecard.overallScore)}
                                                        </div>
                                                    </div>
                                                    <div className="rounded-full bg-white px-3 py-1 text-sm font-medium text-gray-700">
                                                        {humanizeLabel(scorecard.recommendation)}
                                                    </div>
                                                </div>
                                                <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
                                                    {Object.entries(scorecard.dimensions).map(([dimension, value]) => (
                                                        <div key={dimension} className="rounded-lg bg-white px-3 py-2">
                                                            <div className="text-xs uppercase tracking-wide text-gray-500">{dimension}</div>
                                                            <div className="mt-1 font-medium text-gray-900">{formatScore(value)}</div>
                                                        </div>
                                                    ))}
                                                </div>
                                                {scorecard.blockedReasons.length > 0 ? (
                                                    <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-3 text-sm text-amber-800">
                                                        <div className="font-medium">Blocked reasons</div>
                                                        <ul className="mt-2 list-disc space-y-1 pl-5">
                                                            {scorecard.blockedReasons.map((reason) => (
                                                                <li key={reason}>{reason}</li>
                                                            ))}
                                                        </ul>
                                                    </div>
                                                ) : null}
                                            </div>
                                        ) : (
                                            <div className="mt-4 rounded-xl border border-dashed border-gray-300 bg-gray-50 px-4 py-8 text-sm text-gray-500">
                                                Run evaluation to generate the latest governed scorecard.
                                            </div>
                                        )}

                                        <div className="mt-4 space-y-3">
                                            {evaluations.map((evaluation) => (
                                                <div key={evaluation.id} className="rounded-xl border border-gray-200 px-4 py-3">
                                                    <div className="flex items-start justify-between gap-3">
                                                        <div>
                                                            <div className="text-sm font-medium text-gray-900">
                                                                {evaluation.assetId ?? evaluation.generationJobId ?? evaluation.id}
                                                            </div>
                                                            <div className="mt-1 text-xs text-gray-500">
                                                                Status: {humanizeLabel(evaluation.status)} • Recommendation: {humanizeLabel(evaluation.recommendation)}
                                                            </div>
                                                        </div>
                                                        <span className={clsx("rounded-full px-2 py-1 text-xs font-medium", recommendationTone(evaluation.recommendation))}>
                                                            {formatScore(evaluation.overallScore)}
                                                        </span>
                                                    </div>
                                                    {evaluation.issues?.length ? (
                                                        <div className="mt-3 space-y-2">
                                                            {evaluation.issues.slice(0, 2).map((issue) => (
                                                                <div key={`${evaluation.id}-${issue.dimension}-${issue.message}`} className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-gray-600">
                                                                    <span className="font-medium text-gray-900">{issue.dimension}</span>
                                                                    {": "}
                                                                    {issue.message}
                                                                </div>
                                                            ))}
                                                        </div>
                                                    ) : null}
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                </div>

                                <div className="space-y-6">
                                    <div className="rounded-xl border border-gray-200 bg-white p-5">
                                        <h4 className="text-sm font-semibold text-gray-900">Review Decisions</h4>
                                        <p className="mt-1 text-xs text-gray-500">
                                            Persisted and auditable decisions for the selected request.
                                        </p>

                                        <textarea
                                            value={decisionNote}
                                            onChange={(event) => setDecisionNote(event.target.value)}
                                            placeholder="Add reviewer rationale or regeneration guidance"
                                            className="mt-4 min-h-[110px] w-full rounded-xl border border-gray-200 px-3 py-3 text-sm text-gray-700 outline-none transition focus:border-sky-400"
                                        />

                                        <div className="mt-4 grid gap-2">
                                            <button
                                                type="button"
                                                onClick={() => void submitDecision("approved")}
                                                disabled={activeAction === "decision:approved"}
                                                className="inline-flex items-center justify-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-60"
                                            >
                                                <CheckCircle2 className="h-4 w-4" />
                                                Approve Request
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => void submitDecision("regeneration_requested")}
                                                disabled={activeAction === "decision:regeneration_requested"}
                                                className="inline-flex items-center justify-center gap-2 rounded-lg bg-amber-500 px-3 py-2 text-sm font-medium text-white transition hover:bg-amber-400 disabled:cursor-not-allowed disabled:opacity-60"
                                            >
                                                <RefreshCcw className="h-4 w-4" />
                                                Request Regeneration
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => void submitDecision("rejected")}
                                                disabled={activeAction === "decision:rejected"}
                                                className="inline-flex items-center justify-center gap-2 rounded-lg bg-rose-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-rose-500 disabled:cursor-not-allowed disabled:opacity-60"
                                            >
                                                <AlertTriangle className="h-4 w-4" />
                                                Reject Request
                                            </button>
                                        </div>

                                        <div className="mt-5 space-y-3">
                                            {decisions.length === 0 ? (
                                                <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 px-4 py-6 text-sm text-gray-500">
                                                    No review decisions have been recorded yet.
                                                </div>
                                            ) : (
                                                decisions.map((decision) => (
                                                    <div key={decision.id} className="rounded-xl border border-gray-200 px-4 py-3">
                                                        <div className="flex items-center justify-between gap-3">
                                                            <span className={clsx("rounded-full px-2 py-1 text-xs font-medium", statusTone(decision.status))}>
                                                                {decision.status.replace(/_/g, " ")}
                                                            </span>
                                                            <span className="text-xs text-gray-500">
                                                                {decision.reviewedAt
                                                                    ? new Date(decision.reviewedAt).toLocaleString()
                                                                    : new Date(decision.createdAt).toLocaleString()}
                                                            </span>
                                                        </div>
                                                        <p className="mt-2 text-sm text-gray-700">
                                                            {decision.decisionNote || "No reviewer note provided."}
                                                        </p>
                                                    </div>
                                                ))
                                            )}
                                        </div>
                                    </div>

                                    {publishSummary ? (
                                        <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-5">
                                            <h4 className="text-sm font-semibold text-emerald-900">Publish Summary</h4>
                                            <p className="mt-1 text-sm text-emerald-800">
                                                Published {publishSummary.published} asset{publishSummary.published === 1 ? "" : "s"} and skipped {publishSummary.skipped}.
                                            </p>
                                        </div>
                                    ) : null}
                                </div>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </section>
    );
}