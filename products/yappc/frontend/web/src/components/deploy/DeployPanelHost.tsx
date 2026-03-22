/**
 * Deploy Panel Host Component
 *
 * URL-driven panel router for the Deploy page using ?segment= query parameter.
 * Manages the three segments: Configure, Deployments, and Health.
 *
 * @doc.type component
 * @doc.purpose Deploy surface segment router
 * @doc.layer product
 * @doc.pattern Container Component
 */

import React, { useCallback, Suspense, lazy } from 'react';
import { useSearchParams } from 'react-router';
import { Settings, Rocket as RocketLaunch, Heart as Favorite } from 'lucide-react';

// Lazy load panels for code splitting
const DeliveryPlanEditor = lazy(() =>
    import('./DeliveryPlanEditor').then((m) => ({ default: m.DeliveryPlanEditor })),
);
const ReleaseStrategyConfigurator = lazy(() =>
    import('./ReleaseStrategyConfigurator').then((m) => ({ default: m.ReleaseStrategyConfigurator })),
);
const BuildProgressTracker = lazy(() =>
    import('./BuildProgressTracker').then((m) => ({ default: m.BuildProgressTracker })),
);
const ReleasePacketPanel = lazy(() =>
    import('./ReleasePacketPanel').then((m) => ({ default: m.ReleasePacketPanel })),
);
const HealthPanel = lazy(() =>
    import('../observe/HealthPanel').then((m) => ({ default: m.HealthPanel })),
);
const IncidentsPanel = lazy(() =>
    import('../observe/IncidentsPanel').then((m) => ({ default: m.IncidentsPanel })),
);

export type DeploySegment = 'configure' | 'deployments' | 'health';

const SEGMENT_CONFIG: Record<DeploySegment, { icon: React.ReactNode; label: string; panels: string[] }> = {
    configure: {
        icon: <Settings className="w-4 h-4" />,
        label: 'Configure',
        panels: ['delivery-plan', 'release-strategy'],
    },
    deployments: {
        icon: <RocketLaunch className="w-4 h-4" />,
        label: 'Deployments',
        panels: ['builds', 'releases'],
    },
    health: {
        icon: <Favorite className="w-4 h-4" />,
        label: 'Health',
        panels: ['metrics', 'incidents'],
    },
};

export interface DeployPanelHostProps {
    projectId: string;
    dataContext?: {
        // Configure segment data
        deliveryPlan?: unknown;
        releaseStrategy?: unknown;
        onSaveDeliveryPlan?: (data: unknown) => Promise<void>;
        onSaveReleaseStrategy?: (data: unknown) => Promise<void>;
        // Deployments segment data
        currentBuild?: unknown;
        releasePacket?: unknown;
        evidencePack?: unknown;
        approvalGates?: unknown[];
        onRefreshBuild?: () => Promise<void>;
        onCancelBuild?: () => Promise<void>;
        onRetryBuild?: () => Promise<void>;
        onApprove?: (gateId: string, comments?: string) => Promise<void>;
        onReject?: (gateId: string, reason: string) => Promise<void>;
        // Health segment data
        healthMetrics?: unknown[];
        slos?: unknown[];
        services?: unknown[];
        incidents?: unknown[];
        onRefreshHealth?: () => Promise<void>;
        onCreateIncident?: () => void;
        onUpdateIncidentStatus?: (id: string, status: string) => Promise<void>;
        onAddIncidentNote?: (id: string, note: string) => Promise<void>;
        // Shared
        onAIAssist?: (context: unknown) => Promise<unknown>;
        lastUpdated?: string;
        isPolling?: boolean;
    };
}

const PanelLoadingFallback: React.FC = () => (
    <div className="flex items-center justify-center h-full">
        <div className="animate-pulse text-text-secondary">Loading...</div>
    </div>
);

/**
 * Deploy Panel Host - URL-driven segment router for Deploy page.
 */
