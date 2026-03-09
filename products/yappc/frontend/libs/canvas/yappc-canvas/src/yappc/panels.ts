/**
 * @ghatana/canvas YAPPC Panels
 *
 * Panel definitions for YAPPC canvas.
 *
 * @doc.type module
 * @doc.purpose YAPPC panel definitions
 * @doc.layer product
 * @doc.pattern PanelDefinitions
 */

import type { PanelDefinition } from "../types";

// =============================================================================
// COMPONENT PALETTE PANEL
// =============================================================================

export const componentPalettePanel: PanelDefinition = {
  id: "yappc:component-palette",
  name: "Components",
  icon: "layout-grid",
  position: "left",
  order: 1,
  initiallyVisible: true,
  width: 280,

  sections: [
    {
      id: "basic",
      name: "Basic",
      collapsed: false,
      items: [
        { type: "element:yappc:text", label: "Text", icon: "type" },
        {
          type: "element:yappc:button",
          label: "Button",
          icon: "mouse-pointer-click",
        },
        { type: "element:yappc:image", label: "Image", icon: "image" },
      ],
    },
    {
      id: "layout",
      name: "Layout",
      collapsed: false,
      items: [
        { type: "element:yappc:container", label: "Container", icon: "square" },
        {
          type: "element:yappc:card",
          label: "Card",
          icon: "rectangle-vertical",
        },
      ],
    },
    {
      id: "form",
      name: "Form",
      collapsed: true,
      items: [
        {
          type: "element:yappc:input",
          label: "Input",
          icon: "text-cursor-input",
        },
        { type: "element:yappc:form", label: "Form", icon: "clipboard-list" },
      ],
    },
    {
      id: "data",
      name: "Data",
      collapsed: true,
      items: [{ type: "element:yappc:list", label: "List", icon: "list" }],
    },
  ],
};

// =============================================================================
// PROPERTY INSPECTOR PANEL
// =============================================================================

export const propertyInspectorPanel: PanelDefinition = {
  id: "yappc:property-inspector",
  name: "Properties",
  icon: "sliders",
  position: "right",
  order: 1,
  initiallyVisible: true,
  width: 300,

  sections: [
    {
      id: "layout",
      name: "Layout",
      collapsed: false,
    },
    {
      id: "style",
      name: "Style",
      collapsed: false,
    },
    {
      id: "content",
      name: "Content",
      collapsed: false,
    },
    {
      id: "interactions",
      name: "Interactions",
      collapsed: true,
    },
  ],
};

// =============================================================================
// LAYERS PANEL
// =============================================================================

export const layersPanel: PanelDefinition = {
  id: "yappc:layers",
  name: "Layers",
  icon: "layers",
  position: "left",
  order: 2,
  initiallyVisible: false,
  width: 260,

  sections: [
    {
      id: "tree",
      name: "Element Tree",
      collapsed: false,
    },
  ],
};

// =============================================================================
// PAGE OUTLINE PANEL
// =============================================================================

export const pageOutlinePanel: PanelDefinition = {
  id: "yappc:page-outline",
  name: "Page Outline",
  icon: "file-tree",
  position: "left",
  order: 3,
  initiallyVisible: false,
  width: 260,

  sections: [
    {
      id: "pages",
      name: "Pages",
      collapsed: false,
    },
    {
      id: "components",
      name: "Components",
      collapsed: true,
    },
    {
      id: "layouts",
      name: "Layouts",
      collapsed: true,
    },
  ],
};

// =============================================================================
// CODE PANEL
// =============================================================================

export const codePanel: PanelDefinition = {
  id: "yappc:code",
  name: "Code",
  icon: "code",
  position: "bottom",
  order: 1,
  initiallyVisible: false,
  height: 300,

  sections: [
    {
      id: "generated",
      name: "Generated Code",
      collapsed: false,
    },
    {
      id: "custom",
      name: "Custom Code",
      collapsed: true,
    },
  ],
};

// =============================================================================
// ALL YAPPC PANELS
// =============================================================================

export const yappcPanels: readonly PanelDefinition[] = [
  componentPalettePanel,
  propertyInspectorPanel,
  layersPanel,
  pageOutlinePanel,
  codePanel,
];
