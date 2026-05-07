import { useCallback, useEffect, useMemo, useState } from 'react';
import {
    AlertTriangle,
    CheckCircle2,
    CircleDot,
    RefreshCcw,
    Send,
} from 'lucide-react';
import {
    contentStudioApi,
    type AdminValidationResult,
} from '../../services/contentStudioApi';
import { RiskBadge, type RiskLevel } from './RiskBadge';

interface PublicationReadinessPanelProps {
    experienceId: string;
    experienceTitle: string;
    onPublished?: () => void;
    className?: string;
}

function deriveRiskLevel(validation: AdminValidationResult): RiskLevel {
    const blockingCount = validation.checks.filter(
        (check) => !check.passed && check.severity === 'error',
    ).length;
    const advisoryCount = validation.checks.filter(
        (check) => !check.passed && check.severity !== 'error',
    ).length;

    if (blockingCount > 0) {
        return 'HIGH';
    }

    if (advisoryCount > 0 || validation.score < 80) {
        return 'MEDIUM';
    }

    return 'LOW';
}

const readinessTargets = [
    { id: 'claims', label: 'Learning claims', match: /claim|objective/i, fixAction: 'Open claim map' },
    { id: 'evidence', label: 'Evidence mapping', match: /evidence|coverage|artifact/i, fixAction: 'Map evidence' },
    { id: 'simulation', label: 'Simulation settings', match: /simulation|manifest|parameter|seed/i, fixAction: 'Configure simulation' },
    { id: 'assessment', label: 'Assessment coverage', match: /assessment|cbm|question|task|scoring/i, fixAction: 'Edit assessment' },
    { id: 'accessibility', label: 'Accessibility metadata', match: /accessibility|caption|transcript|alternative|aria/i, fixAction: 'Add accessibility notes' },
    { id: 'review', label: 'Review status', match: /review|sme|qa|approval|validation/i, fixAction: 'Send to reviewer' },
];

function matchesReadinessTarget(
    check: AdminValidationResult['checks'][number],
    target: (typeof readinessTargets)[number],
): boolean {
    const searchable = `${check.checkId} ${check.pillar} ${check.name} ${check.message} ${check.suggestion ?? ''}`;
    return target.match.test(searchable);
}

