import { memo } from 'react';
import { useComplianceStatus } from '@/hooks/useSecurityData';

/**
 * Compliance status tracking and audit checklist panel.
 *
 * <p><b>Purpose</b><br>
 * Displays compliance status for SOC2, GDPR, HIPAA, and other regulations.
 * Tracks compliance items, last audit dates, and outstanding requirements.
 *
 * <p><b>Features</b><br>
 * - Compliance framework status (SOC2, GDPR, HIPAA, ISO 27001)
 * - Compliance checklist items
 * - Last audit date and results
 * - Outstanding requirements
 * - Evidence tracking
 * - Audit history
 *
 * @doc.type component
 * @doc.purpose Compliance status panel
 * @doc.layer product
 * @doc.pattern Panel
 */

interface ComplianceFramework {
    framework: string;
    status: 'compliant' | 'partially' | 'non-compliant';
    completeness: number;
    lastAudit: string;
    nextAudit: string;
    issues: number;
}

interface ComplianceChecklistItem {
    id: string;
    status: 'complete' | 'pending' | 'failed';
    title: string;
    framework: string;
    dueDate: string;
    evidence: string[];
}

const getStatusColor = (status: string) => {
    switch (status) {
        case 'compliant':
            return 'bg-green-950 text-green-400';
        case 'partially':
            return 'bg-yellow-950 text-yellow-400';
        case 'non-compliant':
            return 'bg-red-950 text-red-400';
        case 'complete':
            return 'bg-green-950 text-green-400';
        case 'pending':
            return 'bg-yellow-950 text-yellow-400';
        case 'failed':
            return 'bg-red-950 text-red-400';
        default:
            return 'bg-slate-700 text-slate-400';
    }
};

export const ComplianceStatus = memo(function ComplianceStatus() {
    // GIVEN: Security dashboard opened on compliance tab
    // WHEN: Component renders
    // THEN: Display compliance status and checklist

    const { data: complianceData } = useComplianceStatus({
        refetchInterval: 30000,
    });

    const frameworks: ComplianceFramework[] = complianceData?.frameworks || [];
    const checklist: ComplianceChecklistItem[] = complianceData?.checklist || [];

    const overallCompliance = frameworks.length > 0
        ? Math.round(frameworks.reduce((sum, f) => sum + (f.completeness || 0), 0) / frameworks.length)
        : 0;

    return (
        <div className="space-y-6">
            {/* Overall Compliance */}
            <div className="bg-slate-800 rounded-lg p-4">
                <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-semibold text-white">Overall Compliance Score</h3>
                    <span className="text-4xl font-bold text-green-400">{overallCompliance}%</span>
                </div>

                <div className="w-full bg-slate-700 rounded-full h-4">
                    <div
                        className="h-full bg-green-500 rounded-full transition-all"
                        style={{ width: `${overallCompliance}%` }}
                    />
                </div>

                <div className="grid grid-cols-4 gap-2 mt-4">
                    <div className="text-center">
                        <div className="text-xs text-slate-500 mb-1">Target</div>
                        <div className="text-lg font-bold text-slate-200">95%</div>
                    </div>
                    <div className="text-center">
                        <div className="text-xs text-slate-500 mb-1">Frameworks</div>
                        <div className="text-lg font-bold text-slate-200">{frameworks.length}</div>
                    </div>
                    <div className="text-center">
                        <div className="text-xs text-slate-500 mb-1">Issues</div>
                        <div className="text-lg font-bold text-red-400">
                            {frameworks.reduce((sum, f) => sum + (f.issues || 0), 0)}
                        </div>
                    </div>
                    <div className="text-center">
                        <div className="text-xs text-slate-500 mb-1">Last Audit</div>
                        <div className="text-lg font-bold text-slate-200">7d ago</div>
                    </div>
                </div>
            </div>

            {/* Frameworks */}
            <div>
                <h3 className="text-lg font-semibold text-white mb-3">Compliance Frameworks</h3>
                <div className="space-y-2">
                    {frameworks.map((item) => (
                        <div key={item.framework} className="bg-slate-800 rounded-lg p-4">
                            <div className="flex items-center justify-between mb-2">
                                <span className="font-medium text-slate-200">{item.framework}</span>
                                <span className={`px-2 py-1 rounded text-xs font-medium ${getStatusColor(item.status)}`}>
                                    {item.status.toUpperCase()}
                                </span>
                            </div>

                            <div className="flex items-center gap-2 mb-2">
                                <div className="flex-1 bg-slate-700 rounded-full h-2">
                                    <div
                                        className={`h-full rounded-full transition-all ${item.status === 'compliant'
                                            ? 'bg-green-500'
                                            : item.status === 'partially'
                                                ? 'bg-yellow-500'
                                                : 'bg-red-500'
                                            }`}
                                        style={{ width: `${item.completeness}%` }}
                                    />
                                </div>
                                <span className="text-xs font-mono text-slate-400">{item.completeness}%</span>
                            </div>

                            <div className="grid grid-cols-3 gap-2 text-xs text-slate-500">
                                <div>Last audit: {item.lastAudit}</div>
                                <div>Next: {item.nextAudit}</div>
                                <div>{item.issues} open issues</div>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Checklist */}
            <div>
                <h3 className="text-lg font-semibold text-white mb-3">Compliance Checklist</h3>
                <div className="space-y-2">
                    {checklist.map((item) => (
                        <div key={item.id} className="bg-slate-800 rounded-lg p-3">
                            <div className="flex items-start gap-3">
                                <div className="mt-1">
                                    {item.status === 'complete' && <span className="text-xl">✓</span>}
                                    {item.status === 'pending' && <span className="text-xl">⏳</span>}
                                    {item.status === 'failed' && <span className="text-xl">❌</span>}
                                </div>

                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 mb-1">
                                        <span className="font-medium text-slate-200">{item.title}</span>
                                        <span className="text-xs text-slate-500">{item.framework}</span>
                                    </div>

                                    {item.evidence && item.evidence.length > 0 && (
                                        <div className="flex gap-1 flex-wrap mb-1">
                                            {item.evidence.map((ev: string) => (
                                                <span key={ev} className="px-2 py-0.5 bg-slate-700 rounded text-xs text-slate-400">
                                                    {ev}
                                                </span>
                                            ))}
                                        </div>
                                    )}

                                    <div className="text-xs text-slate-500">Due: {item.dueDate}</div>
                                </div>

                                <span className={`px-2 py-1 rounded text-xs font-medium whitespace-nowrap ${getStatusColor(item.status)}`}>
                                    {item.status.toUpperCase()}
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
});

export default ComplianceStatus;
