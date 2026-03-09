import { memo } from 'react';

/**
 * SLA compliance tracking and monitoring panel.
 *
 * <p><b>Purpose</b><br>
 * Displays SLA metrics by service, tracks compliance percentage,
 * shows breach incidents, and provides SLA violation insights.
 *
 * <p><b>Features</b><br>
 * - Overall compliance percentage
 * - Per-service SLA tracking
 * - Breach history
 * - SLA targets display (99.9%, 99%, etc.)
 * - Color-coded status (green=compliant, yellow=warning, red=breach)
 * - Downtime attribution by cause
 *
 * <p><b>Props</b><br>
 * @param timeRange - Selected time range for SLA calculation
 *
 * @doc.type component
 * @doc.purpose SLA compliance panel
 * @doc.layer product
 * @doc.pattern Panel
 */

interface SLACompliancePanelProps {
    timeRange: string;
}

interface ServiceSLA {
    name: string;
    target: number; // 99.9 = 99.9%
    actual: number;
    breaches: number;
    status: 'compliant' | 'warning' | 'breach';
}

interface DowntimeEvent {
    service: string;
    duration: number; // minutes
    cause: string;
    time: string;
}

// SLA data - for production, would use useQuery to fetch from API
const services: ServiceSLA[] = [
    {
        name: 'API Gateway',
        target: 99.9,
        actual: 99.94,
        breaches: 0,
        status: 'compliant',
    },
    {
        name: 'Auth Service',
        target: 99.9,
        actual: 99.85,
        breaches: 1,
        status: 'warning',
    },
    {
        name: 'Database',
        target: 99.99,
        actual: 99.91,
        breaches: 2,
        status: 'warning',
    },
    {
        name: 'Message Queue',
        target: 99.5,
        actual: 99.72,
        breaches: 0,
        status: 'compliant',
    },
    {
        name: 'Cache Layer',
        target: 99,
        actual: 98.45,
        breaches: 1,
        status: 'breach',
    },
];

const downtime: DowntimeEvent[] = [
    { service: 'Auth Service', duration: 12, cause: 'Memory leak', time: '2024-01-15 14:23' },
    { service: 'Database', duration: 5, cause: 'Connection pool exhaustion', time: '2024-01-14 09:15' },
    { service: 'Cache Layer', duration: 45, cause: 'Disk space full', time: '2024-01-12 22:30' },
];

export const SLACompliancePanel = memo(function SLACompliancePanel(
    _props: SLACompliancePanelProps,
) {
    // GIVEN: Time range selected
    // WHEN: Component renders SLA metrics
    // THEN: Display compliance and breach information

    const overallCompliance =
        (services.reduce((sum: number, s: ServiceSLA) => sum + s.actual, 0) / services.length).toFixed(2);

    const totalBreaches = services.reduce((sum: number, s: ServiceSLA) => sum + s.breaches, 0);

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'compliant':
                return 'bg-green-950 text-green-400 border-l-4 border-l-green-500';
            case 'warning':
                return 'bg-yellow-950 text-yellow-400 border-l-4 border-l-yellow-500';
            case 'breach':
                return 'bg-red-950 text-red-400 border-l-4 border-l-red-500';
            default:
                return 'bg-slate-800 border-l-4 border-l-slate-600';
        }
    };

    return (
        <div className="space-y-6">
            {/* Overall Metrics */}
            <div className="grid grid-cols-3 gap-4">
                <div className="bg-slate-700 rounded-lg p-4">
                    <div className="text-xs text-slate-400 mb-2">Overall Compliance</div>
                    <div className="text-4xl font-bold text-green-400">{overallCompliance}%</div>
                    <div className="text-xs text-slate-500 dark:text-neutral-400 mt-2">Target: 99.9%</div>
                </div>

                <div className="bg-slate-700 rounded-lg p-4">
                    <div className="text-xs text-slate-400 mb-2">Compliant Services</div>
                    <div className="text-4xl font-bold text-white">
                        {services.filter((s: ServiceSLA) => s.status === 'compliant').length}
                        <span className="text-xl text-slate-500">/{services.length}</span>
                    </div>
                </div>

                <div className="bg-slate-700 rounded-lg p-4">
                    <div className="text-xs text-slate-400 mb-2">Total Breaches (30d)</div>
                    <div className="text-4xl font-bold text-red-400">{totalBreaches}</div>
                    <div className="text-xs text-slate-500 dark:text-neutral-400 mt-2">Downtime: 62 minutes</div>
                </div>
            </div>

            {/* Service Details */}
            <div>
                <h3 className="font-semibold text-white mb-3">Service SLA Status</h3>
                <div className="space-y-2">
                    {services.map((service: ServiceSLA) => (
                        <div
                            key={service.name}
                            className={`rounded-lg p-4 ${getStatusColor(service.status)}`}
                        >
                            <div className="flex items-center justify-between mb-2">
                                <span className="font-medium">{service.name}</span>
                                <span className="text-sm font-mono">
                                    {service.actual.toFixed(2)}% / {service.target}%
                                </span>
                            </div>

                            {/* Progress bar */}
                            <div className="bg-slate-800 rounded-full h-2 overflow-hidden mb-2">
                                <div
                                    className={`h-full transition-all ${service.status === 'compliant'
                                        ? 'bg-green-500'
                                        : service.status === 'warning'
                                            ? 'bg-yellow-500'
                                            : 'bg-red-500'
                                        }`}
                                    style={{ width: `${service.actual}%` }}
                                />
                            </div>

                            <div className="text-xs flex items-center justify-between">
                                <span>Breaches: {service.breaches}</span>
                                <span className="text-slate-400">
                                    {service.actual >= service.target ? '✓ On target' : '⚠ Below target'}
                                </span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Recent Downtime Events */}
            <div>
                <h3 className="font-semibold text-white mb-3">Recent Downtime Events</h3>
                <div className="space-y-2">
                    {downtime.map((event: DowntimeEvent, idx: number) => (
                        <div key={idx} className="bg-slate-700 rounded-lg p-3">
                            <div className="flex items-center justify-between mb-2">
                                <span className="font-medium text-slate-200">{event.service}</span>
                                <span className="px-2 py-1 bg-slate-800 rounded text-xs font-mono text-slate-400">
                                    {event.time}
                                </span>
                            </div>
                            <div className="flex items-center justify-between">
                                <span className="text-sm text-slate-400">{event.cause}</span>
                                <span className="text-sm font-bold text-red-400">{event.duration}m downtime</span>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* SLA Targets Summary */}
            <div>
                <h3 className="font-semibold text-white mb-3">SLA Target Summary</h3>
                <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
                    <div className="bg-slate-700 rounded p-3 text-center">
                        <div className="text-xs text-slate-400 mb-1">99.99%</div>
                        <div className="text-lg font-bold text-slate-200">52 min/year</div>
                    </div>
                    <div className="bg-slate-700 rounded p-3 text-center">
                        <div className="text-xs text-slate-400 mb-1">99.9%</div>
                        <div className="text-lg font-bold text-slate-200">8.76 hr/year</div>
                    </div>
                    <div className="bg-slate-700 rounded p-3 text-center">
                        <div className="text-xs text-slate-400 mb-1">99%</div>
                        <div className="text-lg font-bold text-slate-200">3.65 days/year</div>
                    </div>
                    <div className="bg-slate-700 rounded p-3 text-center">
                        <div className="text-xs text-slate-400 mb-1">95%</div>
                        <div className="text-lg font-bold text-slate-200">18.25 days/year</div>
                    </div>
                </div>
            </div>
        </div>
    );
});

export default SLACompliancePanel;