export function PublicationReadinessPanel({
    experienceId,
    experienceTitle,
    onPublished,
    className,
}: PublicationReadinessPanelProps) {
    const [validation, setValidation] = useState<AdminValidationResult | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isPublishing, setIsPublishing] = useState(false);

    const loadValidation = useCallback(async () => {
        try {
            setError(null);
            setIsLoading(true);
            const nextValidation = await contentStudioApi.validateExperience(experienceId);
            setValidation(nextValidation);
        } catch (loadError) {
            setError(
                loadError instanceof Error
                    ? loadError.message
                    : 'Failed to load publication readiness.',
            );
        } finally {
            setIsLoading(false);
        }
    }, [experienceId]);

    useEffect(() => {
        void loadValidation();
    }, [loadValidation]);

    const blockingChecks = useMemo(
        () => validation?.checks.filter((check) => !check.passed && check.severity === 'error') ?? [],
        [validation],
    );

    const advisoryChecks = useMemo(
        () => validation?.checks.filter((check) => !check.passed && check.severity !== 'error') ?? [],
        [validation],
    );

    const riskLevel = validation ? deriveRiskLevel(validation) : 'MEDIUM';

    const guidedTargets = useMemo(
        () =>
            readinessTargets.map((target) => {
                const failedChecks =
                    validation?.checks.filter((check) => !check.passed && matchesReadinessTarget(check, target)) ?? [];
                return {
                    ...target,
                    failedChecks,
                    status: failedChecks.length === 0 ? 'complete' : 'missing',
                };
            }),
        [validation],
    );

    const handlePublish = useCallback(async () => {
        if (!validation?.canPublish) {
            return;
        }

        try {
            setError(null);
            setIsPublishing(true);
            await contentStudioApi.publishExperience(experienceId);
            await loadValidation();
            onPublished?.();
        } catch (publishError) {
            setError(
                publishError instanceof Error
                    ? publishError.message
                    : 'Failed to publish experience.',
            );
        } finally {
            setIsPublishing(false);
        }
    }, [experienceId, loadValidation, onPublished, validation?.canPublish]);

    return (
        <section className={className ? className : ''}>
            <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div>
                        <div className="flex items-center gap-2 text-sm font-medium text-amber-700">
                            <AlertTriangle className="h-4 w-4" />
                            Review And Publication Status
                        </div>
                        <h3 className="mt-1 text-lg font-semibold text-gray-900 dark:text-white">
                            {experienceTitle}
                        </h3>
                        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                            Re-run validation to confirm the artifact graph is publishable. Governed generation queue and reviewer history remain available below in the console.
                        </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        <RiskBadge riskLevel={riskLevel} />
                        <button
                            type="button"
                            onClick={() => {
                                void loadValidation();
                            }}
                            disabled={isLoading}
                            className="inline-flex items-center gap-2 rounded-lg border border-gray-200 px-3 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-60 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
                        >
                            <RefreshCcw className="h-4 w-4" />
                            Re-run Validation
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                void handlePublish();
                            }}
                            disabled={isPublishing || !validation?.canPublish}
                            className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                            <Send className="h-4 w-4" />
                            {isPublishing ? 'Publishing...' : 'Publish'}
                        </button>
                    </div>
                </div>

                {error ? (
                    <div className="mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 dark:border-rose-900 dark:bg-rose-950/30 dark:text-rose-200">
                        {error}
                    </div>
                ) : null}

                {isLoading && !validation ? (
                    <div className="mt-4 rounded-xl border border-dashed border-gray-300 px-4 py-6 text-sm text-gray-500 dark:border-gray-600 dark:text-gray-400">
                        Loading publication readiness...
                    </div>
                ) : null}

                {validation ? (
                    <>
                        <div className="mt-4 grid gap-4 md:grid-cols-4">
                            <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-900/40">
                                <div className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                                    Publishability
                                </div>
                                <div className="mt-2 flex items-center gap-2 text-sm font-semibold text-gray-900 dark:text-white">
                                    <CheckCircle2 className={`h-4 w-4 ${validation.canPublish ? 'text-emerald-500' : 'text-rose-500'}`} />
                                    {validation.canPublish ? 'Ready to publish' : 'Blocked'}
                                </div>
                            </div>
                            <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-900/40">
                                <div className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                                    Validation Score
                                </div>
                                <div className="mt-2 text-2xl font-semibold text-gray-900 dark:text-white">
                                    {validation.score}%
                                </div>
                            </div>
                            <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-900/40">
                                <div className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                                    Blocking Reasons
                                </div>
                                <div className="mt-2 text-2xl font-semibold text-rose-600 dark:text-rose-300">
                                    {blockingChecks.length}
                                </div>
                            </div>
                            <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-900/40">
                                <div className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                                    Advisory Checks
                                </div>
                                <div className="mt-2 text-2xl font-semibold text-amber-600 dark:text-amber-300">
                                    {advisoryChecks.length}
                                </div>
                            </div>
                        </div>

                        <div className="mt-4 rounded-xl border border-indigo-200 bg-indigo-50/70 p-4 dark:border-indigo-900 dark:bg-indigo-950/20">
                            <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                                <div>
                                    <h4 className="text-sm font-semibold text-indigo-900 dark:text-indigo-100">
                                        Guided Publish Readiness
                                    </h4>
                                    <p className="text-sm text-indigo-700 dark:text-indigo-300">
                                        Fix the exact missing production requirements before the Publish button unlocks.
                                    </p>
                                </div>
                                <span className="text-sm font-medium text-indigo-900 dark:text-indigo-100">
                                    {guidedTargets.filter((target) => target.status === 'complete').length}/{guidedTargets.length} complete
                                </span>
                            </div>

                            <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                                {guidedTargets.map((target) => (
                                    <div
                                        key={target.id}
                                        className={`rounded-lg border px-3 py-3 ${
                                            target.status === 'complete'
                                                ? 'border-emerald-200 bg-white/80 dark:border-emerald-900 dark:bg-emerald-950/20'
                                                : 'border-amber-200 bg-white/90 dark:border-amber-900 dark:bg-amber-950/20'
                                        }`}
                                    >
                                        <div className="flex items-center gap-2">
                                            {target.status === 'complete' ? (
                                                <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                                            ) : (
                                                <CircleDot className="h-4 w-4 text-amber-600" />
                                            )}
                                            <span className="font-medium text-gray-900 dark:text-white">
                                                {target.label}
                                            </span>
                                        </div>
                                        <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                                            {target.status === 'complete'
                                                ? 'Ready'
                                                : `${target.failedChecks.length} issue${target.failedChecks.length === 1 ? '' : 's'} to resolve`}
                                        </p>
                                        {target.failedChecks[0]?.suggestion ? (
                                            <p className="mt-2 text-xs text-amber-800 dark:text-amber-200">
                                                {target.failedChecks[0].suggestion}
                                            </p>
                                        ) : null}
                                        {target.status !== 'complete' ? (
                                            <button
                                                type="button"
                                                className="mt-3 rounded-md border border-indigo-200 px-3 py-1.5 text-xs font-medium text-indigo-700 hover:bg-indigo-50 dark:border-indigo-800 dark:text-indigo-200"
                                            >
                                                {target.fixAction}
                                            </button>
                                        ) : null}
                                    </div>
                                ))}
                            </div>
                        </div>

                        <div className="mt-4 grid gap-4 lg:grid-cols-2">
                            <div className="rounded-xl border border-rose-200 bg-rose-50/70 p-4 dark:border-rose-900 dark:bg-rose-950/20">
                                <div className="text-sm font-semibold text-rose-800 dark:text-rose-200">
                                    Publication Block Reasons
                                </div>
                                {blockingChecks.length === 0 ? (
                                    <p className="mt-2 text-sm text-emerald-700 dark:text-emerald-300">
                                        No blocking issues remain. The artifact graph is internally consistent for publication.
                                    </p>
                                ) : (
                                    <ul className="mt-3 space-y-3 text-sm text-rose-900 dark:text-rose-100">
                                        {blockingChecks.map((check) => (
                                            <li key={check.checkId} className="rounded-lg border border-rose-200 bg-white/70 px-3 py-3 dark:border-rose-900 dark:bg-rose-950/30">
                                                <div className="font-medium">{check.name}</div>
                                                <div className="mt-1">{check.message}</div>
                                                {check.suggestion ? (
                                                    <div className="mt-2 text-rose-700 dark:text-rose-300">
                                                        Next step: {check.suggestion}
                                                    </div>
                                                ) : null}
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>

                            <div className="rounded-xl border border-amber-200 bg-amber-50/70 p-4 dark:border-amber-900 dark:bg-amber-950/20">
                                <div className="text-sm font-semibold text-amber-800 dark:text-amber-200">
                                    Review Guidance
                                </div>
                                {advisoryChecks.length === 0 ? (
                                    <p className="mt-2 text-sm text-gray-700 dark:text-gray-300">
                                        No advisory findings remain. Reviewer attention can stay focused on governed generation decisions and downstream approvals.
                                    </p>
                                ) : (
                                    <ul className="mt-3 space-y-3 text-sm text-amber-900 dark:text-amber-100">
                                        {advisoryChecks.map((check) => (
                                            <li key={check.checkId} className="rounded-lg border border-amber-200 bg-white/70 px-3 py-3 dark:border-amber-900 dark:bg-amber-950/30">
                                                <div className="font-medium">{check.name}</div>
                                                <div className="mt-1">{check.message}</div>
                                                {check.suggestion ? (
                                                    <div className="mt-2 text-amber-700 dark:text-amber-300">
                                                        Recommendation: {check.suggestion}
                                                    </div>
                                                ) : null}
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>
                        </div>
                    </>
                ) : null}
            </div>
        </section>
    );
}
