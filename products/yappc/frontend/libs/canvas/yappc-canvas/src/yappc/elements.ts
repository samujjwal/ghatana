/**
 * @ghatana/canvas YAPPC Elements
 *
 * Freeform canvas element definitions for YAPPC UI components.
 *
 * @doc.type module
 * @doc.purpose YAPPC element definitions
 * @doc.layer product
 * @doc.pattern ElementDefinitions
 */

import type { ElementDefinition, ElementConfig } from "../types";

// =============================================================================
// CONTAINER ELEMENT
// =============================================================================

export const containerElement: ElementDefinition = {
  type: "yappc:container",
  name: "Container",
  description: "A flexible container for grouping elements",
  icon: "square",
  category: "layout",

  defaultConfig: {
    width: 400,
    height: 300,
    properties: {
      display: "flex",
      flexDirection: "column",
      alignItems: "stretch",
      justifyContent: "flex-start",
      padding: 16,
      gap: 8,
      backgroundColor: "#ffffff",
      borderRadius: 8,
      border: "1px solid #e0e0e0",
    },
  },

  schema: {
    properties: {
      display: {
        type: "select",
        label: "Display",
        options: ["flex", "grid", "block"],
        default: "flex",
      },
      flexDirection: {
        type: "select",
        label: "Direction",
        options: ["row", "column", "row-reverse", "column-reverse"],
        default: "column",
      },
      alignItems: {
        type: "select",
        label: "Align Items",
        options: ["flex-start", "center", "flex-end", "stretch", "baseline"],
        default: "stretch",
      },
      justifyContent: {
        type: "select",
        label: "Justify Content",
        options: [
          "flex-start",
          "center",
          "flex-end",
          "space-between",
          "space-around",
          "space-evenly",
        ],
        default: "flex-start",
      },
      padding: {
        type: "spacing",
        label: "Padding",
        default: 16,
      },
      gap: {
        type: "number",
        label: "Gap",
        default: 8,
        min: 0,
        max: 100,
      },
      backgroundColor: {
        type: "color",
        label: "Background",
        default: "#ffffff",
      },
      borderRadius: {
        type: "number",
        label: "Border Radius",
        default: 8,
        min: 0,
        max: 50,
      },
    },
  },

  constraints: {
    minWidth: 50,
    minHeight: 50,
    maxWidth: 2000,
    maxHeight: 2000,
    aspectRatioLock: false,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: false,
    groupable: true,
    nestable: true,
    acceptsChildren: true,
    childTypes: ["yappc:*"],
  },
};

// =============================================================================
// TEXT ELEMENT
// =============================================================================

export const textElement: ElementDefinition = {
  type: "yappc:text",
  name: "Text",
  description: "A text element for labels, headings, and paragraphs",
  icon: "type",
  category: "basic",

  defaultConfig: {
    width: 200,
    height: 40,
    properties: {
      text: "Text",
      fontSize: 16,
      fontWeight: "normal",
      fontFamily: "Inter, sans-serif",
      color: "#1a1a1a",
      textAlign: "left",
      lineHeight: 1.5,
    },
  },

  schema: {
    properties: {
      text: {
        type: "text",
        label: "Text",
        default: "Text",
      },
      fontSize: {
        type: "number",
        label: "Font Size",
        default: 16,
        min: 8,
        max: 120,
      },
      fontWeight: {
        type: "select",
        label: "Font Weight",
        options: ["normal", "medium", "semibold", "bold"],
        default: "normal",
      },
      fontFamily: {
        type: "select",
        label: "Font Family",
        options: [
          "Inter, sans-serif",
          "Roboto, sans-serif",
          "Georgia, serif",
          "monospace",
        ],
        default: "Inter, sans-serif",
      },
      color: {
        type: "color",
        label: "Color",
        default: "#1a1a1a",
      },
      textAlign: {
        type: "select",
        label: "Alignment",
        options: ["left", "center", "right", "justify"],
        default: "left",
      },
      lineHeight: {
        type: "number",
        label: "Line Height",
        default: 1.5,
        min: 1,
        max: 3,
        step: 0.1,
      },
    },
  },

  constraints: {
    minWidth: 20,
    minHeight: 20,
    maxWidth: 1000,
    maxHeight: 1000,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: false,
    groupable: true,
    nestable: true,
  },
};

