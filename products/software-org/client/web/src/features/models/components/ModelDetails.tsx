import { memo } from 'react';

/**
 * Detailed model information with version history and metrics.
 *
 * <p><b>Purpose</b><br>
 * Displays comprehensive model information including performance metrics,
 * version history, training data, deployment status, and rollback options.
 *
 * <p><b>Features</b><br>
 * - Current version metrics
 * - Version history timeline
 * - Training data summary
 * - Performance comparison vs previous version
 * - Deployment status
 * - Rollback button
 * - Model metadata and tags
 *
 * <p><b>Props</b><br>
 * @param model - Model to display details for
 *
 * @doc.type component
 * @doc.purpose Model details viewer
 * @doc.layer product
 * @doc.pattern Details Panel
 */

interface DetailModel {
    id: string;
    name: string;
    type: string;
    currentVersion: string;
    status: 'active' | 'testing' | 'deprecated';
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    lastUpdated: string;
    deployedAt: string | null;
}

interface ModelDetailsProps {
    model: DetailModel;
}

interface VersionInfo {
    version: string;
    releaseDate: string;
    status: string;
    accuracy: number;
    changes: string[];
}

// Version history - typically fetched from API
// TODO: Integrate with models API to fetch version history
const versionHistory: VersionInfo[] = [
    {
        version: '2.3.1',
        releaseDate: '2024-01-10',
        status: 'Current',
        accuracy: 0.956,
        changes: ['Fixed edge cases', 'Improved latency'],
    },
    {
        version: '2.3.0',
        releaseDate: '2024-01-05',
        status: 'Previous',
        accuracy: 0.951,
        changes: ['Added new features', 'Retrained on latest data'],
    },
    {
        version: '2.2.5',
        releaseDate: '2023-12-20',
        status: 'Archived',
        accuracy: 0.944,
        changes: ['Bug fixes', 'Performance optimization'],
    },
];

