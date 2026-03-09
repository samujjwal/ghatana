/**
 * @fileoverview Command Sync Source
 *
 * Event source that periodically polls the backend `/devices/:id/sync` endpoint
 * to fetch policies and pending commands for the agent. Implements the Sprint 5
 * connector-based sync consumer design.
 *
 * @module pipeline/sources/CommandSyncSource
 */

import { BaseEventSource } from '@ghatana/dcmaar-browser-extension-core';

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
 * Internal event emitted by CommandSyncSource.
 */
export interface CommandSyncEvent {
    id: string;
    type: 'sync_snapshot';
    timestamp: number;
    source: string;
    snapshot: SyncSnapshot;
}

/**
 * Configuration for CommandSyncSource.
 */
export interface CommandSyncSourceConfig {
    /** Backend API base URL */
    apiBaseUrl: string;
    /** Device ID for sync */
    deviceId: string;
    /** Auth token getter */
    getAuthToken: () => string | null;
    /** Minimum poll interval in seconds (floor for next_sync_seconds) */
    minPollIntervalSeconds?: number;
    /** Maximum poll interval in seconds (ceiling for next_sync_seconds) */
    maxPollIntervalSeconds?: number;
    /** Initial poll interval before first successful sync */
    initialPollIntervalSeconds?: number;
    /** Whether to start polling immediately on start() */
    autoStart?: boolean;
}

const DEFAULT_CONFIG: Omit<Required<CommandSyncSourceConfig>, 'apiBaseUrl' | 'deviceId' | 'getAuthToken'> = {
    minPollIntervalSeconds: 30,
    maxPollIntervalSeconds: 300,
    initialPollIntervalSeconds: 60,
    autoStart: true,
};

/**
 * CommandSyncSource
 *
 * Periodically polls `/api/devices/:id/sync` to fetch the latest policies and
 * pending commands. Emits `CommandSyncEvent` with the full snapshot for
 * downstream processors/sinks to handle.
 *
 * Implements exponential backoff on errors and respects `next_sync_seconds`
 * from the backend (bounded by min/max config).
 *
 * @example
 * ```typescript
 * const source = new CommandSyncSource({
 *   apiBaseUrl: 'https://api.guardian.example.com',
 *   deviceId: 'device-123',
 *   getAuthToken: () => localStorage.getItem('guardian_token'),
 * });
 *
 * source.onEvent((event) => {
 *   console.log('Sync snapshot:', event.snapshot);
 * });
 *
 * await source.start();
 * ```
 */
export class CommandSyncSource extends BaseEventSource<CommandSyncEvent> {
    readonly name = 'command-sync';

    private readonly config: Required<CommandSyncSourceConfig>;
    private pollTimeout?: ReturnType<typeof setTimeout>;
    private currentPollIntervalSeconds: number;
    private consecutiveErrors = 0;
    private lastSyncVersion?: string;

    constructor(config: CommandSyncSourceConfig) {
        super();
        this.config = {
            ...DEFAULT_CONFIG,
            ...config,
        };
        this.currentPollIntervalSeconds = this.config.initialPollIntervalSeconds;
    }

    /**
     * Start polling for sync snapshots.
     */
    async start(): Promise<void> {
        if (this.status === 'started') {
            return;
        }

        this.status = 'started';
        console.debug('[CommandSyncSource] Started');

        if (this.config.autoStart) {
            // Perform initial sync immediately
            await this.poll();
        }
    }

    /**
     * Stop polling.
     */
    async stop(): Promise<void> {
        if (this.pollTimeout) {
            clearTimeout(this.pollTimeout);
            this.pollTimeout = undefined;
        }

        this.status = 'stopped';
        console.debug('[CommandSyncSource] Stopped');
    }

    /**
     * Force an immediate sync (useful for manual refresh).
     */
    async forceSync(): Promise<SyncSnapshot | null> {
        if (this.pollTimeout) {
            clearTimeout(this.pollTimeout);
            this.pollTimeout = undefined;
        }

        return this.poll();
    }

    /**
     * Get the current poll interval.
     */
    getPollIntervalSeconds(): number {
        return this.currentPollIntervalSeconds;
    }

    /**
     * Get the last sync version.
     */
    getLastSyncVersion(): string | undefined {
        return this.lastSyncVersion;
    }

    /**
     * Poll the backend for sync snapshot.
     */
    private async poll(): Promise<SyncSnapshot | null> {
        if (this.status !== 'started') {
            return null;
        }

        try {
            const snapshot = await this.fetchSyncSnapshot();

            if (snapshot) {
                // Update state
                this.lastSyncVersion = snapshot.sync_version;
                this.consecutiveErrors = 0;

                // Adjust poll interval based on backend recommendation (bounded)
                this.currentPollIntervalSeconds = Math.max(
                    this.config.minPollIntervalSeconds,
                    Math.min(this.config.maxPollIntervalSeconds, snapshot.next_sync_seconds)
                );

                // Emit event
                const event: CommandSyncEvent = {
                    id: `sync-${Date.now()}`,
                    type: 'sync_snapshot',
                    timestamp: Date.now(),
                    source: this.name,
                    snapshot,
                };

                this.emit(event);
            }

            // Schedule next poll
            this.scheduleNextPoll();

            return snapshot;
        } catch (error) {
            console.error('[CommandSyncSource] Sync error:', error);
            this.consecutiveErrors++;

            // Exponential backoff on errors
            const backoffSeconds = Math.min(
                this.config.maxPollIntervalSeconds,
                this.config.initialPollIntervalSeconds * Math.pow(2, Math.min(this.consecutiveErrors, 5))
            );
            this.currentPollIntervalSeconds = backoffSeconds;

            // Schedule next poll with backoff
            this.scheduleNextPoll();

            return null;
        }
    }

    /**
     * Fetch sync snapshot from backend.
     */
    private async fetchSyncSnapshot(): Promise<SyncSnapshot | null> {
        const token = this.config.getAuthToken();

        if (!token) {
            console.warn('[CommandSyncSource] No auth token available, skipping sync');
            return null;
        }

        const url = `${this.config.apiBaseUrl}/api/devices/${this.config.deviceId}/sync`;

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
    private scheduleNextPoll(): void {
        if (this.status !== 'started') {
            return;
        }

        this.pollTimeout = setTimeout(() => {
            void this.poll();
        }, this.currentPollIntervalSeconds * 1000);

        console.debug(`[CommandSyncSource] Next poll in ${this.currentPollIntervalSeconds}s`);
    }
}
