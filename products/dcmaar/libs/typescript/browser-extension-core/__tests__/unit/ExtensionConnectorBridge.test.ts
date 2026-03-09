/**
 * @fileoverview ExtensionConnectorBridge Tests
 */

import { describe, it, expect, beforeEach, vi } from "vitest";
import { ExtensionConnectorBridge } from "../../src/plugins/ExtensionConnectorBridge";
import type { ExtensionConnectorProfile } from "@ghatana/dcmaar-types";

// Mock @ghatana/dcmaar-connectors to avoid real network behavior
const connectMock = vi.fn();
const disconnectMock = vi.fn();
const sendMock = vi.fn();

vi.mock("@ghatana/dcmaar-connectors", () => {
    return {
        // Minimal type exports for the bridge
        createConnector: (config: unknown) => {
            const typedConfig = config as { id: string; type?: string };
            return {
                id: typedConfig.id,
                type: typedConfig.type ?? "http",
                status: "connected",
                connect: connectMock,
                disconnect: disconnectMock,
                send: sendMock,
                onEvent: vi.fn(),
                offEvent: vi.fn(),
                getConfig: vi.fn(() => typedConfig),
                updateConfig: vi.fn(),
                validateConfig: vi.fn(() => ({ valid: true })),
            };
        },
    };
});

describe("ExtensionConnectorBridge", () => {
    beforeEach(() => {
        connectMock.mockReset();
        disconnectMock.mockReset();
        sendMock.mockReset();
    });

    it("initializes connectors from profiles and exposes them", async () => {
        const profiles: ExtensionConnectorProfile[] = [
            {
                id: "guardian_https",
                kind: "egress",
                connection: {
                    id: "guardian_https",
                    type: "http",
                    secure: true,
                } as any,
            },
        ];

        const bridge = new ExtensionConnectorBridge();

        await bridge.initializeProfiles(profiles);

        const connector = bridge.getConnector("guardian_https");
        expect(connector).toBeDefined();
        expect(connectMock).toHaveBeenCalled();
    });

    it("sends events via the selected connector", async () => {
        const profiles: ExtensionConnectorProfile[] = [
            {
                id: "guardian_https",
                connection: {
                    id: "guardian_https",
                    type: "http",
                } as any,
            },
        ];

        const bridge = new ExtensionConnectorBridge({ profiles });

        const event = {
            id: "evt-1",
            type: "test-event",
            timestamp: Date.now(),
            payload: { value: 42 },
            metadata: { source: "test" },
        };

        await bridge.send("guardian_https", event as any);

        expect(sendMock).toHaveBeenCalledWith(event.payload, {
            eventId: event.id,
            eventType: event.type,
            metadata: event.metadata,
        });
    });

    it("throws when sending via an unknown profile", async () => {
        const bridge = new ExtensionConnectorBridge();

        const event = {
            id: "evt-1",
            type: "test-event",
            timestamp: Date.now(),
            payload: {},
        };

        await expect(
            bridge.send("unknown-profile", event as any),
        ).rejects.toThrow(/not initialized/);
    });

    it("shuts down all connectors without throwing", async () => {
        const profiles: ExtensionConnectorProfile[] = [
            {
                id: "guardian_https",
                connection: {
                    id: "guardian_https",
                    type: "http",
                } as any,
            },
        ];

        const bridge = new ExtensionConnectorBridge({ profiles });

        await bridge.shutdown();

        // Best-effort disconnect: we only assert that disconnect was attempted
        expect(disconnectMock).toHaveBeenCalled();
    });
});