// =============================================================================
// BUTTON ELEMENT
// =============================================================================

export const buttonElement: ElementDefinition = {
  type: "yappc:button",
  name: "Button",
  description: "An interactive button element",
  icon: "mouse-pointer-click",
  category: "interactive",

  defaultConfig: {
    width: 120,
    height: 40,
    properties: {
      label: "Button",
      variant: "primary",
      size: "medium",
      disabled: false,
      onClick: "",
    },
  },

  schema: {
    properties: {
      label: {
        type: "text",
        label: "Label",
        default: "Button",
      },
      variant: {
        type: "select",
        label: "Variant",
        options: ["primary", "secondary", "outline", "ghost", "destructive"],
        default: "primary",
      },
      size: {
        type: "select",
        label: "Size",
        options: ["small", "medium", "large"],
        default: "medium",
      },
      disabled: {
        type: "boolean",
        label: "Disabled",
        default: false,
      },
      onClick: {
        type: "action",
        label: "On Click",
        default: "",
      },
    },
  },

  constraints: {
    minWidth: 60,
    minHeight: 32,
    maxWidth: 400,
    maxHeight: 80,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: true,
    groupable: true,
    nestable: true,
  },
};

// =============================================================================
// IMAGE ELEMENT
// =============================================================================

export const imageElement: ElementDefinition = {
  type: "yappc:image",
  name: "Image",
  description: "An image element",
  icon: "image",
  category: "media",

  defaultConfig: {
    width: 300,
    height: 200,
    properties: {
      src: "",
      alt: "Image",
      objectFit: "cover",
      borderRadius: 0,
    },
  },

  schema: {
    properties: {
      src: {
        type: "image",
        label: "Source",
        default: "",
      },
      alt: {
        type: "text",
        label: "Alt Text",
        default: "Image",
      },
      objectFit: {
        type: "select",
        label: "Object Fit",
        options: ["cover", "contain", "fill", "none", "scale-down"],
        default: "cover",
      },
      borderRadius: {
        type: "number",
        label: "Border Radius",
        default: 0,
        min: 0,
        max: 100,
      },
    },
  },

  constraints: {
    minWidth: 20,
    minHeight: 20,
    maxWidth: 2000,
    maxHeight: 2000,
    aspectRatioLock: true,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: false,
    groupable: true,
    nestable: true,
  },
};

// =============================================================================
// INPUT ELEMENT
// =============================================================================

export const inputElement: ElementDefinition = {
  type: "yappc:input",
  name: "Input",
  description: "A form input element",
  icon: "text-cursor-input",
  category: "form",

  defaultConfig: {
    width: 240,
    height: 40,
    properties: {
      type: "text",
      placeholder: "Enter text...",
      label: "",
      required: false,
      disabled: false,
    },
  },

  schema: {
    properties: {
      type: {
        type: "select",
        label: "Type",
        options: ["text", "email", "password", "number", "tel", "url", "date"],
        default: "text",
      },
      placeholder: {
        type: "text",
        label: "Placeholder",
        default: "Enter text...",
      },
      label: {
        type: "text",
        label: "Label",
        default: "",
      },
      required: {
        type: "boolean",
        label: "Required",
        default: false,
      },
      disabled: {
        type: "boolean",
        label: "Disabled",
        default: false,
      },
    },
  },

  constraints: {
    minWidth: 100,
    minHeight: 32,
    maxWidth: 600,
    maxHeight: 60,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: true,
    groupable: true,
    nestable: true,
  },
};

// =============================================================================
// CARD ELEMENT
// =============================================================================

