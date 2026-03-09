/**
 * DeviceManagement - Refactored with composable hooks
 * 
 * REUSES: 
 * - hooks/useGuardian.ts (useDevices, useChildren)
 * - services/api.service.ts (type definitions)
 * NO DUPLICATION: Uses centralized state and API layer
 */

import { useState, useCallback, memo } from 'react';
import { useDevices, useChildren } from '../hooks/useGuardian';
import type { Device, ImmediateAction } from '../services/api.service';

// ============================================================================
// Sub-components (composable, reusable)
// ============================================================================

interface DeviceCardProps {
    device: Device;
    onAction: (deviceId: string, action: ImmediateAction) => void;
    onToggle: (deviceId: string, isActive: boolean) => void;
    onDelete: (deviceId: string) => void;
}

const DeviceCard = memo(function DeviceCard({ device, onAction, onToggle, onDelete }: DeviceCardProps) {
    const isOnline = device.last_seen_at &&
        new Date(device.last_seen_at) > new Date(Date.now() - 5 * 60 * 1000);

    const getDeviceIcon = (type: string) => {
        switch (type) {
            case 'mobile': return '📱';
            case 'desktop': return '🖥️';
            case 'extension': return '🌐';
            default: return '📱';
        }
    };

    const formatLastSeen = (lastSeen?: string) => {
        if (!lastSeen) return 'Never';
        const date = new Date(lastSeen);
        const now = new Date();
        const diffMs = now.getTime() - date.getTime();
        const diffMins = Math.floor(diffMs / 60000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return `${diffHours}h ago`;
        return `${Math.floor(diffHours / 24)}d ago`;
    };

    return (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                    <span className="text-2xl">{getDeviceIcon(device.device_type)}</span>
                    <div>
                        <h3 className="font-medium text-gray-900">{device.device_name}</h3>
                        <p className="text-sm text-gray-500 capitalize">{device.device_type}</p>
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    <span className={`inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${isOnline ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
                        }`}>
                        <span className={`w-2 h-2 rounded-full mr-1 ${isOnline ? 'bg-green-500' : 'bg-gray-400'}`} />
                        {isOnline ? 'Online' : 'Offline'}
                    </span>
                </div>
            </div>

            <div className="mt-3 text-sm text-gray-500">
                Last seen: {formatLastSeen(device.last_seen_at)}
            </div>

            <div className="mt-4 flex items-center justify-between">
                <div className="flex gap-2">
                    <button
                        onClick={() => onAction(device.id, 'lock_device')}
                        className="px-3 py-1.5 text-sm bg-red-50 text-red-700 rounded hover:bg-red-100 transition-colors"
                        title="Lock device"
                    >
                        🔒 Lock
                    </button>
                    <button
                        onClick={() => onAction(device.id, 'sound_alarm')}
                        className="px-3 py-1.5 text-sm bg-yellow-50 text-yellow-700 rounded hover:bg-yellow-100 transition-colors"
                        title="Sound alarm"
                    >
                        🔔 Find
                    </button>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => onToggle(device.id, !device.is_active)}
                        className={`px-3 py-1.5 text-sm rounded transition-colors ${device.is_active
                            ? 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            : 'bg-blue-50 text-blue-700 hover:bg-blue-100'
                            }`}
                    >
                        {device.is_active ? 'Disable' : 'Enable'}
                    </button>
                    <button
                        onClick={() => onDelete(device.id)}
                        className="px-3 py-1.5 text-sm bg-gray-50 text-gray-600 rounded hover:bg-gray-100 transition-colors"
                    >
                        🗑️
                    </button>
                </div>
            </div>
        </div>
    );
});

// ============================================================================
// Stats Card Component
// ============================================================================

interface StatsCardProps {
    label: string;
    value: number;
    icon: string;
    color: 'blue' | 'green' | 'yellow' | 'gray';
}

const StatsCard = memo(function StatsCard({ label, value, icon, color }: StatsCardProps) {
    const colorClasses = {
        blue: 'bg-blue-50 text-blue-700',
        green: 'bg-green-50 text-green-700',
        yellow: 'bg-yellow-50 text-yellow-700',
        gray: 'bg-gray-50 text-gray-700',
    };

    return (
        <div className={`rounded-lg p-4 ${colorClasses[color]}`}>
            <div className="flex items-center gap-2">
                <span className="text-xl">{icon}</span>
                <span className="text-2xl font-bold">{value}</span>
            </div>
            <p className="text-sm mt-1 opacity-80">{label}</p>
        </div>
    );
});

