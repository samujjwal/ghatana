import { useAtomValue } from 'jotai';
import { Link } from 'react-router';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import type { StageHealth, StageStatus } from '@/types/devsecops';
import { useStages } from '@/hooks/useConfig';
import { getStageMetadata } from '@/lib/devsecops/stageMetadata';
import { CheckCircle2, AlertTriangle, XCircle, Clock } from 'lucide-react';

/**
 * DevSecOps Stage Strip
 *
 * <p><b>Purpose</b><br>
 * Horizontal navigation strip showing all DevSecOps stages with health indicators.
 * Provides quick visual overview of pipeline status and navigation to stage pages.
 *
 * <p><b>Features</b><br>
 * - Visual stage progression with status indicators
 * - Color-coded health status (on-track, at-risk, blocked)
 * - Click to navigate to stage detail page
 * - Responsive design
 *
 * @doc.type component
 * @doc.purpose DevSecOps stage navigation
 * @doc.layer product
 * @doc.pattern Navigation
 */

interface DevSecOpsStageStripProps {
    stagesHealth?: StageHealth[] | Record<string, StageHealth>;
    currentStage?: string;
    onStageClick?: (stageKey: string) => void;
}

export function DevSecOpsStageStrip({
    stagesHealth = [],
    currentStage,
    onStageClick,
}: DevSecOpsStageStripProps) {
    const selectedTenant = useAtomValue(selectedTenantAtom);
    const { data: stageMappings, isLoading } = useStages();

    // Enrich backend stages with display metadata
    const stages = (stageMappings || []).map(mapping => ({
        ...mapping,
        ...getStageMetadata(mapping.stage)
    })).sort((a, b) => (a.order || 0) - (b.order || 0));

    if (isLoading) {
        return (
            <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
                <div className="text-sm text-slate-500 dark:text-neutral-500">Loading stages...</div>
            </div>
        );
    }

    const getStageHealth = (stageKey: string): StageHealth | undefined => {
        const stageHealthList: StageHealth[] = Array.isArray(stagesHealth)
            ? stagesHealth
            : Object.values(stagesHealth);

        return stageHealthList.find((h) => h.stage === stageKey);
    };

    const getStatusColor = (status: StageStatus): string => {
        switch (status) {
            case 'on-track':
                return 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 border-green-300 dark:border-green-700';
            case 'at-risk':
                return 'bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300 border-amber-300 dark:border-amber-700';
            case 'blocked':
                return 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 border-red-300 dark:border-red-700';
            case 'completed':
                return 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 border-blue-300 dark:border-blue-700';
            default:
                return 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-neutral-300 border-slate-300 dark:border-slate-700';
        }
    };

    const getStatusIcon = (status: StageStatus) => {
        switch (status) {
            case 'on-track':
                return CheckCircle2;
            case 'at-risk':
                return AlertTriangle;
            case 'blocked':
                return XCircle;
            case 'completed':
                return CheckCircle2;
            default:
                return Clock;
        }
    };

    return (
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-4">
            <div className="flex items-center gap-2 mb-3">
                <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                    DevSecOps Pipeline
                </h3>
                {selectedTenant && (
                    <span className="text-xs text-slate-500 dark:text-neutral-500">
                        ({selectedTenant})
                    </span>
                )}
            </div>

            <div className="flex items-center gap-2 overflow-x-auto pb-2">
                {stages.map((stage, index) => {
                    const health = getStageHealth(stage.stage);
                    const status = health?.status || 'on-track';
                    const StatusIcon = getStatusIcon(status);
                    const isActive = currentStage === stage.stage;

                    return (
                        <div key={stage.stage} className="flex items-center gap-2 flex-shrink-0">
                            <Link
                                to={`/operate/stages/${stage.stage}`}
                                onClick={() => onStageClick?.(stage.stage)}
                                className={`relative flex items-center gap-2 px-4 py-2 rounded-lg border-2 transition-all ${isActive
                                        ? 'ring-2 ring-blue-500 ring-offset-2 dark:ring-offset-slate-900'
                                        : ''
                                    } ${getStatusColor(status)} hover:shadow-md`}
                            >
                                <StatusIcon className="h-4 w-4 flex-shrink-0" />
                                <div className="flex flex-col min-w-0">
                                    <span className="text-sm font-medium truncate">{stage.label}</span>
                                    {health && (
                                        <span className="text-xs opacity-75">
                                            {health.itemsCompleted}/{health.itemsTotal}
                                        </span>
                                    )}
                                </div>
                                {health && health.criticalIssues > 0 && (
                                    <span className="absolute -top-1 -right-1 flex h-5 w-5 items-center justify-center rounded-full bg-red-600 text-xs font-bold text-white">
                                        {health.criticalIssues}
                                    </span>
                                )}
                            </Link>

                            {index < stages.length - 1 && (
                                <div className="w-8 h-0.5 bg-slate-300 dark:bg-slate-700" />
                            )}
                        </div>
                    );
                })}
            </div>

            {/* Legend */}
            <div className="flex items-center gap-4 mt-4 text-xs text-slate-600 dark:text-neutral-400">
                <div className="flex items-center gap-1">
                    <CheckCircle2 className="h-3 w-3 text-green-600" />
                    <span>On Track</span>
                </div>
                <div className="flex items-center gap-1">
                    <AlertTriangle className="h-3 w-3 text-amber-600" />
                    <span>At Risk</span>
                </div>
                <div className="flex items-center gap-1">
                    <XCircle className="h-3 w-3 text-red-600" />
                    <span>Blocked</span>
                </div>
            </div>
        </div>
    );
}
