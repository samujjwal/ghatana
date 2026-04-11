/**
 * @ghatana/canvas-tools
 *
 * Drawing and editing tools for the Ghatana canvas:
 * - SelectTool, PanTool, ShapeTool, TextTool, BrushTool, EraserTool
 * - ConnectorTool for element connections
 * - BaseTool base class for custom tool authoring
 *
 * @doc.type module
 * @doc.purpose Canvas drawing and editing tool implementations
 * @doc.layer platform
 * @doc.pattern Facade
 */

// Re-export from the canonical canvas package's tools sub-path.
// Tools are accessed via the plugins sub-path for type safety when
// used together with the plugin registry.

export {
  BaseTool,
  SelectTool,
  ShapeTool,
  TextTool,
  BrushTool,
  ConnectorTool,
} from "@ghatana/canvas";
