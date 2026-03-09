import type { ExtensionPluginManifest } from "@ghatana/dcmaar-types";
import { guardianOnlyProfile } from "./guardianOnlyProfile";
import { deviceHealthOnlyProfile } from "./deviceHealthOnlyProfile";

export const combinedProfile: ExtensionPluginManifest = {
    appId: "dcmaar-unified-host-combined",
    version: "0.1.0",
    plugins: [
        ...guardianOnlyProfile.plugins,
        ...deviceHealthOnlyProfile.plugins,
    ],
    connectors: [],
    metadata: {
        profileId: "combined",
    },
};
