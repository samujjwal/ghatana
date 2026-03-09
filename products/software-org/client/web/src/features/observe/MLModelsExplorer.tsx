import { useState } from "react";
import { useNavigate } from "react-router";
import { useAtomValue } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import { useMLModels, type MLModelResponse } from '@/hooks/useObserveApi';
import { Brain, TrendingUp, TrendingDown, AlertTriangle, CheckCircle, Activity, BarChart3, GitCompare, Bell, Eye, Sparkles, TrendingDown as DriftIcon } from 'lucide-react';
import { Badge } from "@/components/ui";

/**
 * ML Models Explorer
 *
 * <p><b>Purpose</b><br>
 * Monitor machine learning models in production with health metrics and drift detection.
 * Provides ML engineers with real-time visibility into model performance.
 *
 * <p><b>Features</b><br>
 * - Model status monitoring (healthy, degraded, failed)
 * - Performance metrics (accuracy, precision, recall, F1)
 * - Drift detection and alerting
 * - Latency monitoring (P50, P99)
 * - Related incident tracking
 *
 * @doc.type component
 * @doc.purpose ML model monitoring dashboard
 * @doc.layer product
 * @doc.pattern Page
 */
export function MLModelsExplorer() {
    const navigate = useNavigate();
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const [filterStatus, setFilterStatus] = useState<'all' | 'healthy' | 'degraded' | 'failed'>('all');
    const [selectedModel, setSelectedModel] = useState<MLModelResponse | null>(null);
    const [showDriftModal, setShowDriftModal] = useState(false);
    const [showFeatureImportanceModal, setShowFeatureImportanceModal] = useState(false);
    const [showPredictionAnalysisModal, setShowPredictionAnalysisModal] = useState(false);
    const [showAlertConfigModal, setShowAlertConfigModal] = useState(false);
    const [compareMode, setCompareMode] = useState(false);
    const [selectedModelsForCompare, setSelectedModelsForCompare] = useState<string[]>([]);

    const tenantId = selectedTenant || 'acme-payments-id';
    
    const statusParam = filterStatus === 'all' ? undefined : filterStatus;
    const { data: modelsData, isLoading, error } = useMLModels(tenantId, statusParam);

    const models = modelsData?.data || [];

    // Calculate stats
    const stats = {
        total: models.length,
        healthy: models.filter(m => m.status === 'healthy').length,
        degraded: models.filter(m => m.status === 'degraded').length,
        failed: models.filter(m => m.status === 'failed').length,
    };

    const statusConfig = {
        'healthy': {
            variant: 'success' as const,
            icon: CheckCircle,
            color: 'text-green-500',
            bg: 'bg-green-50 dark:bg-green-900/20',
        },
        'degraded': {
            variant: 'warning' as const,
            icon: AlertTriangle,
            color: 'text-amber-500',
            bg: 'bg-amber-50 dark:bg-amber-900/20',
        },
        'failed': {
            variant: 'danger' as const,
            icon: AlertTriangle,
            color: 'text-red-500',
            bg: 'bg-red-50 dark:bg-red-900/20',
        },
    };

    if (error) {
        return (
            <div className="p-6">
                <div className="text-red-600 dark:text-red-400">
                    Failed to load ML models: {error.message}
                </div>
            </div>
        );
    }

    const handleModelClick = (model: MLModelResponse) => {
        if (compareMode) {
            if (selectedModelsForCompare.includes(model.id)) {
                setSelectedModelsForCompare(prev => prev.filter(id => id !== model.id));
            } else if (selectedModelsForCompare.length < 3) {
                setSelectedModelsForCompare(prev => [...prev, model.id]);
            }
        } else {
            navigate(`/observe/ml-model-detail/${model.id}`);
        }
    };

    const handleDriftAnalysis = (model: MLModelResponse, e: React.MouseEvent) => {
        e.stopPropagation();
        setSelectedModel(model);
        setShowDriftModal(true);
    };

    const handleFeatureImportance = (model: MLModelResponse, e: React.MouseEvent) => {
        e.stopPropagation();
        setSelectedModel(model);
        setShowFeatureImportanceModal(true);
    };

    const handlePredictionAnalysis = (model: MLModelResponse, e: React.MouseEvent) => {
        e.stopPropagation();
        setSelectedModel(model);
        setShowPredictionAnalysisModal(true);
    };

    const handleConfigureAlerts = (model: MLModelResponse, e: React.MouseEvent) => {
        e.stopPropagation();
        setSelectedModel(model);
        setShowAlertConfigModal(true);
    };

    // Mock data for drift visualization
    const driftData = [
        { feature: 'transaction_amount', drift: 0.45, threshold: 0.3, status: 'high' },
        { feature: 'user_age', drift: 0.15, threshold: 0.3, status: 'normal' },
        { feature: 'location_distance', drift: 0.32, threshold: 0.3, status: 'medium' },
        { feature: 'device_type', drift: 0.08, threshold: 0.3, status: 'normal' },
    ];

    // Mock feature importance data
    const featureImportance = [
        { feature: 'transaction_amount', importance: 0.42, change: 0.05 },
        { feature: 'user_history_score', importance: 0.28, change: -0.02 },
        { feature: 'location_distance', importance: 0.18, change: 0.08 },
        { feature: 'device_type', importance: 0.12, change: -0.01 },
    ];

    // Mock prediction error patterns
    const predictionErrors = [
        { category: 'False Positives', count: 45, percentage: 15.2 },
        { category: 'False Negatives', count: 23, percentage: 7.8 },
        { category: 'High Confidence Errors', count: 12, percentage: 4.1 },
        { category: 'Low Confidence Errors', count: 31, percentage: 10.5 },
    ];

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">ML Observatory</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">
                        Monitor machine learning models in production
                    </p>
                </div>
                <button
                    onClick={() => {
                        setCompareMode(!compareMode);
                        setSelectedModelsForCompare([]);
                    }}
                    className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                        compareMode
                            ? 'bg-blue-600 text-white hover:bg-blue-700'
                            : 'border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                >
                    <GitCompare className="h-4 w-4" />
                    {compareMode ? `Compare (${selectedModelsForCompare.length}/3)` : 'Compare Models'}
                </button>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <StatCard label="Total Models" value={stats.total} icon={<Brain className="h-5 w-5" />} />
                <StatCard label="Healthy" value={stats.healthy} icon={<CheckCircle className="h-5 w-5 text-green-500" />} />
                <StatCard label="Degraded" value={stats.degraded} icon={<AlertTriangle className="h-5 w-5 text-amber-500" />} />
                <StatCard label="Failed" value={stats.failed} icon={<AlertTriangle className="h-5 w-5 text-red-500" />} />
            </div>

            {/* Filters */}
            <div className="flex items-center gap-2">
                <button
                    onClick={() => setFilterStatus('all')}
                    className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                        filterStatus === 'all'
                            ? 'bg-blue-600 text-white'
                            : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                >
                    All Models
                </button>
                <button
                    onClick={() => setFilterStatus('healthy')}
                    className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                        filterStatus === 'healthy'
                            ? 'bg-green-600 text-white'
                            : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                >
                    Healthy
                </button>
                <button
                    onClick={() => setFilterStatus('degraded')}
                    className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                        filterStatus === 'degraded'
                            ? 'bg-amber-600 text-white'
                            : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                >
                    Degraded
                </button>
                <button
                    onClick={() => setFilterStatus('failed')}
                    className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                        filterStatus === 'failed'
                            ? 'bg-red-600 text-white'
                            : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                >
                    Failed
                </button>
            </div>

            {/* Models List */}
            {isLoading ? (
                <div className="text-center py-8 text-slate-600 dark:text-neutral-400">
                    Loading ML models...
                </div>
            ) : models.length === 0 ? (
                <div className="text-center py-12 bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700">
                    <Brain className="h-12 w-12 text-slate-400 mx-auto mb-4" />
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                        No Models Found
                    </h3>
                    <p className="text-sm text-slate-500 dark:text-neutral-500">
                        {filterStatus !== 'all' 
                            ? `No ${filterStatus} models available`
                            : 'No machine learning models deployed yet'}
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    {models.map((model) => {
                        return (
                            <div
                                key={model.id}
                                onClick={() => handleModelClick(model)}
                                className={`bg-white dark:bg-slate-900 border rounded-lg p-6 hover:shadow-lg dark:hover:shadow-slate-700/30 transition-all cursor-pointer ${
                                    compareMode && selectedModelsForCompare.includes(model.id)
                                        ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                                        : 'border-slate-200 dark:border-slate-700'
                                }`}
                            >
                                <div className="flex items-start justify-between mb-4">
                                    <div className="flex items-start gap-3">
                                        <div className={`p-2 rounded-lg ${statusConfig[model.status].bg}`}>
                                            <Brain className={`h-5 w-5 ${statusConfig[model.status].color}`} />
                                        </div>
                                        <div>
                                            <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                {model.name}
                                            </h3>
                                            <p className="text-sm text-slate-500 dark:text-neutral-500">
                                                {model.version}
                                            </p>
                                        </div>
                                    </div>
                                    <Badge variant={statusConfig[model.status].variant}>
                                        {model.status}
                                    </Badge>
                                </div>

                                {/* Metrics Grid */}
                                <div className="grid grid-cols-2 gap-4 mb-4">
                                    <div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Accuracy</div>
                                        <div className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                            {((model.metrics.accuracy || 0) * 100).toFixed(1)}%
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">F1 Score</div>
                                        <div className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                            {(model.metrics.f1Score || 0).toFixed(3)}
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Latency P99</div>
                                        <div className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                            {model.metrics.latencyP99 || 0}ms
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1 flex items-center gap-1">
                                            <Activity className="h-3 w-3" />
                                            Drift
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <div className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                                {((model.metrics.drift || 0) * 100).toFixed(1)}%
                                            </div>
                                            {(model.metrics.drift || 0) > 0.3 ? (
                                                <TrendingUp className="h-4 w-4 text-red-500" />
                                            ) : (
                                                <TrendingDown className="h-4 w-4 text-green-500" />
                                            )}
                                        </div>
                                    </div>
                                </div>

                                {/* Footer */}
                                <div className="pt-4 border-t border-slate-200 dark:border-slate-700 space-y-3">
                                    <div className="flex items-center justify-between">
                                        <div className="text-xs text-slate-500 dark:text-neutral-500">
                                            Last deployed: {new Date(model.lastDeployedAt).toLocaleDateString()}
                                        </div>
                                        {model.relatedIncidents.length > 0 && (
                                            <div className="flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                                                <AlertTriangle className="h-3 w-3" />
                                                {model.relatedIncidents.length} incidents
                                            </div>
                                        )}
                                    </div>
                                    
                                    {/* Action Buttons */}
                                    {!compareMode && (
                                        <div className="flex items-center gap-2">
                                            <button
                                                onClick={(e) => handleDriftAnalysis(model, e)}
                                                className="flex-1 inline-flex items-center justify-center gap-1 px-3 py-1.5 text-xs text-slate-600 dark:text-neutral-400 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-md transition-colors border border-slate-200 dark:border-slate-700"
                                                title="Data Drift Analysis"
                                            >
                                                <DriftIcon className="h-3 w-3" />
                                                Drift
                                            </button>
                                            <button
                                                onClick={(e) => handleFeatureImportance(model, e)}
                                                className="flex-1 inline-flex items-center justify-center gap-1 px-3 py-1.5 text-xs text-slate-600 dark:text-neutral-400 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-md transition-colors border border-slate-200 dark:border-slate-700"
                                                title="Feature Importance"
                                            >
                                                <BarChart3 className="h-3 w-3" />
                                                Features
                                            </button>
                                            <button
                                                onClick={(e) => handlePredictionAnalysis(model, e)}
                                                className="flex-1 inline-flex items-center justify-center gap-1 px-3 py-1.5 text-xs text-slate-600 dark:text-neutral-400 hover:bg-slate-50 dark:hover:bg-slate-800 rounded-md transition-colors border border-slate-200 dark:border-slate-700"
                                                title="Prediction Analysis"
                                            >
                                                <Eye className="h-3 w-3" />
                                                Predictions
                                            </button>
                                            <button
                                                onClick={(e) => handleConfigureAlerts(model, e)}
                                                className="flex-1 inline-flex items-center justify-center gap-1 px-3 py-1.5 text-xs text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-md transition-colors border border-blue-200 dark:border-blue-700"
                                                title="Configure Alerts"
                                            >
                                                <Bell className="h-3 w-3" />
                                                Alerts
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Data Drift Modal */}
            {showDriftModal && selectedModel && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" onClick={() => setShowDriftModal(false)}>
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 max-w-2xl w-full p-6" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                Data Drift Analysis
                            </h2>
                            <button
                                onClick={() => setShowDriftModal(false)}
                                className="text-slate-400 hover:text-slate-600 dark:hover:text-neutral-300"
                            >
                                ×
                            </button>
                        </div>
                        
                        <div className="mb-4">
                            <div className="text-sm text-slate-600 dark:text-neutral-400 mb-2">
                                Model: <span className="font-semibold text-slate-900 dark:text-neutral-100">{selectedModel.name}</span> (v{selectedModel.version})
                            </div>
                            <div className="text-sm text-slate-600 dark:text-neutral-400">
                                Overall Drift Score: <span className="font-semibold text-amber-600 dark:text-amber-400">{((selectedModel.metrics.drift || 0) * 100).toFixed(1)}%</span>
                            </div>
                        </div>

                        <div className="space-y-3">
                            {driftData.map((item) => (
                                <div key={item.feature} className="border border-slate-200 dark:border-slate-700 rounded-lg p-4">
                                    <div className="flex items-center justify-between mb-2">
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {item.feature.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}
                                        </div>
                                        <Badge variant={item.status === 'high' ? 'danger' : item.status === 'medium' ? 'warning' : 'success'}>
                                            {item.status}
                                        </Badge>
                                    </div>
                                    <div className="flex items-center gap-4">
                                        <div className="flex-1">
                                            <div className="h-2 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
                                                <div 
                                                    className={`h-full ${ 
                                                        item.status === 'high' ? 'bg-red-500' : 
                                                        item.status === 'medium' ? 'bg-amber-500' : 
                                                        'bg-green-500'
                                                    }`}
                                                    style={{ width: `${(item.drift / 0.5) * 100}%` }}
                                                />
                                            </div>
                                        </div>
                                        <div className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                            {(item.drift * 100).toFixed(1)}%
                                        </div>
                                    </div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                        Threshold: {(item.threshold * 100).toFixed(0)}%
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-700">
                            <div className="flex items-start gap-2">
                                <Sparkles className="h-5 w-5 text-blue-600 dark:text-blue-400 mt-0.5" />
                                <div>
                                    <div className="text-sm font-medium text-blue-900 dark:text-blue-100 mb-1">
                                        Recommendation
                                    </div>
                                    <div className="text-sm text-blue-700 dark:text-blue-300">
                                        High drift detected in transaction_amount. Consider retraining the model with recent data to maintain accuracy.
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Feature Importance Modal */}
            {showFeatureImportanceModal && selectedModel && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" onClick={() => setShowFeatureImportanceModal(false)}>
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 max-w-2xl w-full p-6" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                Feature Importance
                            </h2>
                            <button
                                onClick={() => setShowFeatureImportanceModal(false)}
                                className="text-slate-400 hover:text-slate-600 dark:hover:text-neutral-300"
                            >
                                ×
                            </button>
                        </div>

                        <div className="mb-4">
                            <div className="text-sm text-slate-600 dark:text-neutral-400">
                                Model: <span className="font-semibold text-slate-900 dark:text-neutral-100">{selectedModel.name}</span> (v{selectedModel.version})
                            </div>
                        </div>

                        <div className="space-y-3">
                            {featureImportance.map((item) => (
                                <div key={item.feature} className="border border-slate-200 dark:border-slate-700 rounded-lg p-4">
                                    <div className="flex items-center justify-between mb-2">
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            {item.feature.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <span className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                                                {(item.importance * 100).toFixed(1)}%
                                            </span>
                                            {item.change > 0 ? (
                                                <div className="flex items-center gap-1 text-xs text-green-600 dark:text-green-400">
                                                    <TrendingUp className="h-3 w-3" />
                                                    +{(item.change * 100).toFixed(1)}%
                                                </div>
                                            ) : item.change < 0 ? (
                                                <div className="flex items-center gap-1 text-xs text-red-600 dark:text-red-400">
                                                    <TrendingDown className="h-3 w-3" />
                                                    {(item.change * 100).toFixed(1)}%
                                                </div>
                                            ) : null}
                                        </div>
                                    </div>
                                    <div className="h-2 bg-slate-200 dark:bg-slate-700 rounded-full overflow-hidden">
                                        <div 
                                            className="h-full bg-blue-600"
                                            style={{ width: `${item.importance * 100}%` }}
                                        />
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="mt-6 text-xs text-slate-500 dark:text-neutral-500">
                            Change percentage shows the difference from the previous model version.
                        </div>
                    </div>
                </div>
            )}

            {/* Prediction Analysis Modal */}
            {showPredictionAnalysisModal && selectedModel && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" onClick={() => setShowPredictionAnalysisModal(false)}>
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 max-w-2xl w-full p-6" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                Prediction Error Analysis
                            </h2>
                            <button
                                onClick={() => setShowPredictionAnalysisModal(false)}
                                className="text-slate-400 hover:text-slate-600 dark:hover:text-neutral-300"
                            >
                                ×
                            </button>
                        </div>

                        <div className="mb-4">
                            <div className="text-sm text-slate-600 dark:text-neutral-400 mb-2">
                                Model: <span className="font-semibold text-slate-900 dark:text-neutral-100">{selectedModel.name}</span> (v{selectedModel.version})
                            </div>
                            <div className="text-sm text-slate-600 dark:text-neutral-400">
                                Accuracy: <span className="font-semibold text-slate-900 dark:text-neutral-100">{((selectedModel.metrics.accuracy || 0) * 100).toFixed(1)}%</span>
                            </div>
                        </div>

                        <div className="grid grid-cols-2 gap-3 mb-6">
                            {predictionErrors.map((error) => (
                                <div key={error.category} className="border border-slate-200 dark:border-slate-700 rounded-lg p-4">
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">
                                        {error.category}
                                    </div>
                                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                        {error.count}
                                    </div>
                                    <div className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                        {error.percentage.toFixed(1)}% of predictions
                                    </div>
                                </div>
                            ))}
                        </div>

                        <div className="border-t border-slate-200 dark:border-slate-700 pt-4">
                            <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100 mb-3">
                                Performance Metrics
                            </h3>
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Precision</div>
                                    <div className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                        {((selectedModel.metrics.precision || 0) * 100).toFixed(1)}%
                                    </div>
                                </div>
                                <div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Recall</div>
                                    <div className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                        {((selectedModel.metrics.recall || 0) * 100).toFixed(1)}%
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Alert Configuration Modal */}
            {showAlertConfigModal && selectedModel && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" onClick={() => setShowAlertConfigModal(false)}>
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 max-w-2xl w-full p-6" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                Configure Alerts
                            </h2>
                            <button
                                onClick={() => setShowAlertConfigModal(false)}
                                className="text-slate-400 hover:text-slate-600 dark:hover:text-neutral-300"
                            >
                                ×
                            </button>
                        </div>

                        <div className="mb-6">
                            <div className="text-sm text-slate-600 dark:text-neutral-400">
                                Model: <span className="font-semibold text-slate-900 dark:text-neutral-100">{selectedModel.name}</span> (v{selectedModel.version})
                            </div>
                        </div>

                        <div className="space-y-4">
                            {/* Accuracy Degradation Alert */}
                            <div className="border border-slate-200 dark:border-slate-700 rounded-lg p-4">
                                <div className="flex items-center justify-between mb-3">
                                    <div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            Accuracy Degradation
                                        </div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                            Alert when accuracy drops below threshold
                                        </div>
                                    </div>
                                    <input type="checkbox" className="h-4 w-4" defaultChecked />
                                </div>
                                <input 
                                    type="number" 
                                    defaultValue="90"
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                    placeholder="Threshold (%)"
                                />
                            </div>

                            {/* Data Drift Alert */}
                            <div className="border border-slate-200 dark:border-slate-700 rounded-lg p-4">
                                <div className="flex items-center justify-between mb-3">
                                    <div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            Data Drift Detection
                                        </div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                            Alert when data drift exceeds threshold
                                        </div>
                                    </div>
                                    <input type="checkbox" className="h-4 w-4" defaultChecked />
                                </div>
                                <input 
                                    type="number" 
                                    defaultValue="30"
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                    placeholder="Threshold (%)"
                                />
                            </div>

                            {/* Latency Alert */}
                            <div className="border border-slate-200 dark:border-slate-700 rounded-lg p-4">
                                <div className="flex items-center justify-between mb-3">
                                    <div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            High Latency
                                        </div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                            Alert when P99 latency exceeds threshold
                                        </div>
                                    </div>
                                    <input type="checkbox" className="h-4 w-4" defaultChecked />
                                </div>
                                <input 
                                    type="number" 
                                    defaultValue="500"
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                    placeholder="Threshold (ms)"
                                />
                            </div>

                            {/* Error Rate Alert */}
                            <div className="border border-slate-200 dark:border-slate-700 rounded-lg p-4">
                                <div className="flex items-center justify-between mb-3">
                                    <div>
                                        <div className="font-medium text-slate-900 dark:text-neutral-100">
                                            High Error Rate
                                        </div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mt-1">
                                            Alert when prediction errors spike
                                        </div>
                                    </div>
                                    <input type="checkbox" className="h-4 w-4" />
                                </div>
                                <input 
                                    type="number" 
                                    defaultValue="10"
                                    className="w-full px-3 py-2 border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-neutral-100 text-sm"
                                    placeholder="Threshold (%)"
                                />
                            </div>
                        </div>

                        <div className="flex items-center justify-end gap-3 mt-6 pt-6 border-t border-slate-200 dark:border-slate-700">
                            <button
                                onClick={() => setShowAlertConfigModal(false)}
                                className="px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-md hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => {
                                    // TODO: Save alert configuration
                                    setShowAlertConfigModal(false);
                                }}
                                className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors font-medium"
                            >
                                Save Alerts
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// Helper component
function StatCard({ label, value, icon }: { label: string; value: number; icon: React.ReactNode }) {
    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center justify-between">
                <div>
                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{value}</div>
                    <div className="text-sm text-slate-600 dark:text-neutral-400 mt-1">{label}</div>
                </div>
                <div className="text-slate-400 dark:text-neutral-500">
                    {icon}
                </div>
            </div>
        </div>
    );
}
