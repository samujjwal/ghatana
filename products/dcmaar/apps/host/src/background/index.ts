import { ExtensionPluginHost } from "@ghatana/dcmaar-browser-extension-core";
import type { ExtensionPluginManifest } from "@ghatana/dcmaar-types";

import {
    GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID,
    GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID,
    GuardianUsageCollectorPlugin,
    GuardianPolicyEvaluationPlugin,
} from "@ghatana/dcmaar-guardian-plugins";

import { CPUMonitor, MemoryMonitor, BatteryMonitor } from "@ghatana/dcmaar-plugin-extension";

import { guardianOnlyProfile } from "../config/profiles/guardianOnlyProfile";
import { deviceHealthOnlyProfile } from "../config/profiles/deviceHealthOnlyProfile";
import { combinedProfile } from "../config/profiles/combinedProfile";

export type HostProfileId = "guardian-only" | "device-health-only" | "combined";

function resolveProfileId(): HostProfileId {
    // For now, default to combined. This can later be driven by install-time
    // configuration or environment variables provided by the bundler.
    return "combined";
}

function getProfileManifest(profileId: HostProfileId): ExtensionPluginManifest {
    switch (profileId) {
        case "guardian-only":
            return guardianOnlyProfile;
        case "device-health-only":
            return deviceHealthOnlyProfile;
        case "combined":
        default:
            return combinedProfile;
    }
}

async function createHostForProfile(profileId: HostProfileId): Promise<ExtensionPluginHost> {
    const manifest = getProfileManifest(profileId);

    const host = new ExtensionPluginHost({ manifest });

    // Guardian plugins
    host.registerFactory(GUARDIAN_USAGE_COLLECTOR_PLUGIN_ID, () => {
        return new GuardianUsageCollectorPlugin();
    });

    host.registerFactory(GUARDIAN_POLICY_EVALUATOR_PLUGIN_ID, () => {
        return new GuardianPolicyEvaluationPlugin();
    });

    // Device Health plugins (device monitoring pack)
    host.registerFactory("cpu-monitor", () => {
        return new CPUMonitor();
    });

    host.registerFactory("memory-monitor", () => {
        return new MemoryMonitor();
    });

    host.registerFactory("battery-monitor", () => {
        return new BatteryMonitor();
    });

    await host.initializeFromManifest(manifest);

    return host;
}

let currentHost: ExtensionPluginHost | null = null;

async function bootstrap(): Promise<void> {
    if (currentHost) {
        await currentHost.shutdown();
        currentHost = null;
    }

    const profileId = resolveProfileId();
    currentHost = await createHostForProfile(profileId);
}

// Initialize immediately when the background script loads.
void bootstrap();

// Optionally expose a handle for future integrations (e.g., tests or
// message handlers) without tying this module to any specific UI.
export function getCurrentHost(): ExtensionPluginHost | null {
    return currentHost;
}
