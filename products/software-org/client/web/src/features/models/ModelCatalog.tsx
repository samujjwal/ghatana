import { memo, useState, useEffect, useMemo } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router';
import { useModelRegistry } from '@/hooks/useModelRegistry';
import { Model } from '@/services/api/modelsApi';
import { ENGINEER_DEVSECOPS_FLOW, DEVSECOPS_PHASE_LABELS, resolveDevSecOpsRoute, type DevSecOpsPhaseId } from '@/config/devsecopsEngineerFlow';
import { DevSecOpsPipelineStrip } from '@/shared/components';
import { getEngineerPhaseRouteForStory } from '@/lib/devsecops/getEngineerPhaseRouteForStory';
import ModelDetails from './components/ModelDetails';
import { ModelComparison } from './components/ModelComparison';
import { TestRunner } from './components/TestRunner';

/**
 * Model catalog page for viewing, versioning, and managing ML models.
 *
 * <p><b>Purpose</b><br>
 * Central registry for ML model versions, performance metrics, deployment status,
 * and testing results. Supports model comparison, A/B testing, and version rollback.
 *
 * <p><b>Features</b><br>
 * - Model registry with version history
 * - Performance metrics per version
 * - Deployment status tracking
 * - Model comparison interface
 * - A/B testing results
 * - Test execution dashboard
 * - Rollback capabilities
 * - Story-based deploy workflow via query params (Engineer Flow Phase 4)
 *
 * @doc.type page
 * @doc.purpose Model catalog and versioning
 * @doc.layer product
 * @doc.pattern Page
 */

/**
 * Mock artifact data associated with stories
 */
interface StoryArtifact {
    storyId: string;
    artifactName: string;
    version: string;
    service: string;
    buildNumber: string;
    commit: string;
    builtAt: string;
}

const STORY_ARTIFACTS: StoryArtifact[] = [
    {
        storyId: 'WI-1234',
        artifactName: 'auth-service',
        version: '2.4.1',
        service: 'auth-service',
        buildNumber: '1234',
        commit: 'abc1234',
        builtAt: new Date(Date.now() - 3600000).toISOString(),
    },
    {
        storyId: 'WI-1235',
        artifactName: 'payment-service',
        version: '3.1.0',
        service: 'payment-service',
        buildNumber: '5678',
        commit: 'def5678',
        builtAt: new Date(Date.now() - 1800000).toISOString(),
    },
    {
        storyId: 'WI-1236',
        artifactName: 'notification-service',
        version: '1.8.5',
        service: 'notification-service',
        buildNumber: '9012',
        commit: 'ghi9012',
        builtAt: new Date(Date.now() - 7200000).toISOString(),
    },
    {
        storyId: 'WI-1237',
        artifactName: 'product-service',
        version: '4.2.0',
        service: 'product-service',
        buildNumber: '3456',
        commit: 'jkl3456',
        builtAt: new Date(Date.now() - 86400000).toISOString(),
    },
    {
        storyId: 'WI-1238',
        artifactName: 'api-gateway',
        version: '2.0.0',
        service: 'api-gateway',
        buildNumber: '7890',
        commit: 'mno7890',
        builtAt: new Date(Date.now() - 172800000).toISOString(),
    },
    {
        storyId: 'WI-1239',
        artifactName: 'graphql-gateway',
        version: '1.5.0-beta.1',
        service: 'graphql-gateway',
        buildNumber: '1234',
        commit: 'pqr1234',
        builtAt: new Date(Date.now() - 900000).toISOString(),
    },
    {
        storyId: 'WI-1240',
        artifactName: 'audit-service',
        version: '1.2.0',
        service: 'audit-service',
        buildNumber: '5678',
        commit: 'stu5678',
        builtAt: new Date(Date.now() - 259200000).toISOString(),
    },
];