export const DeployPanelHost: React.FC<DeployPanelHostProps> = ({ projectId: _projectId, dataContext = {} }) => {
    const [searchParams, setSearchParams] = useSearchParams();
    const currentSegment = (searchParams.get('segment') as DeploySegment) || 'configure';
    const currentPanel = searchParams.get('panel') || null;

    const handleSegmentChange = useCallback(
        (segment: DeploySegment) => {
            const newParams = new URLSearchParams(searchParams);
            newParams.set('segment', segment);
            newParams.delete('panel');
            setSearchParams(newParams);
        },
        [searchParams, setSearchParams],
    );

    const handlePanelChange = useCallback(
        (panel: string | null) => {
            const newParams = new URLSearchParams(searchParams);
            if (panel) {
                newParams.set('panel', panel);
            } else {
                newParams.delete('panel');
            }
            setSearchParams(newParams);
        },
        [searchParams, setSearchParams],
    );

    const noOpAsync = useCallback(async () => { }, []);

    return (
        <div className="flex flex-col h-full">
            {/* Segment Navigation */}
            <div className="flex items-center gap-1 px-4 py-2 bg-grey-50 dark:bg-grey-800/50 border-b border-divider">
                {Object.entries(SEGMENT_CONFIG).map(([segment, config]) => (
                    <button
                        key={segment}
                        onClick={() => handleSegmentChange(segment as DeploySegment)}
                        className={`flex items-center gap-2 px-4 py-2 rounded-lg transition-colors ${currentSegment === segment
                                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                                : 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                    >
                        {config.icon}
                        <span className="font-medium text-sm">{config.label}</span>
                    </button>
                ))}
            </div>

            {/* Panel Navigation (within segment) */}
            <div className="flex items-center gap-2 px-4 py-2 border-b border-divider">
                {SEGMENT_CONFIG[currentSegment].panels.map((panel) => (
                    <button
                        key={panel}
                        onClick={() => handlePanelChange(currentPanel === panel ? null : panel)}
                        className={`px-3 py-1 text-xs rounded transition-colors ${currentPanel === panel
                                ? 'bg-primary-600 text-white'
                                : 'text-text-secondary hover:text-text-primary hover:bg-grey-100 dark:hover:bg-grey-800'
                            }`}
                    >
                        {panel.replace('-', ' ').replace(/\b\w/g, (c) => c.toUpperCase())}
                    </button>
                ))}
            </div>

            {/* Panel Content */}
            <div className="flex-1 overflow-hidden">
                <Suspense fallback={<PanelLoadingFallback />}>
                    {/* Configure Segment */}
                    {currentSegment === 'configure' && !currentPanel && (
                        <div className="p-6 space-y-4">
                            <h2 className="text-lg font-semibold text-text-primary">Configure Deployment</h2>
                            <p className="text-text-secondary">
                                Set up your delivery plan and release strategy before deploying.
                            </p>
                            <div className="grid grid-cols-2 gap-4">
                                <button
                                    onClick={() => handlePanelChange('delivery-plan')}
                                    className="p-4 border border-divider rounded-lg bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50 text-left transition-colors"
                                >
                                    <h3 className="font-medium text-text-primary mb-1">Delivery Plan</h3>
                                    <p className="text-xs text-text-secondary">
                                        Define milestones, tasks, and dependencies
                                    </p>
                                </button>
                                <button
                                    onClick={() => handlePanelChange('release-strategy')}
                                    className="p-4 border border-divider rounded-lg bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50 text-left transition-colors"
                                >
                                    <h3 className="font-medium text-text-primary mb-1">Release Strategy</h3>
                                    <p className="text-xs text-text-secondary">
                                        Configure environments and rollout plan
                                    </p>
                                </button>
                            </div>
                        </div>
                    )}
                    {currentSegment === 'configure' && currentPanel === 'delivery-plan' && (
                        <DeliveryPlanEditor
                            data={dataContext.deliveryPlan as unknown}
                            onSave={dataContext.onSaveDeliveryPlan || noOpAsync}
                            onAIAssist={dataContext.onAIAssist as unknown}
                        />
                    )}
                    {currentSegment === 'configure' && currentPanel === 'release-strategy' && (
                        <ReleaseStrategyConfigurator
                            data={dataContext.releaseStrategy as unknown}
                            onSave={dataContext.onSaveReleaseStrategy || noOpAsync}
                            onAIAssist={dataContext.onAIAssist as unknown}
                        />
                    )}

                    {/* Deployments Segment */}
                    {currentSegment === 'deployments' && !currentPanel && (
                        <div className="p-6 space-y-4">
                            <h2 className="text-lg font-semibold text-text-primary">Deployments</h2>
                            <p className="text-text-secondary">
                                Monitor builds and manage releases.
                            </p>
                            <div className="grid grid-cols-2 gap-4">
                                <button
                                    onClick={() => handlePanelChange('builds')}
                                    className="p-4 border border-divider rounded-lg bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50 text-left transition-colors"
                                >
                                    <h3 className="font-medium text-text-primary mb-1">Build Progress</h3>
                                    <p className="text-xs text-text-secondary">
                                        Track current build status and logs
                                    </p>
                                </button>
                                <button
                                    onClick={() => handlePanelChange('releases')}
                                    className="p-4 border border-divider rounded-lg bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50 text-left transition-colors"
                                >
                                    <h3 className="font-medium text-text-primary mb-1">Release Packet</h3>
                                    <p className="text-xs text-text-secondary">
                                        View artifacts, evidence, and approvals
                                    </p>
                                </button>
                            </div>
                        </div>
                    )}
                    {currentSegment === 'deployments' && currentPanel === 'builds' && dataContext.currentBuild && (
                        <BuildProgressTracker
                            build={dataContext.currentBuild as unknown}
                            onRefresh={dataContext.onRefreshBuild || noOpAsync}
                            onCancel={dataContext.onCancelBuild}
                            onRetry={dataContext.onRetryBuild}
                            isPolling={dataContext.isPolling}
                        />
                    )}
                    {currentSegment === 'deployments' && currentPanel === 'releases' && dataContext.releasePacket && (
                        <ReleasePacketPanel
                            releasePacket={dataContext.releasePacket as unknown}
                            evidencePack={dataContext.evidencePack as unknown}
                            approvalGates={(dataContext.approvalGates as unknown) || []}
                            onApprove={dataContext.onApprove}
                            onReject={dataContext.onReject}
                        />
                    )}

                    {/* Health Segment */}
                    {currentSegment === 'health' && !currentPanel && (
                        <div className="p-6 space-y-4">
                            <h2 className="text-lg font-semibold text-text-primary">System Health</h2>
                            <p className="text-text-secondary">
                                Monitor health metrics and manage incidents.
                            </p>
                            <div className="grid grid-cols-2 gap-4">
                                <button
                                    onClick={() => handlePanelChange('metrics')}
                                    className="p-4 border border-divider rounded-lg bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50 text-left transition-colors"
                                >
                                    <h3 className="font-medium text-text-primary mb-1">Health Metrics</h3>
                                    <p className="text-xs text-text-secondary">
                                        View SLOs, service health, and alerts
                                    </p>
                                </button>
                                <button
                                    onClick={() => handlePanelChange('incidents')}
                                    className="p-4 border border-divider rounded-lg bg-bg-paper hover:bg-grey-50 dark:hover:bg-grey-800/50 text-left transition-colors"
                                >
                                    <h3 className="font-medium text-text-primary mb-1">Incidents</h3>
                                    <p className="text-xs text-text-secondary">
                                        Track and manage active incidents
                                    </p>
                                </button>
                            </div>
                        </div>
                    )}
                    {currentSegment === 'health' && currentPanel === 'metrics' && (
                        <HealthPanel
                            metrics={(dataContext.healthMetrics as unknown) || []}
                            slos={(dataContext.slos as unknown) || []}
                            services={(dataContext.services as unknown) || []}
                            onRefresh={dataContext.onRefreshHealth || noOpAsync}
                            lastUpdated={dataContext.lastUpdated}
                            isPolling={dataContext.isPolling}
                        />
                    )}
                    {currentSegment === 'health' && currentPanel === 'incidents' && (
                        <IncidentsPanel
                            incidents={(dataContext.incidents as unknown) || []}
                            onCreateIncident={dataContext.onCreateIncident}
                            onUpdateStatus={dataContext.onUpdateIncidentStatus as unknown}
                            onAddNote={dataContext.onAddIncidentNote}
                        />
                    )}
                </Suspense>
            </div>
        </div>
    );
};

export default DeployPanelHost;
