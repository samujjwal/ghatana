/**
 * Extension plugin manifest types
 *
 * Domain-neutral manifest definitions for browser extensions that host
 * DCMAAR plugins. These types are used by browser-extension runtimes and
 * product apps (Guardian, Device Health, etc.) to describe which plugins
 * are enabled and how they connect to DCMAAR backends.
 */

import type { PluginConfig } from "./Config";
import type { ConnectionOptions } from "../generated/runtime-config";

/**
 * Supported high-level plugin categories for extension manifests.
 *
 * This is intentionally open-ended: product-specific categories may
 * extend this string union via string literals.
 */
export type ExtensionPluginType =
    | "data-collector"
    | "notification"
    | "storage"
    | "ui"
    | string;

/**
 * Connector profile referenced from extension plugin manifests.
 *
 * Wraps the authoritative ConnectionOptions from the runtime
 * configuration schema so we avoid duplicating connection shapes.
 */
export interface ExtensionConnectorProfile {
    /** Profile identifier referenced by plugins (e.g. "guardian_https"). */
    id: string;
    /** Optional logical kind for the profile (egress, ingress, etc.). */
    kind?: "egress" | "ingress" | "bidirectional" | string;
    /** Concrete connection options for this profile. */
    connection: ConnectionOptions;
    /** Optional tags for grouping and selection. */
    tags?: string[];
    /** Arbitrary metadata for observability or configuration. */
    metadata?: Record<string, unknown>;
}

/**
 * Descriptor for a single plugin entry in an extension manifest.
 *
 * Extends the generic PluginConfig type to avoid duplicating
 * pluginId/enabled/options semantics.
 */
export interface ExtensionPluginDescriptor extends PluginConfig {
    /** Optional high-level category for this plugin. */
    type?: ExtensionPluginType;
    /**
     * IDs of connector profiles this plugin is allowed to use.
     *
     * The host runtime and connector bridge enforce that plugins only
     * send/receive data via these profiles.
     */
    connectorProfileIds?: string[];
    /** Optional capability flags for host/runtime decisions. */
    capabilities?: string[];
}

/**
 * ExtensionPluginManifest – top-level manifest for an extension.
 *
 * This structure is used by browser-extension-core and apps to
 * configure which plugins are active, how they are connected, and
 * any extension-scoped metadata.
 */
export interface ExtensionPluginManifest {
    /** Logical application identifier (e.g. "guardian", "device-health"). */
    appId: string;
    /** Optional manifest version for migration and auditing. */
    version?: string;
    /** Declared plugins for this extension. */
    plugins: ExtensionPluginDescriptor[];
    /** Optional connector profiles available to plugins. */
    connectors?: ExtensionConnectorProfile[];
    /** Arbitrary manifest-level metadata. */
    metadata?: Record<string, unknown>;
}