export const ModelCatalog = memo(function ModelCatalog() {
    // GIVEN: User on model catalog page
    // WHEN: User views models and metrics
    // THEN: Display registry with filtering and comparison

    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const action = searchParams.get('action');
    const storyId = searchParams.get('storyId');

    // Get artifact for the story (Engineer Flow deploy support)
    const storyArtifact = useMemo(() => {
        if (action !== 'deploy' || !storyId) return null;
        return STORY_ARTIFACTS.find(a => a.storyId === storyId) || null;
    }, [action, storyId]);

    const deployStep = ENGINEER_DEVSECOPS_FLOW.steps.find(step => step.stepId === 'engineer-deploy');
    const deployPhaseLabel = deployStep ? DEVSECOPS_PHASE_LABELS[deployStep.phaseId] : DEVSECOPS_PHASE_LABELS.deploy;
    const deployNextStep = deployStep?.nextStepId
        ? ENGINEER_DEVSECOPS_FLOW.steps.find(step => step.stepId === deployStep.nextStepId)
        : undefined;
    const monitorRoute = storyId && deployNextStep
        ? resolveDevSecOpsRoute(deployNextStep.route, { storyId })
        : storyId
            ? `/dashboard?storyId=${storyId}`
            : undefined;

    // Deploy state
    const [isDeploying, setIsDeploying] = useState(false);
    const [deploySuccess, setDeploySuccess] = useState(false);

    const handleDeploy = async () => {
        if (!storyArtifact) return;
        setIsDeploying(true);
        // Mock deployment delay
        await new Promise(resolve => setTimeout(resolve, 2000));
        setIsDeploying(false);
        setDeploySuccess(true);
    };

    const handlePhaseClick = (phaseId: DevSecOpsPhaseId) => {
        const targetRoute = getEngineerPhaseRouteForStory(phaseId, storyId || undefined);
        if (!targetRoute) return;
        navigate(targetRoute);
    };

    // Fetch real model registry from API
    const { data: allModels = [], isLoading } = useModelRegistry({ refetchInterval: 10000 });

    const [selectedModels, setSelectedModels] = useState<string[]>([]);
    const [view, setView] = useState<'catalog' | 'details' | 'compare' | 'test'>('catalog');
    const [selectedModel, setSelectedModel] = useState<Model | null>(null);
    const [showCatalogOnboarding, setShowCatalogOnboarding] = useState(() => {
        if (typeof window === 'undefined') {
            return true;
        }
        const stored = window.localStorage.getItem('softwareOrg.modelCatalog.onboarding.dismissed');
        return stored !== 'true';
    });

    // Debugging: log view and selection changes so developer can trace UI state
    useEffect(() => {
        console.debug('[ModelCatalog] view changed ->', view, 'selectedModel=', selectedModel?.id ?? null);
    }, [view, selectedModel?.id]);

    const handleSelectModel = (modelId: string) => {
        if (selectedModels.includes(modelId)) {
            setSelectedModels(selectedModels.filter((id) => id !== modelId));
        } else {
            setSelectedModels([...selectedModels, modelId]);
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'active':
                return 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400';
            case 'testing':
                return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-indigo-400';
            case 'deprecated':
                return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-rose-400';
            case 'archived':
                return 'bg-slate-100 dark:bg-neutral-800 text-slate-600 dark:text-neutral-400';
            default:
                return 'bg-slate-100 dark:bg-neutral-700 text-slate-600 dark:text-neutral-400';
        }
    };

    // Show Deploy Panel when action=deploy and storyId are present
    if (action === 'deploy' && storyId) {
        return (
            <div className="flex flex-col h-full bg-slate-50 dark:bg-slate-900 p-6">
                <div className="max-w-2xl mx-auto w-full">
                    {/* Header */}
                    <div className="mb-6">
                        <Link
                            to={`/reports?view=staging&storyId=${storyId}`}
                            className="text-sm text-blue-600 dark:text-indigo-400 hover:underline"
                        >
                            ← Back to Staging Validation
                        </Link>
                    </div>

                    <div className="bg-white dark:bg-neutral-800 rounded-lg border border-slate-200 dark:border-neutral-600 p-6">
                        <div className="flex items-center gap-3 mb-6">
                            <span className="text-3xl">🚀</span>
                            <div>
                                <h1 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                    Deploy to Production
                                </h1>
                                <p className="text-slate-600 dark:text-neutral-400">
                                    Story {storyId}
                                </p>
                                <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1">
                                    Phase: {deployPhaseLabel}
                                    {deployNextStep && ` • Next: ${deployNextStep.label}`}
                                </p>
                            </div>
                        </div>
                        <div className="mt-3 space-y-1">
                            <DevSecOpsPipelineStrip
                                phases={ENGINEER_DEVSECOPS_FLOW.phases}
                                currentPhaseId={deployStep?.phaseId ?? 'deploy'}
                                onPhaseClick={handlePhaseClick}
                            />
                            <p className="text-xs text-slate-500 dark:text-neutral-400">
                                Click a phase to move back to staging validation, deployment checks, or operate dashboards for this story.
                            </p>
                        </div>

                        {deploySuccess ? (
                            <div className="text-center py-8">
                                <div className="text-6xl mb-4">✅</div>
                                <h2 className="text-xl font-semibold text-green-600 dark:text-green-400 mb-2">
                                    Deployment Successful!
                                </h2>
                                <p className="text-slate-600 dark:text-neutral-400 mb-6">
                                    {storyArtifact?.artifactName} v{storyArtifact?.version} has been deployed to production.
                                </p>
                                <button
                                    onClick={() => monitorRoute && navigate(monitorRoute)}
                                    className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition"
                                >
                                    📊 Monitor production impact
                                </button>
                            </div>
                        ) : storyArtifact ? (
                            <>
                                {/* Artifact Details */}
                                <div className="space-y-4 mb-6">
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <div className="text-xs text-slate-500 dark:text-neutral-400 uppercase">Artifact</div>
                                            <div className="font-medium text-slate-900 dark:text-neutral-100">{storyArtifact.artifactName}</div>
                                        </div>
                                        <div>
                                            <div className="text-xs text-slate-500 dark:text-neutral-400 uppercase">Version</div>
                                            <div className="font-medium text-slate-900 dark:text-neutral-100">v{storyArtifact.version}</div>
                                        </div>
                                        <div>
                                            <div className="text-xs text-slate-500 dark:text-neutral-400 uppercase">Build</div>
                                            <div className="font-medium text-slate-900 dark:text-neutral-100">#{storyArtifact.buildNumber}</div>
                                        </div>
                                        <div>
                                            <div className="text-xs text-slate-500 dark:text-neutral-400 uppercase">Commit</div>
                                            <div className="font-mono text-sm text-slate-900 dark:text-neutral-100">{storyArtifact.commit}</div>
                                        </div>
                                    </div>

                                    <div className="p-3 bg-slate-100 dark:bg-neutral-700 rounded-lg">
                                        <div className="text-xs text-slate-500 dark:text-neutral-400 uppercase mb-1">Target Environment</div>
                                        <div className="flex items-center gap-2">
                                            <span className="px-2 py-1 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-rose-400 rounded text-sm font-medium">
                                                🔴 Production
                                            </span>
                                        </div>
                                    </div>
                                </div>

                                {/* Deploy Checklist */}
                                <div className="border-t border-slate-200 dark:border-neutral-600 pt-4 mb-6">
                                    <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3 mb-3">
                                        <div>
                                            <h3 className="font-medium text-slate-900 dark:text-neutral-100">Pre-deploy Checklist</h3>
                                            <p className="mt-1 text-xs text-slate-600 dark:text-neutral-400">
                                                AI-style hint: Treat this as the final promotion gate for story {storyId}. Double-check staging
                                                validation in Reporting, confirm approvals, then deploy and watch Real-Time Monitor and the SRE
                                                DevSecOps view for impact.
                                            </p>
                                        </div>
                                        {monitorRoute && (
                                            <button
                                                type="button"
                                                onClick={() => navigate(monitorRoute)}
                                                className="inline-flex items-center justify-center px-4 py-2 rounded-lg bg-indigo-600 text-white text-xs font-medium hover:bg-indigo-700 transition"
                                            >
                                                📈 Open post-deploy monitor
                                            </button>
                                        )}
                                    </div>
                                    <div className="space-y-2">
                                        {[
                                            'Staging validation completed',
                                            'All CI checks passed',
                                            'Required approvals obtained',
                                            'Feature flag configured',
                                        ].map((item, idx) => (
                                            <div key={idx} className="flex items-center gap-2 text-sm text-slate-700 dark:text-neutral-300">
                                                <span className="text-green-500">✓</span>
                                                {item}
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                {/* Deploy Button */}
                                <button
                                    onClick={handleDeploy}
                                    disabled={isDeploying}
                                    className="w-full px-6 py-3 bg-red-600 hover:bg-red-700 disabled:bg-red-400 text-white rounded-lg font-medium transition flex items-center justify-center gap-2"
                                >
                                    {isDeploying ? (
                                        <>
                                            <span className="animate-spin">⏳</span>
                                            Deploying...
                                        </>
                                    ) : (
                                        <>
                                            <span>🚀</span>
                                            Deploy to Production
                                        </>
                                    )}
                                </button>
                            </>
                        ) : (
                            <div className="text-center py-8 text-slate-500 dark:text-neutral-400">
                                <p>No artifact found for story {storyId}.</p>
                                <p className="text-sm mt-2">Ensure the CI pipeline has completed and produced an artifact.</p>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        );
    }

    // Show conditional content
    // If we somehow got into 'details' view without a selected model, show a helpful UI
    if (view === 'details' && !selectedModel) {
        return (
            <div className="flex flex-col h-full items-center justify-center bg-slate-50 dark:bg-slate-900">
                <div className="text-center text-slate-600 dark:text-neutral-400">
                    <p>Selected model data is not available.</p>
                    <button
                        className="mt-4 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
                        onClick={() => setView('catalog')}
                    >
                        Back to Catalog
                    </button>
                </div>
            </div>
        );
    }

    if (view === 'details' && selectedModel) {
        return (
            <div className="flex flex-col h-full bg-slate-50 dark:bg-slate-900">
                <button
                    onClick={() => setView('catalog')}
                    className="px-4 py-3 text-slate-600 dark:text-neutral-300 hover:text-slate-900 dark:hover:text-white transition-colors"
                >
                    ← Back to Catalog
                </button>
                <ModelDetails model={selectedModel as any} />
            </div>
        );
    }

    if (view === 'compare' && selectedModels.length >= 2) {
        const compareModels = allModels
            .filter((m) => selectedModels.includes(m.id))
            .map((m) => ({
                id: m.id,
                name: m.name,
                version: m.currentVersion,
                accuracy: m.accuracy,
                precision: m.precision,
                recall: m.recall,
                f1Score: m.f1Score,
                latency: m.latency,
                throughput: m.throughput,
            }));
        return (
            <div className="flex flex-col h-full bg-slate-50 dark:bg-slate-900">
                <button
                    onClick={() => setView('catalog')}
                    className="px-4 py-3 text-slate-600 dark:text-neutral-300 hover:text-slate-900 dark:hover:text-white transition-colors"
                >
                    ← Back to Catalog
                </button>
                <ModelComparison models={compareModels} />
            </div>
        );
    }

    if (view === 'test') {
        return (
            <div className="flex flex-col h-full bg-slate-50 dark:bg-slate-900">
                <button
                    onClick={() => setView('catalog')}
                    className="px-4 py-3 text-slate-600 dark:text-neutral-300 hover:text-slate-900 dark:hover:text-white transition-colors"
                >
                    ← Back to Catalog
                </button>
                <TestRunner />
            </div>
        );
    }

    // Catalog view
    return (
        <div className="flex flex-col h-full bg-slate-50 dark:bg-slate-900">
            {/* Header */}
            <div className="border-b border-slate-200 dark:border-neutral-600 bg-white dark:bg-neutral-800 p-4">
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-4">Model Catalog</h1>

                <div className="flex gap-2 items-center">
                    <button className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded text-sm font-medium">
                        + Deploy Model
                    </button>
                    <button
                        onClick={() => setView('test')}
                        className="px-3 py-1.5 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-900 dark:text-neutral-100 rounded text-sm font-medium"
                    >
                        🧪 Run Tests
                    </button>
                    <button
                        disabled={selectedModels.length < 2}
                        onClick={() => setView('compare')}
                        className="px-3 py-1.5 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 disabled:bg-slate-100 dark:disabled:bg-slate-800 disabled:text-slate-400 dark:disabled:text-slate-500 text-slate-900 dark:text-neutral-100 rounded text-sm font-medium ml-auto"
                    >
                        ⚖️ Compare ({selectedModels.length})
                    </button>
                </div>
            </div>

            {showCatalogOnboarding && (
                <section className="m-4 rounded-lg border border-slate-200 dark:border-neutral-600 bg-white dark:bg-slate-900 px-4 py-3 text-xs text-slate-700 dark:text-neutral-300 flex flex-col md:flex-row md:items-start md:justify-between gap-3">
                    <div className="space-y-1">
                        <div className="font-semibold text-slate-900 dark:text-neutral-100 text-sm">
                            How to use the Model Catalog
                        </div>
                        <div>
                            Use this view to browse models, see their latest metrics, and select one or more models to compare.
                        </div>
                        <div>
                            The deploy-from-story flow opens when a story sends you here with <code className="font-mono">action=deploy</code>. Use the test and compare views to validate
                            changes before promoting to production.
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={() => {
                            setShowCatalogOnboarding(false);
                            if (typeof window !== 'undefined') {
                                window.localStorage.setItem('softwareOrg.modelCatalog.onboarding.dismissed', 'true');
                            }
                        }}
                        className="self-start md:self-center px-3 py-1.5 rounded-md text-xs font-medium bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700"
                    >
                        Hide help
                    </button>
                </section>
            )}

            {/* Model List */}
            <div className="flex-1 overflow-y-auto p-4 space-y-2">
                {isLoading && allModels.length === 0 ? (
                    <div className="flex items-center justify-center h-full text-slate-600 dark:text-neutral-400">
                        Loading models...
                    </div>
                ) : (
                    allModels.map((model) => (
                        <div
                            key={model.id}
                            className="border border-slate-200 dark:border-neutral-600 rounded-lg p-4 bg-white dark:bg-neutral-800 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors cursor-pointer"
                        >
                            {/* Checkbox + Title */}
                            <div className="flex items-start gap-3 mb-3">
                                <input
                                    type="checkbox"
                                    checked={selectedModels.includes(model.id)}
                                    onChange={() => handleSelectModel(model.id)}
                                    className="mt-1 w-4 h-4"
                                />

                                <div className="flex-1">
                                    <div
                                        className="flex items-center gap-2 mb-1 cursor-pointer"
                                        onClick={() => {
                                            console.debug('[ModelCatalog] Card clicked, id=', model.id, 'view->details');
                                            setSelectedModel(model);
                                            setView('details');
                                        }}
                                    >
                                        <h3 className="font-semibold text-slate-900 dark:text-neutral-100 text-lg">{model.name}</h3>
                                        <span
                                            className={`px-2 py-0.5 rounded text-xs font-medium ${getStatusColor(
                                                model.status
                                            )}`}
                                        >
                                            {model.status.toUpperCase()}
                                        </span>
                                        <span className="text-xs text-slate-500 dark:text-slate-500 font-mono">
                                            v{model.currentVersion}
                                        </span>
                                    </div>

                                    <div className="text-sm text-slate-600 dark:text-neutral-400 mb-3">{model.type} Model</div>

                                    {/* Metrics Grid */}
                                    <div className="grid grid-cols-4 gap-2">
                                        <div className="bg-slate-100 dark:bg-slate-900 rounded p-2">
                                            <div className="text-xs text-slate-500 dark:text-slate-500">Accuracy</div>
                                            <div className="text-lg font-bold text-green-600 dark:text-green-400">
                                                {(model.accuracy * 100).toFixed(1)}%
                                            </div>
                                        </div>
                                        <div className="bg-slate-100 dark:bg-slate-900 rounded p-2">
                                            <div className="text-xs text-slate-500 dark:text-slate-500">Precision</div>
                                            <div className="text-lg font-bold text-blue-600 dark:text-indigo-400">
                                                {(model.precision * 100).toFixed(1)}%
                                            </div>
                                        </div>
                                        <div className="bg-slate-100 dark:bg-slate-900 rounded p-2">
                                            <div className="text-xs text-slate-500 dark:text-slate-500">Recall</div>
                                            <div className="text-lg font-bold text-yellow-600 dark:text-yellow-400">
                                                {(model.recall * 100).toFixed(1)}%
                                            </div>
                                        </div>
                                        <div className="bg-slate-100 dark:bg-slate-900 rounded p-2">
                                            <div className="text-xs text-slate-500 dark:text-slate-500">F1 Score</div>
                                            <div className="text-lg font-bold text-purple-600 dark:text-violet-400">
                                                {(model.f1Score * 100).toFixed(1)}%
                                            </div>
                                        </div>
                                    </div>

                                    {/* Footer */}
                                    <div className="flex items-center justify-between mt-3 text-xs text-slate-500 dark:text-slate-500">
                                        <span>Updated: {model.lastUpdated}</span>
                                        {model.deployedAt && <span>Deployed: {model.deployedAt}</span>}
                                    </div>
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </div>
        </div>
    );
});

ModelCatalog.displayName = 'ModelCatalog';

export default ModelCatalog;