// ============================================================================
// Main Component
// ============================================================================

export interface DeviceManagementRefactoredProps {
    childId?: string;
}

function DeviceManagementRefactoredComponent({ childId }: DeviceManagementRefactoredProps) {
    const {
        devices,
        childDevices,
        loading,
        error,
        sendAction,
        toggleDevice,
        deleteDevice,
        fetchDevices,
    } = useDevices();

    useChildren();

    const [, setActionLoading] = useState<string | null>(null);
    const [filter, setFilter] = useState<'all' | 'online' | 'offline'>('all');

    // Use child-filtered devices if childId provided
    const displayDevices = childId
        ? devices.filter(d => d.child_id === childId)
        : childDevices;

    // Filter by online status
    const filteredDevices = displayDevices.filter(device => {
        if (filter === 'all') return true;
        const isOnline = device.last_seen_at &&
            new Date(device.last_seen_at) > new Date(Date.now() - 5 * 60 * 1000);
        return filter === 'online' ? isOnline : !isOnline;
    });

    // Stats
    const stats = {
        total: displayDevices.length,
        online: displayDevices.filter(d =>
            d.last_seen_at && new Date(d.last_seen_at) > new Date(Date.now() - 5 * 60 * 1000)
        ).length,
        active: displayDevices.filter(d => d.is_active).length,
    };

    const handleAction = useCallback(async (deviceId: string, action: ImmediateAction) => {
        setActionLoading(deviceId);
        try {
            await sendAction(deviceId, action);
            // Show success feedback
        } catch (err) {
            console.error('Action failed:', err);
        } finally {
            setActionLoading(null);
        }
    }, [sendAction]);

    const handleToggle = useCallback(async (deviceId: string, isActive: boolean) => {
        try {
            await toggleDevice(deviceId, isActive);
        } catch (err) {
            console.error('Toggle failed:', err);
        }
    }, [toggleDevice]);

    const handleDelete = useCallback(async (deviceId: string) => {
        if (!confirm('Are you sure you want to remove this device?')) return;
        try {
            await deleteDevice(deviceId);
        } catch (err) {
            console.error('Delete failed:', err);
        }
    }, [deleteDevice]);

    if (loading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (error) {
        return (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
                <p className="font-medium">Error loading devices</p>
                <p className="text-sm mt-1">{error}</p>
                <button
                    onClick={() => fetchDevices()}
                    className="mt-2 text-sm underline hover:no-underline"
                >
                    Try again
                </button>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Stats */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <StatsCard label="Total Devices" value={stats.total} icon="📱" color="blue" />
                <StatsCard label="Online Now" value={stats.online} icon="🟢" color="green" />
                <StatsCard label="Monitoring Active" value={stats.active} icon="👁️" color="yellow" />
            </div>

            {/* Filters */}
            <div className="flex items-center justify-between">
                <div className="flex gap-2">
                    {(['all', 'online', 'offline'] as const).map(f => (
                        <button
                            key={f}
                            onClick={() => setFilter(f)}
                            className={`px-3 py-1.5 text-sm rounded-full transition-colors ${filter === f
                                ? 'bg-blue-600 text-white'
                                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                                }`}
                        >
                            {f.charAt(0).toUpperCase() + f.slice(1)}
                        </button>
                    ))}
                </div>
                <button
                    onClick={() => fetchDevices()}
                    className="px-3 py-1.5 text-sm bg-gray-100 text-gray-700 rounded hover:bg-gray-200 transition-colors"
                >
                    ↻ Refresh
                </button>
            </div>

            {/* Device Grid */}
            {filteredDevices.length === 0 ? (
                <div className="text-center py-12 bg-gray-50 rounded-lg">
                    <p className="text-gray-500">No devices found</p>
                    <p className="text-sm text-gray-400 mt-1">
                        {filter !== 'all' ? 'Try changing the filter' : 'Register a device to get started'}
                    </p>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                    {filteredDevices.map(device => (
                        <DeviceCard
                            key={device.id}
                            device={device}
                            onAction={handleAction}
                            onToggle={handleToggle}
                            onDelete={handleDelete}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

export const DeviceManagementRefactored = memo(DeviceManagementRefactoredComponent);
export default DeviceManagementRefactored;
