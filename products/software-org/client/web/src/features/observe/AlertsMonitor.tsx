import { useState, useEffect, useRef } from "react";
import { AlertTriangle, CheckCircle, XCircle, Clock, Bell, BellOff, Filter, RefreshCw, Zap, Wifi, WifiOff } from 'lucide-react';
import { Badge } from "@/components/ui";
import { WebSocketClient, ConnectionStatus } from '@/lib/websocket';
import { useSoundNotifications } from '@/lib/sound-notifications';
import { AlertListSkeleton, LoadingOverlay } from '@/components/LoadingState';

/**
 * Real-time Alerts Monitor
 *
 * <p><b>Purpose</b><br>
 * Live monitoring of system alerts with WebSocket streaming.
 * Provides operators with immediate visibility into critical system events.
 *
 * <p><b>Features</b><br>
 * - Live alert streaming via WebSocket
 * - Alert severity filtering (critical, high, medium, low)
 * - Acknowledge and snooze functionality
 * - Incident correlation
 * - Alert history with timeline
 * - Sound notifications toggle
 * - Auto-reconnection with exponential backoff
 *
 * @doc.type component
 * @doc.purpose Real-time alert monitoring dashboard
 * @doc.layer product
 * @doc.pattern Page
 */
export function AlertsMonitor() {
    const [alerts, setAlerts] = useState<Alert[]>([]);
    const [isLive, setIsLive] = useState(true);
    const [soundEnabled, setSoundEnabled] = useState(false);
    const [filterSeverity, setFilterSeverity] = useState<'all' | 'critical' | 'high' | 'medium' | 'low'>('all');
    const [selectedAlert, setSelectedAlert] = useState<Alert | null>(null);
    const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('disconnected');
    const [isLoading, setIsLoading] = useState(true);
    const [isExporting, setIsExporting] = useState(false);
    const wsClientRef = useRef<WebSocketClient | null>(null);
    
    // Sound notifications hook
    const soundNotifications = useSoundNotifications();

    // WebSocket connection for live alert stream
    useEffect(() => {
        if (!isLive) {
            // Disconnect if live mode is off
            if (wsClientRef.current) {
                wsClientRef.current.disconnect();
                wsClientRef.current = null;
            }
            return;
        }

        // Create WebSocket client
        const wsClient = new WebSocketClient({
            url: 'http://localhost:3101',
            namespace: '/observe/alerts',
        });

        wsClientRef.current = wsClient;

        // Subscribe to connection status changes
        const unsubscribeStatus = wsClient.onStatusChange((status) => {
            setConnectionStatus(status);
        });

        // Connect and subscribe to alerts
        wsClient.connect()
            .then(() => {
                console.log('[Alerts] Connected to WebSocket');
                
                // Subscribe to alerts stream
                wsClient.emit('subscribe', {
                    tenantId: 'acme-payments-id',
                    filters: filterSeverity !== 'all' ? { severity: filterSeverity } : {},
                });

                // Listen for initial batch of alerts
                wsClient.on('alerts:batch', (data: { alerts: Alert[] }) => {
                    console.log('[Alerts] Received batch:', data.alerts.length);
                    setAlerts(data.alerts);
                    setIsLoading(false);
                });

                // Listen for new alerts
                wsClient.on('alerts:new', (data: { alert: Alert }) => {
                    console.log('[Alerts] New alert:', data.alert.id);
                    setAlerts(prev => [data.alert, ...prev].slice(0, 50)); // Keep last 50
                    
                    // Play sound notification for critical/high alerts
                    if (soundEnabled && (data.alert.severity === 'critical' || data.alert.severity === 'high')) {
                        soundNotifications.playAlertSound(data.alert.severity);
                        soundNotifications.soundManager.showNotification(
                            `${data.alert.severity.toUpperCase()} Alert`,
                            {
                                body: data.alert.title,
                                icon: '/alert-icon.png',
                                badge: '/badge-icon.png',
                                tag: `alert-${data.alert.id}`,
                            }
                        );
                    }
                });
            })
            .catch((error) => {
                console.error('[Alerts] Connection failed:', error);
            });

        // Cleanup on unmount
        return () => {
            unsubscribeStatus();
            wsClient.disconnect();
        };
    }, [isLive, filterSeverity, soundEnabled]);

    // Re-subscribe when filter changes
    useEffect(() => {
        if (isLive && wsClientRef.current && connectionStatus === 'connected') {
            wsClientRef.current.emit('subscribe', {
                tenantId: 'acme-payments-id',
                filters: filterSeverity !== 'all' ? { severity: filterSeverity } : {},
            });
        }
    }, [filterSeverity, isLive, connectionStatus]);

    const filteredAlerts = filterSeverity === 'all' 
        ? alerts 
        : alerts.filter(a => a.severity === filterSeverity);

    const stats = {
        total: alerts.length,
        critical: alerts.filter(a => a.severity === 'critical').length,
        high: alerts.filter(a => a.severity === 'high').length,
        active: alerts.filter(a => a.status === 'active').length,
        acknowledged: alerts.filter(a => a.status === 'acknowledged').length,
    };

    const handleAcknowledge = (alertId: string) => {
        setAlerts(prev => prev.map(a => 
            a.id === alertId ? { ...a, status: 'acknowledged' as const, acknowledgedAt: new Date() } : a
        ));
    };

    const handleSnooze = (alertId: string, minutes: number) => {
        setAlerts(prev => prev.map(a => 
            a.id === alertId ? { ...a, snoozedUntil: new Date(Date.now() + minutes * 60000) } : a
        ));
    };

    const handleResolve = (alertId: string) => {
        setAlerts(prev => prev.map(a => 
            a.id === alertId ? { ...a, status: 'resolved' as const, resolvedAt: new Date() } : a
        ));
    };

    const handleExport = async (format: 'csv' | 'pdf' | 'json') => {
        setIsExporting(true);
        try {
            const response = await fetch('http://localhost:3101/api/v1/observe/alerts/export', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    tenantId: 'acme-payments-id',
                    filters: filterSeverity !== 'all' ? { severity: filterSeverity } : {},
                    format,
                }),
            });

            if (format === 'csv') {
                const csv = await response.text();
                const blob = new Blob([csv], { type: 'text/csv' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `alerts-export-${Date.now()}.csv`;
                a.click();
                URL.revokeObjectURL(url);
            } else if (format === 'json') {
                const json = await response.json();
                const blob = new Blob([JSON.stringify(json, null, 2)], { type: 'application/json' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `alerts-export-${Date.now()}.json`;
                a.click();
                URL.revokeObjectURL(url);
            } else if (format === 'pdf') {
                const result = await response.json();
                console.log('PDF export result:', result);
                // TODO: Handle PDF download when actual PDF generation is implemented
            }
        } catch (error) {
            console.error('Export failed:', error);
        } finally {
            setIsExporting(false);
        }
    };

    const severityConfig = {
        critical: { color: 'text-red-600 dark:text-red-400', bg: 'bg-red-50 dark:bg-red-900/20', icon: XCircle, variant: 'danger' as const },
        high: { color: 'text-orange-600 dark:text-orange-400', bg: 'bg-orange-50 dark:bg-orange-900/20', icon: AlertTriangle, variant: 'warning' as const },
        medium: { color: 'text-amber-600 dark:text-amber-400', bg: 'bg-amber-50 dark:bg-amber-900/20', icon: AlertTriangle, variant: 'warning' as const },
        low: { color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-50 dark:bg-blue-900/20', icon: AlertTriangle, variant: 'neutral' as const },
    };

    return (
        <LoadingOverlay isLoading={isExporting} message="Exporting alerts...">
            <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Real-time Alerts</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1 flex items-center gap-2">
                        Live monitoring of system alerts and incidents
                        {connectionStatus === 'connected' && (
                            <span className="inline-flex items-center gap-1 text-xs text-green-600 dark:text-green-400">
                                <Wifi className="h-3 w-3" /> Connected
                            </span>
                        )}
                        {connectionStatus === 'connecting' && (
                            <span className="inline-flex items-center gap-1 text-xs text-blue-600 dark:text-blue-400">
                                <RefreshCw className="h-3 w-3 animate-spin" /> Connecting...
                            </span>
                        )}
                        {connectionStatus === 'reconnecting' && (
                            <span className="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                                <RefreshCw className="h-3 w-3 animate-spin" /> Reconnecting...
                            </span>
                        )}
                        {connectionStatus === 'disconnected' && (
                            <span className="inline-flex items-center gap-1 text-xs text-slate-500 dark:text-neutral-500">
                                <WifiOff className="h-3 w-3" /> Disconnected
                            </span>
                        )}
                        {connectionStatus === 'error' && (
                            <span className="inline-flex items-center gap-1 text-xs text-red-600 dark:text-red-400">
                                <WifiOff className="h-3 w-3" /> Connection error
                            </span>
                        )}
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => {
                            const newState = !soundEnabled;
                            setSoundEnabled(newState);
                            soundNotifications.setEnabled(newState);
                            
                            // Play test sound when enabling
                            if (newState) {
                                soundNotifications.soundManager.playSound('info', { volume: 0.3 });
                            }
                        }}
                        className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                            soundEnabled
                                ? 'bg-blue-600 text-white hover:bg-blue-700'
                                : 'border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-800'
                        }`}
                        title={soundEnabled ? 'Mute notifications' : 'Enable sound notifications'}
                    >
                        {soundEnabled ? <Bell className="h-4 w-4" /> : <BellOff className="h-4 w-4" />}
                        Sound
                    </button>
                    <button
                        onClick={() => setIsLive(!isLive)}
                        className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                            isLive
                                ? 'bg-green-600 text-white hover:bg-green-700'
                                : 'bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-neutral-300 hover:bg-slate-300 dark:hover:bg-slate-600'
                        }`}
                    >
                        {isLive ? <Zap className="h-4 w-4" /> : <RefreshCw className="h-4 w-4" />}
                        {isLive ? '● Live' : 'Paused'}
                    </button>
                    <div className="relative group">
                        <button className="inline-flex items-center gap-2 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-neutral-300 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 text-sm font-medium">
                            Export
                        </button>
                        <div className="absolute right-0 mt-2 w-40 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-lg shadow-lg opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
                            <button
                                onClick={() => handleExport('csv')}
                                className="w-full px-4 py-2 text-left text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded-t-lg"
                            >
                                Export as CSV
                            </button>
                            <button
                                onClick={() => handleExport('json')}
                                className="w-full px-4 py-2 text-left text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700"
                            >
                                Export as JSON
                            </button>
                            <button
                                onClick={() => handleExport('pdf')}
                                className="w-full px-4 py-2 text-left text-sm text-slate-700 dark:text-neutral-300 hover:bg-slate-50 dark:hover:bg-slate-700 rounded-b-lg"
                            >
                                Export as PDF
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                <StatCard label="Total Alerts" value={stats.total} icon={<Bell className="h-5 w-5" />} />
                <StatCard label="Critical" value={stats.critical} icon={<XCircle className="h-5 w-5 text-red-500" />} />
                <StatCard label="High" value={stats.high} icon={<AlertTriangle className="h-5 w-5 text-orange-500" />} />
                <StatCard label="Active" value={stats.active} icon={<Clock className="h-5 w-5 text-blue-500" />} />
                <StatCard label="Acknowledged" value={stats.acknowledged} icon={<CheckCircle className="h-5 w-5 text-green-500" />} />
            </div>

            {/* Filters */}
            <div className="flex items-center gap-2">
                <Filter className="h-4 w-4 text-slate-500 dark:text-neutral-500" />
                <button
                    onClick={() => setFilterSeverity('all')}
                    className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                        filterSeverity === 'all'
                            ? 'bg-blue-600 text-white'
                            : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                    }`}
                >
                    All
                </button>
                {(['critical', 'high', 'medium', 'low'] as const).map(severity => (
                    <button
                        key={severity}
                        onClick={() => setFilterSeverity(severity)}
                        className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors capitalize ${
                            filterSeverity === severity
                                ? `${severity === 'critical' ? 'bg-red-600' : severity === 'high' ? 'bg-orange-600' : severity === 'medium' ? 'bg-amber-600' : 'bg-blue-600'} text-white`
                                : 'bg-white dark:bg-slate-900 text-slate-700 dark:text-neutral-300 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-800'
                        }`}
                    >
                        {severity}
                    </button>
                ))}
            </div>

            {/* Alerts Stream */}
            <div className="space-y-3">
                {isLoading ? (
                    <AlertListSkeleton count={8} />
                ) : filteredAlerts.length === 0 ? (
                    <div className="text-center py-12 bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700">
                        <CheckCircle className="h-12 w-12 text-green-500 mx-auto mb-4" />
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                            All Clear!
                        </h3>
                        <p className="text-sm text-slate-500 dark:text-neutral-500">
                            No active alerts at the moment
                        </p>
                    </div>
                ) : (
                    filteredAlerts.map(alert => {
                        const config = severityConfig[alert.severity];
                        const Icon = config.icon;
                        const isSnoozed = alert.snoozedUntil && alert.snoozedUntil > new Date();

                        return (
                            <div
                                key={alert.id}
                                onClick={() => setSelectedAlert(alert)}
                                className={`border rounded-lg p-4 transition-all cursor-pointer ${
                                    alert.status === 'resolved'
                                        ? 'border-slate-200 dark:border-slate-700 opacity-60'
                                        : isSnoozed
                                        ? 'border-slate-300 dark:border-slate-600 opacity-75'
                                        : `${config.bg} border-${alert.severity === 'critical' ? 'red' : alert.severity === 'high' ? 'orange' : alert.severity === 'medium' ? 'amber' : 'blue'}-200 dark:border-${alert.severity === 'critical' ? 'red' : alert.severity === 'high' ? 'orange' : alert.severity === 'medium' ? 'amber' : 'blue'}-700`
                                } hover:shadow-md dark:hover:shadow-slate-700/30`}
                            >
                                <div className="flex items-start gap-4">
                                    <div className={`p-2 rounded-lg ${config.bg}`}>
                                        <Icon className={`h-5 w-5 ${config.color}`} />
                                    </div>
                                    
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-start justify-between gap-4">
                                            <div className="flex-1">
                                                <div className="flex items-center gap-2 mb-1">
                                                    <h3 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                        {alert.title}
                                                    </h3>
                                                    <Badge variant={config.variant}>{alert.severity}</Badge>
                                                    {alert.status === 'acknowledged' && (
                                                        <Badge variant="neutral">Acknowledged</Badge>
                                                    )}
                                                    {alert.status === 'resolved' && (
                                                        <Badge variant="success">Resolved</Badge>
                                                    )}
                                                    {isSnoozed && (
                                                        <Badge variant="neutral">Snoozed</Badge>
                                                    )}
                                                </div>
                                                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-2">
                                                    {alert.message}
                                                </p>
                                                <div className="flex items-center gap-4 text-xs text-slate-500 dark:text-neutral-500">
                                                    <span>Source: {alert.source}</span>
                                                    <span>•</span>
                                                    <span>{formatTimestamp(alert.timestamp)}</span>
                                                    {alert.relatedIncidents.length > 0 && (
                                                        <>
                                                            <span>•</span>
                                                            <span className="text-amber-600 dark:text-amber-400">
                                                                {alert.relatedIncidents.length} related incidents
                                                            </span>
                                                        </>
                                                    )}
                                                </div>
                                            </div>
                                            
                                            {alert.status === 'active' && (
                                                <div className="flex items-center gap-2">
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleSnooze(alert.id, 30);
                                                        }}
                                                        className="px-3 py-1.5 text-xs text-slate-600 dark:text-neutral-400 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-md transition-colors"
                                                        title="Snooze for 30 minutes"
                                                    >
                                                        Snooze
                                                    </button>
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleAcknowledge(alert.id);
                                                        }}
                                                        className="px-3 py-1.5 text-xs text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-md transition-colors"
                                                    >
                                                        Acknowledge
                                                    </button>
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            handleResolve(alert.id);
                                                        }}
                                                        className="px-3 py-1.5 text-xs text-green-600 dark:text-green-400 hover:bg-green-50 dark:hover:bg-green-900/20 rounded-md transition-colors"
                                                    >
                                                        Resolve
                                                    </button>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })
                )}
            </div>

            {/* Alert Detail Modal */}
            {selectedAlert && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50" onClick={() => setSelectedAlert(null)}>
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700 max-w-2xl w-full p-6" onClick={(e) => e.stopPropagation()}>
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                                Alert Details
                            </h2>
                            <button
                                onClick={() => setSelectedAlert(null)}
                                className="text-slate-400 hover:text-slate-600 dark:hover:text-neutral-300"
                            >
                                ×
                            </button>
                        </div>

                        <div className="space-y-4">
                            <div>
                                <div className="flex items-center gap-2 mb-2">
                                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                        {selectedAlert.title}
                                    </h3>
                                    <Badge variant={severityConfig[selectedAlert.severity].variant}>
                                        {selectedAlert.severity}
                                    </Badge>
                                </div>
                                <p className="text-slate-600 dark:text-neutral-400">
                                    {selectedAlert.message}
                                </p>
                            </div>

                            <div className="grid grid-cols-2 gap-4 pt-4 border-t border-slate-200 dark:border-slate-700">
                                <div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Source</div>
                                    <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                        {selectedAlert.source}
                                    </div>
                                </div>
                                <div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Status</div>
                                    <div className="text-sm font-medium text-slate-900 dark:text-neutral-100 capitalize">
                                        {selectedAlert.status}
                                    </div>
                                </div>
                                <div>
                                    <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Triggered At</div>
                                    <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                        {selectedAlert.timestamp.toLocaleString()}
                                    </div>
                                </div>
                                {selectedAlert.acknowledgedAt && (
                                    <div>
                                        <div className="text-xs text-slate-500 dark:text-neutral-500 mb-1">Acknowledged At</div>
                                        <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                            {selectedAlert.acknowledgedAt.toLocaleString()}
                                        </div>
                                    </div>
                                )}
                            </div>

                            {selectedAlert.relatedIncidents.length > 0 && (
                                <div className="pt-4 border-t border-slate-200 dark:border-slate-700">
                                    <div className="text-sm font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                                        Related Incidents ({selectedAlert.relatedIncidents.length})
                                    </div>
                                    <div className="space-y-2">
                                        {selectedAlert.relatedIncidents.map(incident => (
                                            <div key={incident} className="text-sm text-slate-600 dark:text-neutral-400 pl-4 border-l-2 border-amber-400">
                                                Incident #{incident}
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {selectedAlert.metadata && (
                                <div className="pt-4 border-t border-slate-200 dark:border-slate-700">
                                    <div className="text-sm font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                                        Additional Details
                                    </div>
                                    <pre className="text-xs bg-slate-100 dark:bg-slate-800 p-3 rounded-md overflow-x-auto">
                                        {JSON.stringify(selectedAlert.metadata, null, 2)}
                                    </pre>
                                </div>
                            )}
                        </div>
                    </div>
                </div>
            )}
            </div>
        </LoadingOverlay>
    );
}

// Types
interface Alert {
    id: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    title: string;
    message: string;
    source: string;
    timestamp: Date;
    status: 'active' | 'acknowledged' | 'resolved';
    acknowledgedAt?: Date;
    resolvedAt?: Date;
    snoozedUntil?: Date;
    relatedIncidents: string[];
    metadata?: Record<string, unknown>;
}

// Helper Components
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


// Helper function
function formatTimestamp(date: Date): string {
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    
    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
    return date.toLocaleDateString();
}

