/**
 * Command Sync Service
 *
 * Periodically polls the backend `/devices/:id/sync` endpoint to fetch
 * policies and pending commands. Implements the Sprint 5 connector-based
 * sync consumer design for React Native agents.
 *
 * @module services/commandSyncService
 */

import { create } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';

/**
 * Sync snapshot returned by the backend `/devices/:id/sync` endpoint.
 */
export interface SyncSnapshot {
    schema_version: number;
    device_id: string;
    synced_at: string;
    sync_version: string;
    policies: {
        version: string;
        items: PolicyItem[];
        count: number;
    };
    commands: {
        items: GuardianCommand[];
        count: number;
    };
    next_sync_seconds: number;
}

/**
 * Policy item from sync payload.
 */
export interface PolicyItem {
    id: string;
    name: string;
    type: string;
    priority: number;
    enabled: boolean;
    config: Record<string, unknown>;
}

/**
 * Guardian command from sync payload (matches backend GuardianCommand contract).
 */
export interface GuardianCommand {
    schema_version: number;
    command_id: string;
    kind: 'immediate_action' | 'session_request' | 'policy_update';
    action: string;
    target: {
        device_id: string;
        child_id?: string;
    };
    params: Record<string, unknown>;
    issued_by: {
        actor_type: 'parent' | 'child' | 'system';
        user_id?: string;
    };
    created_at: string;
    expires_at?: string;
}

/**
 * Command sync service configuration.
 */
export interface CommandSyncConfig {
    apiBaseUrl: string;
    deviceId: string;
    getAuthToken: () => string | null;
    minPollIntervalSeconds?: number;
    maxPollIntervalSeconds?: number;
    initialPollIntervalSeconds?: number;
}

/**
 * Command sync service state.
 */
interface CommandSyncState {
    isRunning: boolean;
    lastSnapshot: SyncSnapshot | null;
    lastSyncTime: number | null;
    lastSyncVersion: string | null;
    currentPollIntervalSeconds: number;
    consecutiveErrors: number;
    error: string | null;
}

const DEFAULT_MIN_POLL_INTERVAL = 30;
const DEFAULT_MAX_POLL_INTERVAL = 300;
const DEFAULT_INITIAL_POLL_INTERVAL = 60;

/**
 * Command Sync Service store.
 */
export const useCommandSyncStore = create<CommandSyncState>()(
    subscribeWithSelector(() => ({
        isRunning: false as boolean,
        lastSnapshot: null as SyncSnapshot | null,
        lastSyncTime: null as number | null,
        lastSyncVersion: null as string | null,
        currentPollIntervalSeconds: DEFAULT_INITIAL_POLL_INTERVAL,
        consecutiveErrors: 0,
        error: null as string | null,
    }))
);

// Private state
let config: CommandSyncConfig | null = null;
let pollTimeout: NodeJS.Timeout | null = null;
let snapshotListeners: Array<(snapshot: SyncSnapshot) => void> = [];

/**
 * Initialize the command sync service.
 */
export function initCommandSync(syncConfig: CommandSyncConfig): void {
    config = {
        ...syncConfig,
        minPollIntervalSeconds: syncConfig.minPollIntervalSeconds ?? DEFAULT_MIN_POLL_INTERVAL,
        maxPollIntervalSeconds: syncConfig.maxPollIntervalSeconds ?? DEFAULT_MAX_POLL_INTERVAL,
        initialPollIntervalSeconds: syncConfig.initialPollIntervalSeconds ?? DEFAULT_INITIAL_POLL_INTERVAL,
    };

    useCommandSyncStore.setState({
        currentPollIntervalSeconds: config.initialPollIntervalSeconds!,
    });
}

/**
 * Start the sync polling loop.
 */
export async function startCommandSync(): Promise<void> {
    if (!config) {
        console.error('[CommandSyncService] Not initialized');
        return;
    }

    if (useCommandSyncStore.getState().isRunning) {
        return;
    }

    useCommandSyncStore.setState({ isRunning: true, error: null });

    // Initial sync immediately
    await performSync();
}

