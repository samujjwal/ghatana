/**
 * @ghatana/canvas YAPPC Tools
 *
 * Tool definitions for YAPPC canvas.
 *
 * @doc.type module
 * @doc.purpose YAPPC tool definitions
 * @doc.layer product
 * @doc.pattern ToolDefinitions
 */

import type { ToolDefinition } from "../types";

// =============================================================================
// SELECT TOOL
// =============================================================================

export const selectTool: ToolDefinition = {
  id: "select",
  name: "Select",
  icon: "pointer",
  category: "selection",
  shortcut: "v",
  exclusive: true,

  description: "Select and move elements",

  cursor: "default",

  behavior: {
    canSelect: true,
    canMultiSelect: true,
    canDrag: true,
    canResize: true,
    canRotate: false,
  },
};

// =============================================================================
// PAN TOOL
// =============================================================================

export const panTool: ToolDefinition = {
  id: "pan",
  name: "Pan",
  icon: "hand",
  category: "navigation",
  shortcut: "h",
  exclusive: true,

  description: "Pan the canvas view",

  cursor: "grab",
  activeCursor: "grabbing",

  behavior: {
    canSelect: false,
    canMultiSelect: false,
    canDrag: false,
    canResize: false,
    canRotate: false,
    panOnDrag: true,
  },
};

// =============================================================================
// INSERT TOOL
// =============================================================================

export const insertTool: ToolDefinition = {
  id: "insert",
  name: "Insert",
  icon: "plus",
  category: "creation",
  shortcut: "i",
  exclusive: true,

  description: "Insert new elements",

  cursor: "crosshair",

  behavior: {
    canSelect: false,
    canMultiSelect: false,
    canDrag: false,
    canResize: false,
    canRotate: false,
    createOnClick: true,
    createOnDrag: true,
  },

  options: {
    elementType: "yappc:container",
  },
};

// =============================================================================
// TEXT TOOL
// =============================================================================

export const textTool: ToolDefinition = {
  id: "text",
  name: "Text",
  icon: "type",
  category: "creation",
  shortcut: "t",
  exclusive: true,

  description: "Add text elements",

  cursor: "text",

  behavior: {
    canSelect: false,
    canMultiSelect: false,
    canDrag: false,
    canResize: false,
    canRotate: false,
    createOnClick: true,
  },

  options: {
    elementType: "yappc:text",
  },
};

// =============================================================================
// FRAME TOOL
// =============================================================================

export const frameTool: ToolDefinition = {
  id: "frame",
  name: "Frame",
  icon: "frame",
  category: "creation",
  shortcut: "f",
  exclusive: true,

  description: "Create frame containers",

  cursor: "crosshair",

  behavior: {
    canSelect: false,
    canMultiSelect: false,
    canDrag: false,
    canResize: false,
    canRotate: false,
    createOnDrag: true,
  },

  options: {
    elementType: "yappc:container",
  },
};

// =============================================================================
// ZOOM IN TOOL
// =============================================================================

export const zoomInTool: ToolDefinition = {
  id: "zoom-in",
  name: "Zoom In",
  icon: "zoom-in",
  category: "navigation",
  shortcut: "mod+=",
  exclusive: false,

  description: "Zoom in on the canvas",

  cursor: "zoom-in",

  behavior: {
    canSelect: false,
    zoomOnClick: 1.25,
  },
};

// =============================================================================
// ZOOM OUT TOOL
// =============================================================================

export const zoomOutTool: ToolDefinition = {
  id: "zoom-out",
  name: "Zoom Out",
  icon: "zoom-out",
  category: "navigation",
  shortcut: "mod+-",
  exclusive: false,

  description: "Zoom out on the canvas",

  cursor: "zoom-out",

  behavior: {
    canSelect: false,
    zoomOnClick: 0.8,
  },
};

// =============================================================================
// COMMENT TOOL
// =============================================================================

export const commentTool: ToolDefinition = {
  id: "comment",
  name: "Comment",
  icon: "message-square",
  category: "annotation",
  shortcut: "c",
  exclusive: true,

  description: "Add comments to the canvas",

  cursor: "crosshair",

  behavior: {
    canSelect: false,
    createOnClick: true,
  },

  options: {
    elementType: "yappc:comment",
  },
};

// =============================================================================
// ALL YAPPC TOOLS
// =============================================================================

export const yappcTools: readonly ToolDefinition[] = [
  selectTool,
  panTool,
  insertTool,
  textTool,
  frameTool,
  zoomInTool,
  zoomOutTool,
  commentTool,
];
