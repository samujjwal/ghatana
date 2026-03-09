import { memo } from 'react';

/**
 * Compliance posture and regulatory status dashboard.
 *
 * <p><b>Purpose</b><br>
 * Shows compliance status against multiple regulatory frameworks (SOC2, ISO27001, GDPR, HIPAA, etc.).
 * Tracks control implementation and audit progress.
 *
 * <p><b>Features</b><br>
 * - Compliance status by framework
 * - Control implementation percentage
 * - Audit timeline
 * - Gap analysis
 * - Remediation tracking
 *
 * @doc.type component
 * @doc.purpose Compliance posture tracking
 * @doc.layer product
 * @doc.pattern Organism
 */

interface ComplianceFramework {
    name: string;
    shortName: string;
    status: 'compliant' | 'partial' | 'non-compliant';
    implementedControls: number;
    totalControls: number;
    lastAuditDate: string;
    nextAuditDate: string;
}

interface CompliancePostureProps {
    frameworks?: ComplianceFramework[];
}

export const CompliancePosture = memo(function CompliancePosture({
    frameworks,
}: CompliancePostureProps) {
    // Mock data if none provided
    const data = frameworks || [
        {
            name: 'SOC 2 Type II',
            shortName: 'SOC2',
            status: 'compliant',
            implementedControls: 68,
            totalControls: 70,
            lastAuditDate: new Date(Date.now() - 45 * 24 * 60 * 60 * 1000).toISOString(),
            nextAuditDate: new Date(Date.now() + 230 * 24 * 60 * 60 * 1000).toISOString(),
        },
        {
            name: 'ISO/IEC 27001',
            shortName: 'ISO27001',
            status: 'compliant',
            implementedControls: 114,
            totalControls: 114,
            lastAuditDate: new Date(Date.now() - 120 * 24 * 60 * 60 * 1000).toISOString(),
            nextAuditDate: new Date(Date.now() + 150 * 24 * 60 * 60 * 1000).toISOString(),
        },
        {
            name: 'GDPR',
            shortName: 'GDPR',
            status: 'partial',
            implementedControls: 18,
            totalControls: 20,
            lastAuditDate: new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString(),
            nextAuditDate: new Date(Date.now() + 180 * 24 * 60 * 60 * 1000).toISOString(),
        },
        {
            name: 'HIPAA',
            shortName: 'HIPAA',
            status: 'compliant',
            implementedControls: 86,
            totalControls: 88,
            lastAuditDate: new Date(Date.now() - 200 * 24 * 60 * 60 * 1000).toISOString(),
            nextAuditDate: new Date(Date.now() + 75 * 24 * 60 * 60 * 1000).toISOString(),
        },
    ];

    const getStatusIcon = (status: ComplianceFramework['status']) => {
        switch (status) {
            case 'compliant':
                return '✅';
            case 'partial':
                return '⚠️';
            case 'non-compliant':
                return '❌';
            default:
                return '•';
        }
    };

    const getStatusColor = (status: ComplianceFramework['status']) => {
        switch (status) {
            case 'compliant':
                return 'bg-green-50 border-green-200 dark:bg-green-600/30 dark:border-green-800';
            case 'partial':
                return 'bg-yellow-50 border-yellow-200 dark:bg-orange-600/30 dark:border-yellow-800';
            case 'non-compliant':
                return 'bg-red-50 border-red-200 dark:bg-rose-600/30 dark:border-red-800';
            default:
                return 'bg-slate-50 border-slate-200 dark:bg-neutral-800 dark:border-neutral-600';
        }
    };

    const getProgressBarColor = (status: ComplianceFramework['status']) => {
        switch (status) {
            case 'compliant':
                return 'bg-green-500';
            case 'partial':
                return 'bg-yellow-500';
            case 'non-compliant':
                return 'bg-red-500';
            default:
                return 'bg-slate-500';
        }
    };

    const overallCompliance = Math.round(
        (data.reduce((sum, f) => sum + f.implementedControls, 0) /
            data.reduce((sum, f) => sum + f.totalControls, 0)) *
        100
    );

    return (
        <div className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
            {/* Header with Overall Status */}
            <div className="space-y-3">
                <div className="flex items-center justify-between">
                    <h2 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                        🛡️ Compliance Posture
                    </h2>
                    <div className="text-right">
                        <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                            {overallCompliance}%
                        </div>
                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                            Overall Compliance
                        </p>
                    </div>
                </div>

                {/* Overall Progress Bar */}
                <div className="space-y-1">
                    <div className="h-2 w-full rounded-full bg-slate-200 dark:bg-neutral-700">
                        <div
                            className="h-full rounded-full bg-gradient-to-r from-green-400 to-green-600"
                            style={{ width: `${overallCompliance}%` }}
                        />
                    </div>
                    <p className="text-xs text-slate-500 dark:text-neutral-400">
                        {data.reduce((sum, f) => sum + f.implementedControls, 0)} of{' '}
                        {data.reduce((sum, f) => sum + f.totalControls, 0)} controls implemented
                    </p>
                </div>
            </div>

            {/* Frameworks Grid */}
            <div className="border-t border-slate-200 pt-4 dark:border-neutral-600">
                <div className="space-y-3">
                    {data.map((framework) => {
                        const percentage = Math.round((framework.implementedControls / framework.totalControls) * 100);
                        const daysUntilAudit = Math.ceil(
                            (new Date(framework.nextAuditDate).getTime() - Date.now()) /
                            (24 * 60 * 60 * 1000)
                        );

                        return (
                            <div
                                key={framework.shortName}
                                className={`rounded border p-3 ${getStatusColor(framework.status)}`}
                            >
                                <div className="space-y-2">
                                    {/* Framework Header */}
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-2">
                                            <span className="text-lg">{getStatusIcon(framework.status)}</span>
                                            <div>
                                                <h3 className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {framework.name}
                                                </h3>
                                                <p className="text-xs text-slate-600 dark:text-neutral-400">
                                                    {framework.implementedControls}/{framework.totalControls} controls
                                                </p>
                                            </div>
                                        </div>
                                        <div className="text-right">
                                            <div className="font-semibold text-slate-900 dark:text-neutral-100">
                                                {percentage}%
                                            </div>
                                            <p className="text-xs text-slate-600 dark:text-neutral-400">
                                                {framework.status === 'compliant' ? 'Compliant' : 'In Progress'}
                                            </p>
                                        </div>
                                    </div>

                                    {/* Progress Bar */}
                                    <div className="h-1.5 w-full rounded-full bg-slate-200 dark:bg-neutral-700">
                                        <div
                                            className={`h-full rounded-full ${getProgressBarColor(framework.status)}`}
                                            style={{ width: `${percentage}%` }}
                                        />
                                    </div>

                                    {/* Audit Info */}
                                    <div className="flex justify-between text-xs text-slate-600 dark:text-neutral-400">
                                        <span>
                                            Last: {new Date(framework.lastAuditDate).toLocaleDateString()}
                                        </span>
                                        <span>
                                            Next: {daysUntilAudit} days
                                        </span>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Action Buttons */}
            <div className="flex gap-2 border-t border-slate-200 pt-4 dark:border-neutral-600">
                <button className="flex-1 rounded bg-blue-600 px-3 py-2 text-xs font-medium text-white hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-800">
                    📋 View Gap Analysis
                </button>
                <button className="flex-1 rounded border border-slate-300 px-3 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 dark:border-neutral-600 dark:text-neutral-300 dark:hover:bg-slate-800">
                    📅 Schedule Audit
                </button>
            </div>
        </div>
    );
});