export const cardElement: ElementDefinition = {
  type: "yappc:card",
  name: "Card",
  description: "A card container with header and content",
  icon: "rectangle-vertical",
  category: "layout",

  defaultConfig: {
    width: 320,
    height: 200,
    properties: {
      title: "Card Title",
      description: "",
      showHeader: true,
      padding: 16,
      shadow: "medium",
      borderRadius: 12,
    },
  },

  schema: {
    properties: {
      title: {
        type: "text",
        label: "Title",
        default: "Card Title",
      },
      description: {
        type: "text",
        label: "Description",
        default: "",
      },
      showHeader: {
        type: "boolean",
        label: "Show Header",
        default: true,
      },
      padding: {
        type: "number",
        label: "Padding",
        default: 16,
        min: 0,
        max: 48,
      },
      shadow: {
        type: "select",
        label: "Shadow",
        options: ["none", "small", "medium", "large"],
        default: "medium",
      },
      borderRadius: {
        type: "number",
        label: "Border Radius",
        default: 12,
        min: 0,
        max: 32,
      },
    },
  },

  constraints: {
    minWidth: 100,
    minHeight: 80,
    maxWidth: 800,
    maxHeight: 800,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: false,
    groupable: true,
    nestable: true,
    acceptsChildren: true,
    childTypes: ["yappc:*"],
  },
};

// =============================================================================
// LIST ELEMENT
// =============================================================================

export const listElement: ElementDefinition = {
  type: "yappc:list",
  name: "List",
  description: "A list of items",
  icon: "list",
  category: "data",

  defaultConfig: {
    width: 300,
    height: 200,
    properties: {
      items: ["Item 1", "Item 2", "Item 3"],
      listStyle: "none",
      gap: 8,
      divider: true,
    },
  },

  schema: {
    properties: {
      items: {
        type: "array",
        label: "Items",
        default: ["Item 1", "Item 2", "Item 3"],
      },
      listStyle: {
        type: "select",
        label: "List Style",
        options: ["none", "disc", "decimal", "check"],
        default: "none",
      },
      gap: {
        type: "number",
        label: "Gap",
        default: 8,
        min: 0,
        max: 32,
      },
      divider: {
        type: "boolean",
        label: "Show Divider",
        default: true,
      },
    },
  },

  constraints: {
    minWidth: 100,
    minHeight: 50,
    maxWidth: 600,
    maxHeight: 1000,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: true,
    groupable: true,
    nestable: true,
  },
};

// =============================================================================
// FORM ELEMENT
// =============================================================================

export const formElement: ElementDefinition = {
  type: "yappc:form",
  name: "Form",
  description: "A form container for inputs",
  icon: "clipboard-list",
  category: "form",

  defaultConfig: {
    width: 400,
    height: 300,
    properties: {
      action: "",
      method: "POST",
      layout: "vertical",
      gap: 16,
      submitLabel: "Submit",
    },
  },

  schema: {
    properties: {
      action: {
        type: "text",
        label: "Action URL",
        default: "",
      },
      method: {
        type: "select",
        label: "Method",
        options: ["GET", "POST", "PUT", "PATCH", "DELETE"],
        default: "POST",
      },
      layout: {
        type: "select",
        label: "Layout",
        options: ["vertical", "horizontal", "grid"],
        default: "vertical",
      },
      gap: {
        type: "number",
        label: "Gap",
        default: 16,
        min: 0,
        max: 48,
      },
      submitLabel: {
        type: "text",
        label: "Submit Label",
        default: "Submit",
      },
    },
  },

  constraints: {
    minWidth: 200,
    minHeight: 100,
    maxWidth: 800,
    maxHeight: 1200,
  },

  behavior: {
    resizable: true,
    rotatable: false,
    connectable: true,
    groupable: true,
    nestable: true,
    acceptsChildren: true,
    childTypes: ["yappc:input", "yappc:button", "yappc:text"],
  },
};

// =============================================================================
// ALL YAPPC ELEMENTS
// =============================================================================

export const yappcElements: readonly ElementDefinition[] = [
  containerElement,
  textElement,
  buttonElement,
  imageElement,
  inputElement,
  cardElement,
  listElement,
  formElement,
];
