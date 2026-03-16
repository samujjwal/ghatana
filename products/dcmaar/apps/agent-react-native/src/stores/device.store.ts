/**
 * Device Management Store - Jotai Atoms
 *
 * Manages device selection and device list state including:
 * - List of monitored devices
 * - Currently selected device
 * - Device status tracking
 * - Device lifecycle management
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose Device management state
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';
import { guardianApi } from '../services/guardianApi';
import { authAtom } from './auth.store';

/**
 * Device object representing a monitored device.
 *
 * @interface Device
 * @property {string} id - Unique device identifier
 * @property {string} name - Device display name
 * @property {string} model - Device model (e.g., "Samsung Galaxy S24")
 * @property {string} osVersion - Android version
 * @property {'online' | 'offline' | 'error'} status - Device status
 * @property {Date} lastSeen - Last time device was seen online
 */
export interface Device {
  id: string;
  name: string;
  model: string;
  osVersion: string;
  status: 'online' | 'offline' | 'error';
  lastSeen: Date;
}

/**
 * Device management state.
 *
 * @interface DeviceState
 * @property {Device[]} devices - List of all monitored devices
 * @property {string | null} selectedDeviceId - ID of currently selected device
 * @property {'idle' | 'loading' | 'loaded' | 'error'} status - Loading status
 * @property {string | null} error - Error message if fetch failed
 */
export interface DeviceState {
  devices: Device[];
  selectedDeviceId: string | null;
  status: 'idle' | 'loading' | 'loaded' | 'error';
  error: string | null;
}

/**
 * Initial device state.
 *
 * GIVEN: App initialization
 * WHEN: deviceAtom is first accessed
 * THEN: Device list starts empty with no selection
 */
const initialDeviceState: DeviceState = {
  devices: [],
  selectedDeviceId: null,
  status: 'idle',
  error: null,
};

/**
 * Core device atom.
 *
 * Holds complete device management state including:
 * - List of monitored devices
 * - Currently selected device
 * - Loading and error state
 *
 * Usage (in components):
 * `const [deviceState, setDeviceState] = useAtom(deviceAtom);`
 */
export const deviceAtom = atom<DeviceState>(initialDeviceState);

/**
 * Derived atom: Currently selected device.
 *
 * GIVEN: deviceAtom with devices and selectedDeviceId
 * WHEN: selectedDeviceAtom is read
 * THEN: Returns the device object matching selectedDeviceId, or null
 *
 * Usage (in components):
 * `const [selectedDevice] = useAtom(selectedDeviceAtom);`
 * If selectedDevice exists, show device details
 */
export const selectedDeviceAtom = atom<Device | null>((get) => {
  const state = get(deviceAtom);
  if (!state.selectedDeviceId) return null;
  return (
    state.devices.find((d) => d.id === state.selectedDeviceId) || null
  );
});

/**
 * Derived atom: Total number of devices.
 *
 * GIVEN: deviceAtom with devices list
 * WHEN: deviceCountAtom is read
 * THEN: Returns total count of devices
 *
 * Usage (in components):
 * `const [count] = useAtom(deviceCountAtom);`
 * Show "3 devices" in UI
 */
export const deviceCountAtom = atom<number>((get) => {
  return get(deviceAtom).devices.length;
});

/**
 * Derived atom: Is a device selected?
 *
 * GIVEN: deviceAtom with optional selection
 * WHEN: isDeviceSelectedAtom is read
 * THEN: Returns true if device is selected
 *
 * Usage (in components):
 * `const [isSelected] = useAtom(isDeviceSelectedAtom);`
 * Enable/disable controls based on selection
 */
export const isDeviceSelectedAtom = atom<boolean>((get) => {
  return get(deviceAtom).selectedDeviceId !== null;
});

/**
 * Derived atom: List of online devices only.
 *
 * GIVEN: deviceAtom with mixed device statuses
 * WHEN: onlineDevicesAtom is read
 * THEN: Returns only devices with status === 'online'
 *
 * Usage (in components):
 * `const [onlineDevices] = useAtom(onlineDevicesAtom);`
 * Show only devices available for control
 */
export const onlineDevicesAtom = atom<Device[]>((get) => {
  return get(deviceAtom).devices.filter((d) => d.status === 'online');
});

/**
 * Action atom: Select a device.
 *
 * GIVEN: Device ID to select
 * WHEN: selectDeviceAtom action is called
 * THEN: Updates selectedDeviceId in deviceAtom
 *
 * GIVEN: Device ID does not exist in devices list
 * WHEN: Action is called with invalid ID
 * THEN: Sets error in deviceAtom.error
 *
 * Usage (in components):
 * `const [, selectDevice] = useAtom(selectDeviceAtom);`
 * selectDevice('device-123');
 */