/**
 * Stop the sync polling loop.
 */
export function stopCommandSync(): void {
    if (pollTimeout) {
        clearTimeout(pollTimeout);
        pollTimeout = null;
    }

    useCommandSyncStore.setState({ isRunning: false });
}

/**
 * Force an immediate sync.
 */
export async function forceSync(): Promise<SyncSnapshot | null> {
    if (pollTimeout) {
        clearTimeout(pollTimeout);
        pollTimeout = null;
    }

    return performSync();
}

/**
 * Register a listener for sync snapshots.
 */
export function onSyncSnapshot(listener: (snapshot: SyncSnapshot) => void): () => void {
    snapshotListeners.push(listener);
    return () => {
        snapshotListeners = snapshotListeners.filter((l) => l !== listener);
    };
}

/**
 * Perform a single sync.
 */
async function performSync(): Promise<SyncSnapshot | null> {
    if (!config) {
        return null;
    }

    const state = useCommandSyncStore.getState();
    if (!state.isRunning) {
        return null;
    }

    try {
        const snapshot = await fetchSyncSnapshot();

        if (snapshot) {
            // Update state
            const nextInterval = Math.max(
                config.minPollIntervalSeconds!,
                Math.min(config.maxPollIntervalSeconds!, snapshot.next_sync_seconds)
            );

            useCommandSyncStore.setState({
                lastSnapshot: snapshot,
                lastSyncTime: Date.now(),
                lastSyncVersion: snapshot.sync_version,
                currentPollIntervalSeconds: nextInterval,
                consecutiveErrors: 0,
                error: null,
            });

            // Notify listeners
            snapshotListeners.forEach((listener) => {
                try {
                    listener(snapshot);
                } catch (e) {
                    console.error('[CommandSyncService] Listener error:', e);
                }
            });
        }

        // Schedule next poll
        scheduleNextPoll();

        return snapshot;
    } catch (error) {
        const errorMessage = error instanceof Error ? error.message : String(error);
        console.error('[CommandSyncService] Sync error:', errorMessage);

        const currentErrors = useCommandSyncStore.getState().consecutiveErrors + 1;

        // Exponential backoff
        const backoffSeconds = Math.min(
            config.maxPollIntervalSeconds!,
            config.initialPollIntervalSeconds! * Math.pow(2, Math.min(currentErrors, 5))
        );

        useCommandSyncStore.setState({
            consecutiveErrors: currentErrors,
            currentPollIntervalSeconds: backoffSeconds,
            error: errorMessage,
        });

        // Schedule next poll with backoff
        scheduleNextPoll();

        return null;
    }
}

/**
 * Fetch sync snapshot from backend.
 */
async function fetchSyncSnapshot(): Promise<SyncSnapshot | null> {
    if (!config) {
        return null;
    }

    const token = config.getAuthToken();
    if (!token) {
        console.warn('[CommandSyncService] No auth token, skipping sync');
        return null;
    }

    const url = `${config.apiBaseUrl}/api/devices/${config.deviceId}/sync`;

    const response = await fetch(url, {
        method: 'GET',
        headers: {
            Authorization: `Bearer ${token}`,
            'Content-Type': 'application/json',
        },
    });

    if (!response.ok) {
        throw new Error(`Sync request failed: ${response.status} ${response.statusText}`);
    }

    const json = await response.json();

    if (!json.success) {
        throw new Error(json.error || 'Sync request returned unsuccessful response');
    }

    return json.data as SyncSnapshot;
}

/**
 * Schedule the next poll.
 */
function scheduleNextPoll(): void {
    const state = useCommandSyncStore.getState();
    if (!state.isRunning) {
        return;
    }

    pollTimeout = setTimeout(() => {
        void performSync();
    }, state.currentPollIntervalSeconds * 1000);

    console.debug(`[CommandSyncService] Next poll in ${state.currentPollIntervalSeconds}s`);
}
