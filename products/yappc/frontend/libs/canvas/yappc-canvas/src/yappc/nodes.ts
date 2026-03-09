/**
 * @ghatana/canvas YAPPC Node Types
 *
 * ReactFlow node type definitions for YAPPC pages and layouts.
 *
 * @doc.type module
 * @doc.purpose YAPPC node type definitions
 * @doc.layer product
 * @doc.pattern NodeDefinitions
 */

import type { NodeTypeDefinition } from "../types";

// =============================================================================
// PAGE NODE
// =============================================================================

export const PageNode: NodeTypeDefinition = {
  type: "yappc:page",
  name: "Page",
  description: "A page in the application",
  icon: "file",
  category: "structure",

  defaultData: {
    name: "New Page",
    path: "/new-page",
    title: "New Page",
    description: "",
    isHome: false,
    layout: "default",
    meta: {},
  },

  schema: {
    properties: {
      name: {
        type: "text",
        label: "Page Name",
        default: "New Page",
        required: true,
      },
      path: {
        type: "text",
        label: "URL Path",
        default: "/new-page",
        required: true,
      },
      title: {
        type: "text",
        label: "Page Title",
        default: "New Page",
      },
      description: {
        type: "textarea",
        label: "Description",
        default: "",
      },
      isHome: {
        type: "boolean",
        label: "Is Home Page",
        default: false,
      },
      layout: {
        type: "select",
        label: "Layout",
        options: ["default", "full-width", "sidebar", "centered"],
        default: "default",
      },
    },
  },

  style: {
    width: 200,
    height: 80,
    backgroundColor: "#f0f9ff",
    borderColor: "#0ea5e9",
    borderWidth: 2,
    borderRadius: 8,
  },

  handles: {
    source: [{ id: "out", position: "right", type: "source" }],
    target: [{ id: "in", position: "left", type: "target" }],
  },

  behavior: {
    deletable: true,
    draggable: true,
    selectable: true,
    connectable: true,
  },
};

// =============================================================================
// COMPONENT NODE
// =============================================================================

export const ComponentNode: NodeTypeDefinition = {
  type: "yappc:component",
  name: "Component",
  description: "A reusable component",
  icon: "component",
  category: "structure",

  defaultData: {
    name: "Component",
    description: "",
    props: {},
    slots: [],
    isExported: true,
  },

  schema: {
    properties: {
      name: {
        type: "text",
        label: "Component Name",
        default: "Component",
        required: true,
      },
      description: {
        type: "textarea",
        label: "Description",
        default: "",
      },
      isExported: {
        type: "boolean",
        label: "Export Component",
        default: true,
      },
    },
  },

  style: {
    width: 180,
    height: 70,
    backgroundColor: "#f0fdf4",
    borderColor: "#22c55e",
    borderWidth: 2,
    borderRadius: 8,
  },

  handles: {
    source: [{ id: "out", position: "right", type: "source" }],
    target: [{ id: "in", position: "left", type: "target" }],
  },

  behavior: {
    deletable: true,
    draggable: true,
    selectable: true,
    connectable: true,
  },
};

// =============================================================================
// LAYOUT NODE
// =============================================================================

export const LayoutNode: NodeTypeDefinition = {
  type: "yappc:layout",
  name: "Layout",
  description: "A page layout template",
  icon: "layout-template",
  category: "structure",

  defaultData: {
    name: "Default Layout",
    hasHeader: true,
    hasFooter: true,
    hasSidebar: false,
    sidebarPosition: "left",
  },

  schema: {
    properties: {
      name: {
        type: "text",
        label: "Layout Name",
        default: "Default Layout",
        required: true,
      },
      hasHeader: {
        type: "boolean",
        label: "Has Header",
        default: true,
      },
      hasFooter: {
        type: "boolean",
        label: "Has Footer",
        default: true,
      },
      hasSidebar: {
        type: "boolean",
        label: "Has Sidebar",
        default: false,
      },
      sidebarPosition: {
        type: "select",
        label: "Sidebar Position",
        options: ["left", "right"],
        default: "left",
      },
    },
  },

  style: {
    width: 200,
    height: 100,
    backgroundColor: "#fefce8",
    borderColor: "#eab308",
    borderWidth: 2,
    borderRadius: 8,
  },

  handles: {
    source: [{ id: "content", position: "bottom", type: "source" }],
    target: [{ id: "pages", position: "top", type: "target" }],
  },

  behavior: {
    deletable: true,
    draggable: true,
    selectable: true,
    connectable: true,
  },
};

// =============================================================================
// API NODE
// =============================================================================

export const ApiNode: NodeTypeDefinition = {
  type: "yappc:api",
  name: "API Endpoint",
  description: "An API endpoint connection",
  icon: "cloud",
  category: "data",

  defaultData: {
    name: "API Call",
    url: "",
    method: "GET",
    headers: {},
    body: null,
  },

  schema: {
    properties: {
      name: {
        type: "text",
        label: "Name",
        default: "API Call",
      },
      url: {
        type: "text",
        label: "URL",
        default: "",
        required: true,
      },
      method: {
        type: "select",
        label: "Method",
        options: ["GET", "POST", "PUT", "PATCH", "DELETE"],
        default: "GET",
      },
    },
  },

  style: {
    width: 160,
    height: 60,
    backgroundColor: "#fdf4ff",
    borderColor: "#a855f7",
    borderWidth: 2,
    borderRadius: 8,
  },

  handles: {
    source: [{ id: "response", position: "right", type: "source" }],
    target: [{ id: "trigger", position: "left", type: "target" }],
  },

  behavior: {
    deletable: true,
    draggable: true,
    selectable: true,
    connectable: true,
  },
};

// =============================================================================
// STATE NODE
// =============================================================================

export const StateNode: NodeTypeDefinition = {
  type: "yappc:state",
  name: "State",
  description: "Application state container",
  icon: "database",
  category: "data",

  defaultData: {
    name: "State",
    initialValue: null,
    type: "object",
    persistent: false,
  },

  schema: {
    properties: {
      name: {
        type: "text",
        label: "State Name",
        default: "State",
        required: true,
      },
      type: {
        type: "select",
        label: "Type",
        options: ["string", "number", "boolean", "object", "array"],
        default: "object",
      },
      persistent: {
        type: "boolean",
        label: "Persist to Storage",
        default: false,
      },
    },
  },

  style: {
    width: 140,
    height: 60,
    backgroundColor: "#fff1f2",
    borderColor: "#f43f5e",
    borderWidth: 2,
    borderRadius: 8,
  },

  handles: {
    source: [{ id: "value", position: "right", type: "source" }],
    target: [{ id: "update", position: "left", type: "target" }],
  },

  behavior: {
    deletable: true,
    draggable: true,
    selectable: true,
    connectable: true,
  },
};

// =============================================================================
// ALL YAPPC NODE TYPES
// =============================================================================

export const yappcNodeTypes: readonly NodeTypeDefinition[] = [
  PageNode,
  ComponentNode,
  LayoutNode,
  ApiNode,
  StateNode,
];
