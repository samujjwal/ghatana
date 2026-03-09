/**
 * @ghatana/canvas YAPPC Plugin Definition
 *
 * Main plugin definition for YAPPC canvas integration.
 *
 * @doc.type class
 * @doc.purpose YAPPC plugin definition
 * @doc.layer product
 * @doc.pattern Plugin
 */

import type { CanvasPlugin, PluginManifest, PluginContext } from "../types";
import {
  yappcElements,
  containerElement,
  textElement,
  buttonElement,
  imageElement,
  inputElement,
  cardElement,
  listElement,
  formElement,
} from "./elements";
import { yappcNodeTypes, PageNode, ComponentNode, LayoutNode } from "./nodes";
import {
  yappcPanels,
  componentPalettePanel,
  propertyInspectorPanel,
  layersPanel,
  pageOutlinePanel,
} from "./panels";
import {
  yappcTools,
  selectTool,
  panTool,
  insertTool,
  textTool,
  frameTool,
} from "./tools";

/**
 * YAPPC Plugin Configuration
 */
export interface YappcPluginConfig {
  /** Enable page management */
  enablePages?: boolean;
  /** Enable component library */
  enableComponents?: boolean;
  /** Enable responsive design features */
  enableResponsive?: boolean;
  /** Default page size */
  defaultPageSize?: { width: number; height: number };
  /** Grid settings */
  grid?: {
    size: number;
    snap: boolean;
  };
}

const DEFAULT_CONFIG: YappcPluginConfig = {
  enablePages: true,
  enableComponents: true,
  enableResponsive: true,
  defaultPageSize: { width: 1440, height: 900 },
  grid: {
    size: 8,
    snap: true,
  },
};

/**
 * YAPPC Plugin Manifest
 */
const yappcManifest: PluginManifest = {
  id: "ghatana.yappc",
  name: "YAPPC Canvas",
  version: "1.0.0",
  description: "UI builder elements and page design capabilities for YAPPC",
  author: "Ghatana Team",
  icon: "layout",
  keywords: ["ui", "builder", "page", "component", "design"],
  homepage: "https://ghatana.com/yappc",
};

/**
 * Create YAPPC plugin with custom configuration
 */
export function createYappcPlugin(
  config: Partial<YappcPluginConfig> = {},
): CanvasPlugin {
  const mergedConfig = { ...DEFAULT_CONFIG, ...config };

  return {
    manifest: yappcManifest,

    // Freeform canvas elements (UI components)
    elements: yappcElements,

    // ReactFlow node types (pages, layouts)
    nodeTypes: yappcNodeTypes,

    // Canvas tools
    tools: yappcTools,

    // Side panels
    panels: yappcPanels,

    // Lifecycle hooks
    onLoad: async (context: PluginContext) => {
      context.logger.info("YAPPC plugin loading...");

      // Apply grid settings
      if (mergedConfig.grid) {
        context.canvas.setGrid({
          size: mergedConfig.grid.size,
          snap: mergedConfig.grid.snap,
        });
      }

      context.logger.info("YAPPC plugin loaded");
    },

    onActivate: async (context: PluginContext) => {
      context.logger.info("YAPPC plugin activating...");

      // Set default rendering mode for YAPPC (hybrid with freeform primary)
      context.canvas.setMode("hybrid-freeform");

      // Set default tool
      context.canvas.setTool("select");

      context.logger.info("YAPPC plugin activated");
    },

    onDeactivate: async (context: PluginContext) => {
      context.logger.info("YAPPC plugin deactivating...");
    },

    onUninstall: async (context: PluginContext) => {
      context.logger.info("YAPPC plugin uninstalling...");
    },
  };
}

/**
 * Default YAPPC plugin instance
 */
export const yappcPlugin = createYappcPlugin();
