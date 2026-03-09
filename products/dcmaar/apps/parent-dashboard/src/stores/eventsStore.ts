import { atom } from 'jotai';
import type { UsageEvent, BlockEvent, PolicyEvent, DeviceStatusEvent } from '../services/websocket.service';

// Usage events
export const usageEventsAtom = atom<UsageEvent[]>([]);
export const latestUsageEventAtom = atom<UsageEvent | null>(null);

// Block events
export const blockEventsAtom = atom<BlockEvent[]>([]);
export const latestBlockEventAtom = atom<BlockEvent | null>(null);

// Policy events
export const policiesAtom = atom<PolicyEvent[]>([]);

// Device status
export const devicesStatusAtom = atom<Map<string, DeviceStatusEvent>>(new Map());

// WebSocket connection status
export const wsConnectedAtom = atom<boolean>(false);

// Add usage event
export const addUsageEventAtom = atom(
  null,
  (get, set, event: UsageEvent) => {
    const current = get(usageEventsAtom);
    set(usageEventsAtom, [event, ...current].slice(0, 100)); // Keep last 100 events
    set(latestUsageEventAtom, event);
  }
);

// Add block event
export const addBlockEventAtom = atom(
  null,
  (get, set, event: BlockEvent) => {
    const current = get(blockEventsAtom);
    set(blockEventsAtom, [event, ...current].slice(0, 100)); // Keep last 100 events
    set(latestBlockEventAtom, event);
  }
);

// Update device status
export const updateDeviceStatusAtom = atom(
  null,
  (get, set, event: DeviceStatusEvent) => {
    const current = new Map(get(devicesStatusAtom));
    current.set(event.device.id, event);
    set(devicesStatusAtom, current);
  }
);
