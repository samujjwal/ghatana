import { useState } from 'react';
import { Badge } from '@/components/ui';

/**
 * ML Observatory
 *
 * <p><b>Purpose</b><br>
 * Monitor model performance, learning metrics, and experiment tracking to ensure
 * ML models are healthy and performing within expectations in production.
 *
 * <p><b>Features</b><br>
 * - Model performance dashboards with key metrics
 * - Data drift detection and monitoring
 * - Model version comparison and history
 * - Experiment tracking and results
 * - Learning metrics and accuracy trends
 * - Model deployment status
 * - Retraining recommendations
 * - Alert configuration for performance thresholds
 *
 * <p><b>Specs</b><br>
 * See web-page-specs/14_ml_observatory.md for complete specification.
 *
 * <p><b>Mock Data</b><br>
 * All data is currently mocked. Integrate with ML tracking API at `/api/v1/ml`
 * for real model performance monitoring and experiment tracking.
 *
 * @doc.type component
 * @doc.purpose ML model performance monitoring and learning metrics
 * @doc.layer product
 * @doc.pattern Page
 */

interface ModelPerformance {
    modelId: string;
    name: string;
    version: string;
    accuracy: number;
    precision: number;
    recall: number;
    f1Score: number;
    dataPoints: number;
    lastUpdated: string;
    status: 'healthy' | 'warning' | 'critical';
    driftScore: number;
}

interface Experiment {
    id: string;
    name: string;
    status: 'running' | 'completed' | 'failed';
    accuracy: number;
    startTime: string;
    endTime?: string;
    modelVariant: string;
}

interface LearningMetric {
    timestamp: string;
    accuracy: number;
    loss: number;
    epoch: number;
}

