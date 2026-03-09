/**
 * ExtensionConnectorBridge
 *
 * Thin bridge between browser-extension hosts and the `@ghatana/dcmaar-connectors`
 * library. This allows plugins to emit events and metrics without taking a
 * hard dependency on concrete connector implementations.
 */

import type {
    ConnectionOptions,
    Event,
    IConnector,
} from "@ghatana/dcmaar-connectors";
import { createConnector } from "@ghatana/dcmaar-connectors";
import type {
    ExtensionConnectorProfile,
} from "@ghatana/dcmaar-types";

/**
 * Options for constructing an ExtensionConnectorBridge.
 */
export interface ExtensionConnectorBridgeOptions {
    /** Optional pre-declared connector profiles from a manifest. */
    profiles?: ExtensionConnectorProfile[];
}

/**
 * Bridge responsible for managing connector instances for an extension
 * and providing a small, typed API for sending events.
 */
export class ExtensionConnectorBridge {
    private readonly connectors: Map<string, IConnector<ConnectionOptions>> = new Map();

    constructor(options: ExtensionConnectorBridgeOptions = {}) {
        if (options.profiles) {
            void this.initializeProfiles(options.profiles);
        }
    }

    /**
     * Initialize connectors for a set of profiles.
     *
     * Safe to call multiple times; existing connectors are reused.
     */
    async initializeProfiles(profiles: ExtensionConnectorProfile[]): Promise<void> {
        const tasks = profiles.map(async (profile) => {
            if (this.connectors.has(profile.id)) {
                return;
            }

            const { auth, ...connectionWithoutAuth } = profile.connection;
            const connector = createConnector<ConnectionOptions>({
                ...connectionWithoutAuth,
                id: profile.connection.id || profile.id,
                ...(auth && typeof auth === 'object' && auth !== null ? { auth: auth as { [key: string]: unknown; type: 'none' | 'basic' | 'bearer' | 'api_key' | 'oauth2' } } : {}),
            } as ConnectionOptions);

            // Best-effort connect; failures are surfaced via send()/status.
            try {
                await connector.connect();
            } catch {
                // Caller can inspect connector.status via getConnector if needed.
            }

            this.connectors.set(profile.id, connector);
        });

        await Promise.all(tasks);
    }

    /**
     * Get a connector instance for a given profile ID, if available.
     */
    getConnector(profileId: string): IConnector<ConnectionOptions> | undefined {
        return this.connectors.get(profileId);
    }

    /**
     * Send an event payload via the connector associated with the given
     * profile ID.
     *
     * The payload is extracted from the Event and forwarded with
     * metadata for observability.
     */
    async send<TPayload = unknown>(
        profileId: string,
        event: Event<TPayload>,
    ): Promise<void> {
        const connector = this.connectors.get(profileId);
        if (!connector) {
            throw new Error(`Connector profile '${profileId}' is not initialized`);
        }

        await connector.send(event.payload, {
            eventId: event.id,
            eventType: event.type,
            metadata: event.metadata,
        });
    }

    /**
     * Gracefully disconnect all managed connectors.
     */
    async shutdown(): Promise<void> {
        const tasks = Array.from(this.connectors.values()).map(async (connector) => {
            try {
                await connector.disconnect();
            } catch {
                // Ignore individual disconnect failures
            }
        });

        await Promise.all(tasks);
        this.connectors.clear();
    }
}