export const ModelDetails = memo(function ModelDetails({ model }: ModelDetailsProps) {
    // GIVEN: Model selected from catalog
    // WHEN: View details
    // THEN: Display full model information and history

    return (
        <div className="flex-1 overflow-y-auto p-4 space-y-6 bg-slate-50 dark:bg-slate-950">
            {/* Header */}
            <div className="bg-white dark:bg-neutral-800 rounded-lg p-6 border border-slate-200 dark:border-neutral-600">
                <div className="flex items-start justify-between mb-4">
                    <div>
                        <h2 className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mb-2">{model.name}</h2>
                        <div className="flex gap-2 items-center">
                            <span className="text-slate-500 dark:text-neutral-400">Version</span>
                            <span className="font-mono font-bold text-slate-700 dark:text-slate-200">{model.currentVersion}</span>
                            <span className="text-slate-400">•</span>
                            <span className="text-slate-500 text-sm">{model.type}</span>
                        </div>
                    </div>
                    {model.status === 'active' && (
                        <div className="flex items-center gap-2 text-green-600 dark:text-green-400">
                            <span className="w-3 h-3 rounded-full bg-green-500 animate-pulse" />
                            <span>Active</span>
                        </div>
                    )}
                </div>

                {/* Key Metrics */}
                <div className="grid grid-cols-4 gap-4">
                    <div>
                        <div className="text-xs text-slate-500 mb-1">Accuracy</div>
                        <div className="text-2xl font-bold text-green-600 dark:text-green-400">{(model.accuracy * 100).toFixed(2)}%</div>
                    </div>
                    <div>
                        <div className="text-xs text-slate-500 mb-1">Precision</div>
                        <div className="text-2xl font-bold text-blue-600 dark:text-indigo-400">{(model.precision * 100).toFixed(2)}%</div>
                    </div>
                    <div>
                        <div className="text-xs text-slate-500 mb-1">Recall</div>
                        <div className="text-2xl font-bold text-yellow-600 dark:text-yellow-400">{(model.recall * 100).toFixed(2)}%</div>
                    </div>
                    <div>
                        <div className="text-xs text-slate-500 mb-1">F1 Score</div>
                        <div className="text-2xl font-bold text-purple-600 dark:text-violet-400">{(model.f1Score * 100).toFixed(2)}%</div>
                    </div>
                </div>
            </div>

            {/* Training Data */}
            <div className="bg-white dark:bg-neutral-800 rounded-lg p-4 border border-slate-200 dark:border-neutral-600">
                <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">Training Data</h3>
                <div className="grid grid-cols-3 gap-4 text-sm">
                    <div>
                        <span className="text-slate-500">Samples</span>
                        <div className="text-lg font-bold text-slate-700 dark:text-slate-200">1,245,840</div>
                    </div>
                    <div>
                        <span className="text-slate-500">Features</span>
                        <div className="text-lg font-bold text-slate-700 dark:text-slate-200">42</div>
                    </div>
                    <div>
                        <span className="text-slate-500">Training Time</span>
                        <div className="text-lg font-bold text-slate-700 dark:text-slate-200">4h 23m</div>
                    </div>
                </div>
            </div>

            {/* Version History */}
            <div className="bg-white dark:bg-neutral-800 rounded-lg p-4 border border-slate-200 dark:border-neutral-600">
                <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">Version History</h3>
                <div className="space-y-2">
                    {versionHistory.map((version: VersionInfo) => (
                        <div key={version.version} className="border border-slate-200 dark:border-neutral-600 rounded p-3 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors">
                            <div className="flex items-center justify-between mb-2">
                                <div>
                                    <span className="font-mono font-bold text-slate-700 dark:text-slate-200">v{version.version}</span>
                                    <span className="text-slate-500 mx-2">•</span>
                                    <span className="text-xs text-slate-500">{version.releaseDate}</span>
                                </div>
                                <span className="px-2 py-1 rounded text-xs font-medium bg-slate-100 dark:bg-slate-900 text-slate-600 dark:text-neutral-300">
                                    {version.status}
                                </span>
                            </div>

                            <div className="flex items-center gap-3 mb-2">
                                <span className="text-xs text-slate-500">Accuracy:</span>
                                <div className="flex-1 bg-slate-200 dark:bg-neutral-700 rounded-full h-2">
                                    <div
                                        className="h-full bg-green-500 rounded-full"
                                        style={{ width: `${version.accuracy * 100}%` }}
                                    />
                                </div>
                                <span className="text-xs font-mono text-slate-500 dark:text-neutral-400">{(version.accuracy * 100).toFixed(1)}%</span>
                            </div>

                            <div className="text-xs text-slate-500 dark:text-neutral-400">
                                <span>Changes: </span>
                                {version.changes.map((change: string, idx: number) => (
                                    <span key={idx}>
                                        {change}
                                        {idx < version.changes.length - 1 && ', '}
                                    </span>
                                ))}
                            </div>

                            {version.status === 'Previous' && (
                                <button className="mt-2 px-2 py-1 text-xs bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-neutral-300 rounded">
                                    ↶ Rollback to this version
                                </button>
                            )}
                        </div>
                    ))}
                </div>
            </div>

            {/* Deployment Info */}
            <div className="bg-white dark:bg-neutral-800 rounded-lg p-4 border border-slate-200 dark:border-neutral-600">
                <h3 className="font-semibold text-slate-900 dark:text-neutral-100 mb-3">Deployment</h3>
                <div className="space-y-2 text-sm">
                    <div className="flex items-center justify-between">
                        <span className="text-slate-500">Status</span>
                        <span className="text-green-600 dark:text-green-400 font-medium">Active in Production</span>
                    </div>
                    <div className="flex items-center justify-between">
                        <span className="text-slate-500">Deployed At</span>
                        <span className="text-slate-700 dark:text-slate-200">{model.deployedAt}</span>
                    </div>
                    <div className="flex items-center justify-between">
                        <span className="text-slate-500">Uptime</span>
                        <span className="text-slate-700 dark:text-slate-200">99.97%</span>
                    </div>
                    <div className="flex items-center justify-between">
                        <span className="text-slate-500">Requests (24h)</span>
                        <span className="text-slate-700 dark:text-slate-200">1,234,567</span>
                    </div>
                </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-2">
                <button className="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-medium rounded transition-colors">
                    📊 View Analytics
                </button>
                <button className="flex-1 px-4 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-neutral-100 font-medium rounded transition-colors">
                    🔄 Retrain
                </button>
                <button className="flex-1 px-4 py-2 bg-slate-200 dark:bg-neutral-700 hover:bg-slate-300 dark:hover:bg-slate-600 text-slate-700 dark:text-neutral-100 font-medium rounded transition-colors">
                    ⏸ Pause Deployment
                </button>
            </div>
        </div>
    );
});

export default ModelDetails;
