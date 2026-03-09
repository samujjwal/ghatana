import { memo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Badge } from '@/components/ui';

/**
 * Model registry and version management interface.
 *
 * <p><b>Purpose</b><br>
 * Centralized management of deployed and staged ML models.
 * Tracks model versions, performance, and deployment status.
 *
 * <p><b>Features</b><br>
 * - Model version listing and comparison
 * - Champion/Challenger model selection
 * - Performance metrics per version
 * - Deployment status tracking
 * - Rollback and promotion capabilities
 *
 * @doc.type component
 * @doc.purpose ML model registry and version management
 * @doc.layer product
 * @doc.pattern Organism
 */

interface ModelVersion {
    id: string;
    name: string;
    version: string;
    status: 'active' | 'staged' | 'archived';
    role: 'champion' | 'challenger' | 'archived';
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    deployedAt: string;
    trainedAt: string;
    metrics?: Record<string, number>;
}

interface ModelRegistryProps {
    models?: ModelVersion[];
    onSetChampion?: (modelId: string) => void;
    onPromoteChallenger?: (modelId: string) => void;
}

export const ModelRegistry = memo(function ModelRegistry({
    models,
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    onSetChampion,
    onPromoteChallenger,
}: ModelRegistryProps) {
    const [expandedId, setExpandedId] = useState<string | null>(null);
    const [comparisonMode, setComparisonMode] = useState(false);

    // Mock data if none provided
    const data = models || [
        {
            id: 'model-1',
            name: 'Credit Risk Predictor',
            version: 'v1.2.5',
            status: 'active' as const,
            role: 'champion' as const,
            accuracy: 0.924,
            precision: 0.918,
            recall: 0.931,
            f1Score: 0.924,
            deployedAt: '2024-11-01T10:30:00Z',
            trainedAt: '2024-10-28T15:45:00Z',
        },
        {
            id: 'model-2',
            name: 'Credit Risk Predictor',
            version: 'v1.3.0',
            status: 'staged' as const,
            role: 'challenger' as const,
            accuracy: 0.931,
            precision: 0.928,
            recall: 0.935,
            f1Score: 0.931,
            deployedAt: '2024-11-05T14:20:00Z',
            trainedAt: '2024-11-02T09:15:00Z',
        },
        {
            id: 'model-3',
            name: 'Credit Risk Predictor',
            version: 'v1.1.8',
            status: 'archived' as const,
            role: 'archived' as const,
            accuracy: 0.912,
            precision: 0.904,
            recall: 0.921,
            f1Score: 0.912,
            deployedAt: '2024-09-15T08:00:00Z',
            trainedAt: '2024-09-10T12:30:00Z',
        },
    ];

    // Simulate model registry query
    const { data: registryData } = useQuery({
        queryKey: ['modelRegistry'],
        queryFn: async () => {
            await new Promise((resolve) => setTimeout(resolve, 300));
            return {
                totalModels: data.length,
                activeModels: data.filter((m) => m.status === 'active').length,
                stagedModels: data.filter((m) => m.status === 'staged').length,
                lastUpdated: new Date().toISOString(),
            };
        },
        staleTime: 5 * 60 * 1000,
        gcTime: 10 * 60 * 1000,
    });

    const getStatusBadge = (status: ModelVersion['status']) => {
        const config = {
            active: { tone: 'positive' as const, icon: '✓', label: 'Active' },
            staged: { tone: 'warning' as const, icon: '⏱', label: 'Staged' },
            archived: { tone: 'neutral' as const, icon: '📦', label: 'Archived' },
        }[status];

        return config ? (
            <Badge tone={config.tone} variant="neutral">
                {config.icon} {config.label}
            </Badge>
        ) : null;
    };

    const getRoleIcon = (role: ModelVersion['role']) => {
        switch (role) {
            case 'champion':
                return '👑';
            case 'challenger':
                return '⚔️';
            case 'archived':
                return '📦';
            default:
                return '•';
        }
    };

    const champion = data.find((m) => m.role === 'champion');
    const challenger = data.find((m) => m.role === 'challenger');

    return (
        <div className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="space-y-1">
                    <h2 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                        📚 Model Registry
                    </h2>
                    {registryData && (
                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                            {registryData.activeModels} Active • {registryData.stagedModels}{' '}
                            Staged • {registryData.totalModels - registryData.activeModels - registryData.stagedModels} Archived
                        </p>
                    )}
                </div>
                <button
                    onClick={() => setComparisonMode(!comparisonMode)}
                    className="rounded bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-200 dark:bg-neutral-800 dark:text-neutral-300 dark:hover:bg-slate-700"
                >
                    {comparisonMode ? '📊 Exit Comparison' : '⚖️ Compare'}
                </button>
            </div>

            {/* Champion vs Challenger Quick View */}
            {comparisonMode && champion && challenger && (
                <div className="rounded bg-gradient-to-r from-purple-50 to-pink-50 p-4 dark:from-purple-900/20 dark:to-pink-900/20">
                    <h3 className="mb-3 text-xs font-semibold text-slate-900 dark:text-neutral-100">
                        👑 vs ⚔️ Comparison
                    </h3>
                    <div className="grid grid-cols-2 gap-3">
                        {/* Champion */}
                        <div className="space-y-2 rounded bg-white p-3 dark:bg-slate-900">
                            <p className="text-xs font-bold text-slate-900 dark:text-neutral-100">
                                Champion: {champion.version}
                            </p>
                            <div className="space-y-1 text-xs text-slate-600 dark:text-neutral-400">
                                <div className="flex justify-between">
                                    <span>Accuracy</span>
                                    <span className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {(champion.accuracy * 100).toFixed(2)}%
                                    </span>
                                </div>
                                <div className="flex justify-between">
                                    <span>F1 Score</span>
                                    <span className="font-semibold text-slate-900 dark:text-neutral-100">
                                        {(champion.f1Score * 100).toFixed(2)}%
                                    </span>
                                </div>
                            </div>
                        </div>

                        {/* Challenger */}
                        <div className="space-y-2 rounded bg-white p-3 dark:bg-slate-900">
                            <p className="text-xs font-bold text-slate-900 dark:text-neutral-100">
                                Challenger: {challenger.version}
                            </p>
                            <div className="space-y-1 text-xs text-slate-600 dark:text-neutral-400">
                                <div className="flex justify-between">
                                    <span>Accuracy</span>
                                    <span
                                        className={`font-semibold ${challenger.accuracy > champion.accuracy
                                            ? 'text-green-600 dark:text-green-400'
                                            : 'text-red-600 dark:text-rose-400'
                                            }`}
                                    >
                                        {(challenger.accuracy * 100).toFixed(2)}%
                                    </span>
                                </div>
                                <div className="flex justify-between">
                                    <span>F1 Score</span>
                                    <span
                                        className={`font-semibold ${challenger.f1Score > champion.f1Score
                                            ? 'text-green-600 dark:text-green-400'
                                            : 'text-red-600 dark:text-rose-400'
                                            }`}
                                    >
                                        {(challenger.f1Score * 100).toFixed(2)}%
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {challenger.accuracy > champion.accuracy && (
                        <button
                            onClick={() => onPromoteChallenger?.(challenger.id)}
                            className="mt-3 w-full rounded bg-green-600 py-2 text-xs font-semibold text-white hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-800"
                        >
                            🚀 Promote Challenger to Champion
                        </button>
                    )}
                </div>
            )}

            {/* Model List */}
            <div className="space-y-2">
                <h3 className="text-xs font-semibold text-slate-700 dark:text-neutral-300">
                    All Models
                </h3>
                {data.map((model) => (
                    <div
                        key={model.id}
                        className="rounded border border-slate-200 dark:border-neutral-600"
                    >
                        <button
                            onClick={() =>
                                setExpandedId(expandedId === model.id ? null : model.id)
                            }
                            className="w-full px-4 py-3 text-left hover:bg-slate-50 dark:hover:bg-slate-800"
                        >
                            <div className="flex items-center justify-between">
                                <div className="flex flex-1 items-center gap-3">
                                    <span className="text-lg">{getRoleIcon(model.role)}</span>
                                    <div className="flex-1">
                                        <p className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                            {model.name} - {model.version}
                                        </p>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                                            Deployed {new Date(model.deployedAt).toLocaleDateString()}
                                        </p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2">
                                    {getStatusBadge(model.status)}
                                    <span className="text-slate-500 dark:text-neutral-400">
                                        {expandedId === model.id ? '▼' : '▶'}
                                    </span>
                                </div>
                            </div>
                        </button>

                        {/* Expanded Details */}
                        {expandedId === model.id && (
                            <div className="border-t border-slate-200 bg-slate-50 p-4 dark:border-neutral-600 dark:bg-neutral-800">
                                <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                                    <div>
                                        <p className="text-xs font-semibold text-slate-600 dark:text-neutral-400">
                                            Accuracy
                                        </p>
                                        <p className="text-sm font-bold text-slate-900 dark:text-neutral-100">
                                            {(model.accuracy * 100).toFixed(2)}%
                                        </p>
                                    </div>
                                    <div>
                                        <p className="text-xs font-semibold text-slate-600 dark:text-neutral-400">
                                            Precision
                                        </p>
                                        <p className="text-sm font-bold text-slate-900 dark:text-neutral-100">
                                            {(model.precision * 100).toFixed(2)}%
                                        </p>
                                    </div>
                                    <div>
                                        <p className="text-xs font-semibold text-slate-600 dark:text-neutral-400">
                                            Recall
                                        </p>
                                        <p className="text-sm font-bold text-slate-900 dark:text-neutral-100">
                                            {(model.recall * 100).toFixed(2)}%
                                        </p>
                                    </div>
                                    <div>
                                        <p className="text-xs font-semibold text-slate-600 dark:text-neutral-400">
                                            F1 Score
                                        </p>
                                        <p className="text-sm font-bold text-slate-900 dark:text-neutral-100">
                                            {(model.f1Score * 100).toFixed(2)}%
                                        </p>
                                    </div>
                                </div>

                                {model.status === 'staged' && (
                                    <div className="mt-4 flex gap-2">
                                        <button
                                            onClick={() => onPromoteChallenger?.(model.id)}
                                            className="flex-1 rounded bg-green-600 px-3 py-2 text-xs font-semibold text-white hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-800"
                                        >
                                            ✓ Promote to Champion
                                        </button>
                                        <button className="flex-1 rounded border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 dark:border-neutral-600 dark:text-neutral-300 dark:hover:bg-slate-700">
                                            Archive
                                        </button>
                                    </div>
                                )}

                                {model.status === 'active' && model.role === 'champion' && (
                                    <div className="mt-4">
                                        <button className="w-full rounded border border-slate-300 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 dark:border-neutral-600 dark:text-neutral-300 dark:hover:bg-slate-700">
                                            📋 View Metrics Report
                                        </button>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                ))}
            </div>

            {/* Footer */}
            <div className="border-t border-slate-200 pt-3 dark:border-neutral-600">
                <button className="w-full rounded bg-blue-600 px-3 py-2 text-xs font-semibold text-white hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-800">
                    ➕ Register New Model
                </button>
            </div>
        </div>
    );
});
