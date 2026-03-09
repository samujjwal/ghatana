import { apiUrl } from '../config/api';
import { useState, useEffect, useMemo, memo, useCallback } from 'react';
import { useAtomValue } from 'jotai';
import { useDashboardData } from '@ghatana/dcmaar-dashboard-core';
import { devicesStatusAtom } from '../stores/eventsStore';
import { websocketService } from '../services/websocket.service';
import type { DeviceStatusEvent } from '../services/websocket.service';

export interface Device {
  id: string;
  name: string;
  type: 'mobile' | 'tablet' | 'desktop' | 'laptop';
  status: 'online' | 'offline';
  lastHeartbeat: string;
  registeredAt: string;
  policies: string[];
}

interface DeviceManagementProps {
  onDeviceRegistered?: (device: Device) => void;
}

function DeviceManagementComponent({ onDeviceRegistered }: DeviceManagementProps) {
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [showRegisterForm, setShowRegisterForm] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [filterType, setFilterType] = useState<string>('all');

  // Subscribe to real-time device status updates
  const deviceStatusMap = useAtomValue(devicesStatusAtom);
  const { devices: coreDevices, isLoading: coreLoading } = useDashboardData();

  // Memoize device status update function
  const updateDeviceStatus = useCallback((deviceId: string, status: 'online' | 'offline', lastHeartbeat: string) => {
    setDevices((prevDevices) =>
      prevDevices.map((device) =>
        device.id === deviceId
          ? { ...device, status, lastHeartbeat }
          : device
      )
    );
  }, []);

  useEffect(() => {
    if (coreDevices) {
      setDevices(coreDevices as Device[]);
    }
    setLoading(coreLoading);
  }, [coreDevices, coreLoading]);

  useEffect(() => {
    // Subscribe to WebSocket events
    const unsubscribeOnline = websocketService.on('device_online', (data) => {
      const deviceEvent = data as unknown as { device: { id: string; last_seen: string } };
      console.log('Device came online:', data);
      updateDeviceStatus(deviceEvent.device.id, 'online', deviceEvent.device.last_seen);
    });

    const unsubscribeOffline = websocketService.on('device_offline', (data) => {
      const deviceEvent = data as unknown as { device: { id: string; last_seen: string } };
      console.log('Device went offline:', data);
      updateDeviceStatus(deviceEvent.device.id, 'offline', deviceEvent.device.last_seen);
    });

    return () => {
      unsubscribeOnline();
      unsubscribeOffline();
    };
  }, [updateDeviceStatus]);

  // Update device list when real-time status changes arrive
  useEffect(() => {
    deviceStatusMap.forEach((statusEvent: DeviceStatusEvent) => {
      updateDeviceStatus(
        statusEvent.device.id,
        statusEvent.device.status,
        statusEvent.device.last_seen
      );
    });
  }, [deviceStatusMap, updateDeviceStatus]);

  const handleRegisterDevice = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);

    const newDevice = {
      name: formData.get('name') as string,
      type: formData.get('type') as Device['type'],
      policies: [],
    };

    try {
      const token = localStorage.getItem('token');
      const response = await fetch(apiUrl('/devices'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(newDevice),
      });

      if (response.ok) {
        const registeredDevice = await response.json();
        setDevices([...devices, registeredDevice]);
        setShowRegisterForm(false);
        onDeviceRegistered?.(registeredDevice);
      }
    } catch (error) {
      console.error('Failed to register device:', error);
    }
  };

  // Memoize filtered devices to avoid recalculation on every render
  const filteredDevices = useMemo(() => {
    return devices.filter(device => {
      const matchesSearch = device.name.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesStatus = filterStatus === 'all' || device.status === filterStatus;
      const matchesType = filterType === 'all' || device.type === filterType;
      return matchesSearch && matchesStatus && matchesType;
    });
  }, [devices, searchTerm, filterStatus, filterType]);

  // Memoize helper functions
  const getStatusColor = useCallback((status: string) => {
    return status === 'online' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800';
  }, []);

  const getTypeIcon = useCallback((type: string) => {
    switch (type) {
      case 'mobile':
        return '📱';
      case 'tablet':
        return '📱';
      case 'desktop':
        return '🖥️';
      case 'laptop':
        return '💻';
      default:
        return '📱';
    }
  }, []);

  const getTimeSinceLastHeartbeat = (lastHeartbeat: string) => {
    const now = new Date();
    const heartbeatTime = new Date(lastHeartbeat);
    const diffMs = now.getTime() - heartbeatTime.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return 'Just now';
    if (diffMins === 1) return '1 minute ago';
    if (diffMins < 60) return `${diffMins} minutes ago`;

    const diffHours = Math.floor(diffMins / 60);
    if (diffHours === 1) return '1 hour ago';
    if (diffHours < 24) return `${diffHours} hours ago`;

    const diffDays = Math.floor(diffHours / 24);
    return diffDays === 1 ? '1 day ago' : `${diffDays} days ago`;
  };

  if (showRegisterForm) {
    return (
      <div className="space-y-6">
        <div className="bg-white shadow rounded-lg p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-2xl font-bold text-gray-900">Register New Device</h2>
            <button
              onClick={() => setShowRegisterForm(false)}
              className="text-gray-500 hover:text-gray-700"
            >
              Cancel
            </button>
          </div>

          <form onSubmit={handleRegisterDevice} className="space-y-4">
            <div>
              <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1">
                Device Name
              </label>
              <input
                id="name"
                name="name"
                type="text"
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="e.g., John's iPhone"
              />
            </div>

            <div>
              <label htmlFor="type" className="block text-sm font-medium text-gray-700 mb-1">
                Device Type
              </label>
              <select
                id="type"
                name="type"
                required
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="mobile">Mobile</option>
                <option value="tablet">Tablet</option>
                <option value="desktop">Desktop</option>
                <option value="laptop">Laptop</option>
              </select>
            </div>

            <div className="flex gap-3 pt-4">
              <button
                type="submit"
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                Register Device
              </button>
              <button
                type="button"
                onClick={() => setShowRegisterForm(false)}
                className="flex-1 px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-500"
              >
                Cancel
              </button>
            </div>
          </form>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="bg-white shadow rounded-lg p-6">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-2xl font-bold text-gray-900">Device Management</h2>
          <button
            onClick={() => setShowRegisterForm(true)}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            Register Device
          </button>
        </div>

        {/* Search and Filters */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <input
            type="text"
            placeholder="Search devices..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <select
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">All Statuses</option>
            <option value="online">Online</option>
            <option value="offline">Offline</option>
          </select>
          <select
            value={filterType}
            onChange={(e) => setFilterType(e.target.value)}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">All Types</option>
            <option value="mobile">Mobile</option>
            <option value="tablet">Tablet</option>
            <option value="desktop">Desktop</option>
            <option value="laptop">Laptop</option>
          </select>
        </div>

        {/* Statistics */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
          <div className="bg-blue-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-blue-600">Total Devices</h3>
            <p className="text-3xl font-bold text-blue-900">{devices.length}</p>
          </div>
          <div className="bg-green-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-green-600">Online</h3>
            <p className="text-3xl font-bold text-green-900">
              {devices.filter(d => d.status === 'online').length}
            </p>
          </div>
          <div className="bg-gray-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-gray-600">Offline</h3>
            <p className="text-3xl font-bold text-gray-900">
              {devices.filter(d => d.status === 'offline').length}
            </p>
          </div>
          <div className="bg-purple-50 rounded-lg p-4">
            <h3 className="text-sm font-medium text-purple-600">Active Policies</h3>
            <p className="text-3xl font-bold text-purple-900">
              {devices.reduce((sum, d) => sum + d.policies.length, 0)}
            </p>
          </div>
        </div>

        {/* Device List */}
        {loading ? (
          <p className="text-gray-500 text-center py-8">Loading devices...</p>
        ) : filteredDevices.length === 0 ? (
          <p className="text-gray-500 text-center py-8">
            No devices found. Register your first device to get started.
          </p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4" role="list">
            {filteredDevices.map((device) => (
              <div key={device.id} className="border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow" role="listitem">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <span className="text-2xl">{getTypeIcon(device.type)}</span>
                    <div>
                      <h3 className="text-lg font-semibold text-gray-900">{device.name}</h3>
                      <p className="text-sm text-gray-500 capitalize">{device.type}</p>
                    </div>
                  </div>
                  <span className={`px-2 py-1 rounded-full text-xs font-semibold ${getStatusColor(device.status)}`}>
                    {device.status}
                  </span>
                </div>

                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-sm text-gray-600">
                    <span className={`h-2 w-2 rounded-full ${device.status === 'online' ? 'bg-green-500' : 'bg-gray-400'}`}></span>
                    <span>Last seen: {getTimeSinceLastHeartbeat(device.lastHeartbeat)}</span>
                  </div>
                  <p className="text-sm text-gray-600">
                    Policies: {device.policies.length}
                  </p>
                  <p className="text-xs text-gray-500">
                    Registered: {new Date(device.registeredAt).toLocaleDateString()}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Export memoized component to prevent unnecessary re-renders
export const DeviceManagement = memo(DeviceManagementComponent);