export const selectDeviceAtom = atom<null, [deviceId: string], void>(
  null,
  (get, set, deviceId: string) => {
    const state = get(deviceAtom);

    // Validate device exists
    if (!state.devices.find((d) => d.id === deviceId)) {
      set(deviceAtom, {
        ...state,
        error: `Device ${deviceId} not found`,
      });
      return;
    }

    set(deviceAtom, {
      ...state,
      selectedDeviceId: deviceId,
      error: null,
    });
  }
);

/**
 * Action atom: Fetch devices from backend.
 *
 * GIVEN: Valid backend connection
 * WHEN: fetchDevicesAtom action is called
 * THEN: Sets status to 'loading' and fetches device list
 *       Updates deviceAtom with fetched devices
 *       Sets status to 'loaded' on success
 *
 * GIVEN: Fetch fails (network error, backend error)
 * WHEN: Error occurs
 * THEN: Sets status to 'error' and error message
 *       Throws error to caller
 *
 * Usage (in components):
 * `const [, fetchDevices] = useAtom(fetchDevicesAtom);`
 * await fetchDevices();
 * // Devices now available
 */
export const fetchDevicesAtom = atom<
  null,
  [],
  Promise<Device[]>
>(
  null,
  async (get, set) => {
    const state = get(deviceAtom);

    // Set loading state
    set(deviceAtom, {
      ...state,
      status: 'loading',
      error: null,
    });

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      const deviceStatusList = await guardianApi.getDevices(tenantId);

      // Map DeviceStatusData → Device (use deviceId as id, derive display fields)
      const devices: Device[] = deviceStatusList.map((d) => ({
        id: d.deviceId,
        name: `Device ${d.deviceId.slice(-6)}`,
        model: 'Android Device',
        osVersion: 'Android',
        status: d.syncStatus === 'failed' ? 'error' : d.syncStatus === 'synced' ? 'online' : 'offline',
        lastSeen: new Date(d.lastSync),
      }));

      set(deviceAtom, {
        ...state,
        devices,
        status: 'loaded',
        error: null,
      });

      return devices;
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to fetch devices';

      set(deviceAtom, {
        ...state,
        status: 'error',
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Remove device from monitoring.
 *
 * GIVEN: Device exists in devices list
 * WHEN: removeDeviceAtom action is called with device ID
 * THEN: Removes device from list
 *       Clears selectedDeviceId if it was selected
 *       Calls backend to deregister device
 *
 * GIVEN: Removed device was selected
 * WHEN: Removal completes
 * THEN: No device is selected after removal
 *
 * Usage (in components):
 * `const [, removeDevice] = useAtom(removeDeviceAtom);`
 * await removeDevice('device-123');
 */
export const removeDeviceAtom = atom<
  null,
  [deviceId: string],
  Promise<void>
>(
  null,
  async (get, set, deviceId: string) => {
    const state = get(deviceAtom);

    try {
      const { user } = get(authAtom);
      const tenantId = user?.tenantId ?? user?.id ?? '';

      // Trigger a final sync then deregister via status endpoint
      await guardianApi.syncDevice(tenantId, deviceId);

      set(deviceAtom, {
        ...state,
        devices: state.devices.filter((d) => d.id !== deviceId),
        selectedDeviceId:
          state.selectedDeviceId === deviceId ? null : state.selectedDeviceId,
        error: null,
      });
    } catch (error) {
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to remove device';

      set(deviceAtom, {
        ...state,
        error: errorMessage,
      });

      throw error;
    }
  }
);

/**
 * Action atom: Update device status.
 *
 * GIVEN: Device with status change (online → offline)
 * WHEN: updateDeviceStatusAtom action is called
 * THEN: Updates device status in devices list
 *       Updates lastSeen timestamp
 *
 * Usage (in components or WebSocket listeners):
 * `const [, updateStatus] = useAtom(updateDeviceStatusAtom);`
 * updateStatus('device-123', 'online');
 */
export const updateDeviceStatusAtom = atom<
  null,
  [deviceId: string, status: Device['status']],
  void
>(
  null,
  (get, set, deviceId: string, status: Device['status']) => {
    const state = get(deviceAtom);

    set(deviceAtom, {
      ...state,
      devices: state.devices.map((d) =>
        d.id === deviceId
          ? { ...d, status, lastSeen: new Date() }
          : d
      ),
    });
  }
);

/**
 * Clear device error.
 *
 * GIVEN: Device operation error is displayed
 * WHEN: User dismisses error
 * THEN: clearDeviceErrorAtom clears the error message
 *
 * Usage (in components):
 * `const [, clearError] = useAtom(clearDeviceErrorAtom);`
 * clearError();
 */
export const clearDeviceErrorAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(deviceAtom);
    set(deviceAtom, {
      ...state,
      error: null,
    });
  }
);
