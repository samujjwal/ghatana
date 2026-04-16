/**
 * AEP Pipeline Canvas Plugin
 *
 * @doc.type plugin
 * @doc.purpose Registers AEP pipeline node types with the @ghatana/canvas plugin system
 * @doc.layer product
 * @doc.pattern Plugin
 *
 * This plugin bridges the AEP product's custom stage/connector ReactFlow node types
 * into the platform plugin registry, making them discoverable through the canonical
 * `PluginManager.getInstance().getNodeTypes()` API.
 *
 * Products still control their own `PipelineCanvas.tsx` layout and store wiring;
 * this plugin only registers the node type catalogue so the platform's HybridCanvas
 * graph layer can render them without knowing about AEP internals.
 *
 * Usage:
 * ```ts
 * import { aepPipelinePlugin } from "@/canvas-plugin/aep-pipeline-plugin";
 * import { PluginManager } from "@ghatana/canvas";
 *
 * const pm = PluginManager.getInstance();
 * await pm.register(aepPipelinePlugin, { autoActivate: true });
 * ```
 */

import { createElement, type ComponentProps } from "react";
import { StageNode } from "../components/pipeline/nodes/StageNode.js";
import { ConnectorNode } from "../components/pipeline/nodes/ConnectorNode.js";
import type {
  CanvasPlugin,
  PluginManifest,
  PluginContext,
  NodeTypeDefinition,
  NodeComponentProps,
} from "@ghatana/canvas/plugins";

// ---------------------------------------------------------------------------
// Manifest
// ---------------------------------------------------------------------------

const manifest: PluginManifest = {
  id: "@aep/canvas-pipeline-plugin",
  name: "AEP Pipeline Canvas Plugin",
  version: "1.0.0",
  description:
    "Registers AEP pipeline stage and connector node types with the platform canvas plugin system",
  author: { name: "Ghatana AEP Team" },
  minCanvasVersion: "0.1.0",
  capabilities: ["custom-node-types"],
};

// ---------------------------------------------------------------------------
// Node type registrations
// ---------------------------------------------------------------------------

const nodeTypes: readonly NodeTypeDefinition[] = [
  {
    type: "stage",
    label: "Pipeline Stage",
    category: "Pipeline",
    component: function StagePluginNode(props: NodeComponentProps) {
      // The platform plugin contract exposes a smaller prop surface than ReactFlow.
      return createElement(
        StageNode,
        props as unknown as ComponentProps<typeof StageNode>,
      );
    },
    defaultData: {
      label: "Stage",
      kind: "sequential",
      agents: [],
      agentCount: 0,
    },
  },
  {
    type: "connector",
    label: "Pipeline Connector",
    category: "Pipeline",
    component: function ConnectorPluginNode(props: NodeComponentProps) {
      return createElement(
        ConnectorNode,
        props as unknown as ComponentProps<typeof ConnectorNode>,
      );
    },
    defaultData: {
      label: "Connector",
      connectorId: "",
      type: "merge",
      direction: "left-to-right",
    },
  },
];

// ---------------------------------------------------------------------------
// Plugin
// ---------------------------------------------------------------------------

export const aepPipelinePlugin: CanvasPlugin = {
  manifest,
  nodeTypes,

  onActivate(ctx: PluginContext) {
    ctx.logger.info(
      `[${manifest.id}] activated — registered ${nodeTypes.length} node types`,
    );
  },

  onDeactivate(ctx: PluginContext) {
    ctx.logger.info(`[${manifest.id}] deactivated`);
  },
};

/**
 * Convenience helper to register and activate the plugin in one call.
 * Idempotent — safe to call multiple times.
 */
export async function registerAepPipelinePlugin(): Promise<void> {
  // Lazy import to avoid pulling PluginManager into every AEP module bundle
  const { PluginManager } = await import("@ghatana/canvas/plugins");
  const pm = PluginManager.getInstance();

  // Guard against double-registration (e.g. HMR in dev)
  if (pm.isRegistered(manifest.id)) return;

  await pm.register(aepPipelinePlugin, { autoActivate: true });
}