export function MLObservatory() {
    const [selectedModel, setSelectedModel] = useState('model-001');
    const [timeRange, setTimeRange] = useState('7d');
    const [selectedExperiment, setSelectedExperiment] = useState<string>('exp-1');

    const models: ModelPerformance[] = [
        {
            modelId: 'model-001',
            name: 'Payment Fraud Detection',
            version: '2.3.1',
            accuracy: 96.8,
            precision: 97.2,
            recall: 96.1,
            f1Score: 96.6,
            dataPoints: 2400000,
            lastUpdated: new Date(Date.now() - 3600000).toISOString(),
            status: 'healthy',
            driftScore: 0.12,
        },
        {
            modelId: 'model-002',
            name: 'Demand Forecasting',
            version: '1.8.4',
            accuracy: 91.3,
            precision: 92.1,
            recall: 89.8,
            f1Score: 90.9,
            dataPoints: 850000,
            lastUpdated: new Date(Date.now() - 7200000).toISOString(),
            status: 'warning',
            driftScore: 0.38,
        },
        {
            modelId: 'model-003',
            name: 'User Churn Prediction',
            version: '3.1.2',
            accuracy: 88.5,
            precision: 89.2,
            recall: 87.1,
            f1Score: 88.1,
            dataPoints: 1200000,
            lastUpdated: new Date(Date.now() - 1800000).toISOString(),
            status: 'healthy',
            driftScore: 0.18,
        },
    ];

    const experiments: Experiment[] = [
        {
            id: 'exp-1',
            name: 'Experiment: Feature Engineering v2',
            status: 'completed',
            accuracy: 97.1,
            startTime: new Date(Date.now() - 86400000).toISOString(),
            endTime: new Date(Date.now() - 79200000).toISOString(),
            modelVariant: 'Payment Fraud Detection v2.3.2',
        },
        {
            id: 'exp-2',
            name: 'Experiment: New Architecture (LSTM)',
            status: 'running',
            accuracy: 96.4,
            startTime: new Date(Date.now() - 43200000).toISOString(),
            modelVariant: 'Payment Fraud Detection v2.4.0-beta',
        },
        {
            id: 'exp-3',
            name: 'Experiment: Hyperparameter Tuning',
            status: 'failed',
            accuracy: 95.8,
            startTime: new Date(Date.now() - 172800000).toISOString(),
            endTime: new Date(Date.now() - 169200000).toISOString(),
            modelVariant: 'Payment Fraud Detection v2.3.0-exp3',
        },
    ];

    const learningMetrics: LearningMetric[] = [
        { timestamp: new Date(Date.now() - 172800000).toISOString(), accuracy: 78.2, loss: 0.62, epoch: 1 },
        { timestamp: new Date(Date.now() - 158400000).toISOString(), accuracy: 82.4, loss: 0.48, epoch: 5 },
        { timestamp: new Date(Date.now() - 144000000).toISOString(), accuracy: 86.1, loss: 0.38, epoch: 10 },
        { timestamp: new Date(Date.now() - 129600000).toISOString(), accuracy: 89.7, loss: 0.28, epoch: 15 },
        { timestamp: new Date(Date.now() - 115200000).toISOString(), accuracy: 92.3, loss: 0.19, epoch: 20 },
        { timestamp: new Date(Date.now() - 100800000).toISOString(), accuracy: 94.1, loss: 0.14, epoch: 25 },
        { timestamp: new Date(Date.now() - 86400000).toISOString(), accuracy: 95.2, loss: 0.11, epoch: 30 },
        { timestamp: new Date(Date.now() - 72000000).toISOString(), accuracy: 96.0, loss: 0.09, epoch: 35 },
        { timestamp: new Date(Date.now() - 57600000).toISOString(), accuracy: 96.5, loss: 0.08, epoch: 40 },
        { timestamp: new Date(Date.now() - 43200000).toISOString(), accuracy: 96.8, loss: 0.07, epoch: 50 },
    ];

    const selectedModelData = models.find(m => m.modelId === selectedModel);
    const dataVersions = [
        { version: '2.3.1', date: 'Current (in production)', performance: '96.8%', dataPoints: '2.4M' },
        { version: '2.3.0', date: '1 week ago', performance: '96.2%', dataPoints: '2.3M' },
        { version: '2.2.8', date: '3 weeks ago', performance: '95.8%', dataPoints: '2.1M' },
    ];

    const getStatusColor = (status: string) => {
        if (status === 'healthy') return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400';
        if (status === 'warning') return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400';
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-rose-400';
    };

    const getExperimentStatusColor = (status: string) => {
        if (status === 'completed') return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400';
        if (status === 'running') return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-indigo-400';
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-rose-400';
    };

    return (
        <div className="space-y-6">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">ML Observatory</h1>
                <p className="text-slate-600 dark:text-neutral-400 mt-1">Model performance, learning metrics and experiment tracking</p>
            </div>

            {/* Time Range Selector */}
            <div className="flex gap-2">
                {['24h', '7d', '30d', '90d'].map(range => (
                    <button
                        key={range}
                        onClick={() => setTimeRange(range)}
                        className={`px-3 py-1 rounded text-sm font-medium transition ${timeRange === range
                                ? 'bg-blue-600 text-white'
                                : 'bg-slate-100 dark:bg-neutral-800 text-slate-700 dark:text-neutral-300 hover:bg-slate-200 dark:hover:bg-slate-700'
                            }`}
                    >
                        {range}
                    </button>
                ))}
            </div>

            {/* Main Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Left Column - Model List */}
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6 h-fit">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Models</h2>
                    <div className="space-y-3">
                        {models.map(model => (
                            <div
                                key={model.modelId}
                                onClick={() => setSelectedModel(model.modelId)}
                                className={`p-3 rounded-lg border cursor-pointer transition ${selectedModel === model.modelId
                                        ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30'
                                        : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
                                    }`}
                            >
                                <div className="flex items-start justify-between gap-2 mb-2">
                                    <div className="flex-1 min-w-0">
                                        <h3 className="font-medium text-slate-900 dark:text-neutral-100 truncate">{model.name}</h3>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400">v{model.version}</p>
                                    </div>
                                    <Badge className={getStatusColor(model.status)}>
                                        {model.status}
                                    </Badge>
                                </div>
                                <div className="flex items-center justify-between text-sm">
                                    <span className="font-semibold text-slate-900 dark:text-neutral-100">{model.accuracy.toFixed(1)}%</span>
                                    <span className="text-xs text-slate-600 dark:text-neutral-400">Drift: {model.driftScore.toFixed(2)}</span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Middle Column - Performance Metrics & Learning Curves */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Model Performance Metrics */}
                    {selectedModelData && (
                        <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                                Performance Metrics – {selectedModelData.name}
                            </h2>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                <div className="bg-gradient-to-br from-blue-50 to-blue-100 dark:from-blue-900/20 dark:to-blue-900/40 rounded-lg p-4">
                                    <div className="text-sm text-blue-800 dark:text-indigo-400 mb-1">Accuracy</div>
                                    <div className="text-2xl font-bold text-blue-900 dark:text-blue-300">{selectedModelData.accuracy.toFixed(1)}%</div>
                                </div>
                                <div className="bg-gradient-to-br from-green-50 to-green-100 dark:from-green-900/20 dark:to-green-900/40 rounded-lg p-4">
                                    <div className="text-sm text-green-800 dark:text-green-400 mb-1">Precision</div>
                                    <div className="text-2xl font-bold text-green-900 dark:text-green-300">{selectedModelData.precision.toFixed(1)}%</div>
                                </div>
                                <div className="bg-gradient-to-br from-purple-50 to-purple-100 dark:from-purple-900/20 dark:to-purple-900/40 rounded-lg p-4">
                                    <div className="text-sm text-purple-800 dark:text-violet-400 mb-1">Recall</div>
                                    <div className="text-2xl font-bold text-purple-900 dark:text-purple-300">{selectedModelData.recall.toFixed(1)}%</div>
                                </div>
                                <div className="bg-gradient-to-br from-orange-50 to-orange-100 dark:from-orange-900/20 dark:to-orange-900/40 rounded-lg p-4">
                                    <div className="text-sm text-orange-800 dark:text-orange-400 mb-1">F1 Score</div>
                                    <div className="text-2xl font-bold text-orange-900 dark:text-orange-300">{selectedModelData.f1Score.toFixed(1)}</div>
                                </div>
                            </div>
                            <div className="mt-6 pt-6 border-t border-slate-200 dark:border-neutral-600">
                                <div className="grid grid-cols-2 gap-4 text-sm">
                                    <div>
                                        <div className="text-slate-600 dark:text-neutral-400 mb-1">Data Points</div>
                                        <div className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                            {(selectedModelData.dataPoints / 1000000).toFixed(1)}M
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-slate-600 dark:text-neutral-400 mb-1">Data Drift</div>
                                        <div className={`text-lg font-semibold ${selectedModelData.driftScore > 0.3
                                                ? 'text-red-600 dark:text-rose-400'
                                                : 'text-green-600 dark:text-green-400'
                                            }`}>
                                            {selectedModelData.driftScore.toFixed(2)}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Learning Curves */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Learning Curve</h2>
                        <div className="bg-gradient-to-br from-slate-50 to-slate-100 dark:from-slate-800 dark:to-slate-900 rounded h-64 flex items-center justify-center border border-dashed border-slate-300 dark:border-neutral-600">
                            <div className="text-center">
                                <div className="text-4xl mb-2">📈</div>
                                <p className="text-slate-600 dark:text-neutral-400">Training progress visualization</p>
                                <p className="text-sm text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-1">{learningMetrics.length} epochs tracked</p>
                            </div>
                        </div>
                    </div>

                    {/* Model Versions */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Version History</h2>
                        <div className="space-y-2">
                            {dataVersions.map((version, idx) => (
                                <div key={idx} className="flex items-center justify-between p-3 bg-slate-50 dark:bg-neutral-800 rounded border border-slate-200 dark:border-neutral-600">
                                    <div className="flex-1">
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">v{version.version}</div>
                                        <div className="text-sm text-slate-600 dark:text-neutral-400">{version.date}</div>
                                    </div>
                                    <div className="text-right">
                                        <div className="font-semibold text-slate-900 dark:text-neutral-100">{version.performance}</div>
                                        <div className="text-xs text-slate-600 dark:text-neutral-400">{version.dataPoints} points</div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>

            {/* Experiments Section */}
            <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Experiments</h2>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    {experiments.map(exp => (
                        <div
                            key={exp.id}
                            onClick={() => setSelectedExperiment(exp.id)}
                            className={`p-4 rounded-lg border cursor-pointer transition ${selectedExperiment === exp.id
                                    ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30'
                                    : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
                                }`}
                        >
                            <div className="flex items-start justify-between mb-2">
                                <h3 className="font-medium text-slate-900 dark:text-neutral-100 flex-1">{exp.name}</h3>
                                <Badge className={getExperimentStatusColor(exp.status)}>
                                    {exp.status}
                                </Badge>
                            </div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400 mb-2">{exp.modelVariant}</p>
                            <div className="flex items-center justify-between text-sm">
                                <span className="font-semibold text-slate-900 dark:text-neutral-100">{exp.accuracy.toFixed(1)}%</span>
                                <span className="text-xs text-slate-600 dark:text-neutral-400">
                                    {new Date(exp.startTime).toLocaleDateString()}
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

export default MLObservatory;
