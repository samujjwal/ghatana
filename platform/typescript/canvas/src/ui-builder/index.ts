/**
 * UI Builder - Component Palette and Property System
 * 
 * @doc.type module
 * @doc.purpose Provides UI Builder capabilities for high-fidelity UI design on canvas
 * @doc.layer core
 * @doc.pattern Builder
 * 
 * Implements UI building features for:
 * - Pre-built component library
 * - Drag-and-drop component placement
 * - Property editing
 * - Layout management
 * - Responsive design
 */

import { CanvasElement } from "../elements/base.js";
import { ShapeElement, ShapeType } from "../elements/shape.js";
import { RichTextElement } from "../elements/rich-text.js";
import { ImageElement } from "../elements/image.js";
import { TableElement } from "../elements/table.js";
import { FrameElement } from "../elements/frame.js";
import type { CanvasElementType, BaseElementProps } from "../types/index.js";
import { Bound } from "../utils/bounds.js";
import { themeManager } from "../theme/index.js";

/**
 * UI Component categories
 */
export type UIComponentCategory =
  | "layout"
  | "content"
  | "form"
  | "navigation"
  | "data"
  | "media"
  | "feedback"
  | "custom";

/**
 * UI Component definition
 */
export interface UIComponentDefinition {
  /** Unique component ID */
  id: string;
  /** Display name */
  name: string;
  /** Description */
  description: string;
  /** Category */
  category: UIComponentCategory;
  /** Icon (emoji or SVG path) */
  icon: string;
  /** Default size */
  defaultSize: { width: number; height: number };
  /** Factory function to create the component */
  factory: (x: number, y: number, id?: string) => CanvasElement;
  /** Property schema for the property editor */
  propertySchema: PropertySchema[];
  /** Preview image URL */
  previewImage?: string;
  /** Keywords for search */
  keywords: string[];
  /** Whether component is premium/pro */
  isPro?: boolean;
}

/**
 * Property types for the property editor
 */
export type PropertyType =
  | "string"
  | "number"
  | "boolean"
  | "color"
  | "select"
  | "multiselect"
  | "range"
  | "spacing"
  | "font"
  | "alignment"
  | "border"
  | "shadow"
  | "size"
  | "position";

/**
 * Property schema for property editor
 */
export interface PropertySchema {
  /** Property key */
  key: string;
  /** Display label */
  label: string;
  /** Property type */
  type: PropertyType;
  /** Default value */
  defaultValue: unknown;
  /** Category for grouping in editor */
  category?: "style" | "layout" | "content" | "advanced";
  /** Options for select/multiselect */
  options?: Array<{ label: string; value: unknown }>;
  /** Min value for number/range */
  min?: number;
  /** Max value for number/range */
  max?: number;
  /** Step for number/range */
  step?: number;
  /** Unit (px, %, em, etc.) */
  unit?: string;
  /** Whether property is required */
  required?: boolean;
  /** Validation function */
  validate?: (value: unknown) => boolean | string;
}

/**
 * UI Component Library
 */
export class UIComponentLibrary {
  private components: Map<string, UIComponentDefinition> = new Map();
  private categories: Map<UIComponentCategory, UIComponentDefinition[]> = new Map();

  constructor() {
    this.registerBuiltInComponents();
  }

  /**
   * Register built-in UI components
   */
  private registerBuiltInComponents(): void {
    // Layout Components
    this.register({
      id: "container",
      name: "Container",
      description: "A flexible container for grouping elements",
      category: "layout",
      icon: "📦",
      defaultSize: { width: 400, height: 300 },
      factory: (x, y, id) => new FrameElement({
        id: id || `container-${Date.now()}`,
        xywh: JSON.stringify([x, y, 400, 300]),
        index: Date.now().toString(),
        title: "Container",
        backgroundColor: "rgba(255, 255, 255, 0.9)",
        borderRadius: 12,
        showTitle: false,
      }),
      propertySchema: [
        { key: "backgroundColor", label: "Background", type: "color", defaultValue: "#ffffff", category: "style" },
        { key: "borderRadius", label: "Border Radius", type: "number", defaultValue: 12, min: 0, max: 50, unit: "px", category: "style" },
        { key: "borderColor", label: "Border Color", type: "color", defaultValue: "#e5e7eb", category: "style" },
        { key: "borderWidth", label: "Border Width", type: "number", defaultValue: 1, min: 0, max: 10, unit: "px", category: "style" },
      ],
      keywords: ["container", "box", "div", "wrapper", "frame"],
    });

    this.register({
      id: "card",
      name: "Card",
      description: "A card component with shadow and rounded corners",
      category: "layout",
      icon: "🃏",
      defaultSize: { width: 320, height: 200 },
      factory: (x, y, id) => new FrameElement({
        id: id || `card-${Date.now()}`,
        xywh: JSON.stringify([x, y, 320, 200]),
        index: Date.now().toString(),
        title: "",
        backgroundColor: "#ffffff",
        borderRadius: 16,
        showTitle: false,
        shadow: { offsetX: 0, offsetY: 4, blur: 16, color: "rgba(0,0,0,0.1)" },
      } as unknown as ConstructorParameters<typeof FrameElement>[0]),
      propertySchema: [
        { key: "backgroundColor", label: "Background", type: "color", defaultValue: "#ffffff", category: "style" },
        { key: "borderRadius", label: "Border Radius", type: "number", defaultValue: 16, min: 0, max: 50, unit: "px", category: "style" },
      ],
      keywords: ["card", "panel", "tile"],
    });

    // Content Components
    this.register({
      id: "heading",
      name: "Heading",
      description: "A text heading (H1-H6)",
      category: "content",
      icon: "📝",
      defaultSize: { width: 300, height: 48 },
      factory: (x, y, id) => new RichTextElement({
        id: id || `heading-${Date.now()}`,
        xywh: JSON.stringify([x, y, 300, 48]),
        index: Date.now().toString(),
        content: [{ text: "Heading", marks: [{ type: "bold" }] }],
        headingLevel: 1,
        fontSize: 24,
      }),
      propertySchema: [
        {
          key: "headingLevel", label: "Level", type: "select", defaultValue: 1, options: [
            { label: "H1", value: 1 },
            { label: "H2", value: 2 },
            { label: "H3", value: 3 },
            { label: "H4", value: 4 },
            { label: "H5", value: 5 },
            { label: "H6", value: 6 },
          ], category: "content"
        },
        { key: "color", label: "Color", type: "color", defaultValue: "#111827", category: "style" },
        { key: "textAlign", label: "Alignment", type: "alignment", defaultValue: "left", category: "style" },
      ],
      keywords: ["heading", "title", "h1", "h2", "h3", "header"],
    });

    this.register({
      id: "paragraph",
      name: "Paragraph",
      description: "A paragraph of text",
      category: "content",
      icon: "📄",
      defaultSize: { width: 400, height: 100 },
      factory: (x, y, id) => new RichTextElement({
        id: id || `paragraph-${Date.now()}`,
        xywh: JSON.stringify([x, y, 400, 100]),
        index: Date.now().toString(),
        content: [{ text: "Enter your text here..." }],
        fontSize: 16,
        lineHeight: 1.6,
      }),
      propertySchema: [
        { key: "fontSize", label: "Font Size", type: "number", defaultValue: 16, min: 8, max: 72, unit: "px", category: "style" },
        { key: "lineHeight", label: "Line Height", type: "number", defaultValue: 1.6, min: 1, max: 3, step: 0.1, category: "style" },
        { key: "color", label: "Color", type: "color", defaultValue: "#374151", category: "style" },
        { key: "textAlign", label: "Alignment", type: "alignment", defaultValue: "left", category: "style" },
      ],
      keywords: ["paragraph", "text", "body", "content"],
    });

    // Form Components
    this.register({
      id: "button",
      name: "Button",
      description: "A clickable button",
      category: "form",
      icon: "🔘",
      defaultSize: { width: 120, height: 44 },
      factory: (x, y, id) => new ShapeElement({
        id: id || `button-${Date.now()}`,
        xywh: JSON.stringify([x, y, 120, 44]),
        index: Date.now().toString(),
        shapeType: "rect",
        fillColor: "#3b82f6",
        strokeColor: "#2563eb",
        strokeWidth: 0,
        radius: 8,
        text: "Button",
        textColor: "#ffffff",
        fontSize: 14,
      }),
      propertySchema: [
        { key: "text", label: "Label", type: "string", defaultValue: "Button", category: "content" },
        { key: "fillColor", label: "Background", type: "color", defaultValue: "#3b82f6", category: "style" },
        { key: "textColor", label: "Text Color", type: "color", defaultValue: "#ffffff", category: "style" },
        { key: "radius", label: "Border Radius", type: "number", defaultValue: 8, min: 0, max: 22, unit: "px", category: "style" },
      ],
      keywords: ["button", "btn", "submit", "action", "cta"],
    });

    this.register({
      id: "input",
      name: "Text Input",
      description: "A text input field",
      category: "form",
      icon: "📝",
      defaultSize: { width: 280, height: 44 },
      factory: (x, y, id) => new ShapeElement({
        id: id || `input-${Date.now()}`,
        xywh: JSON.stringify([x, y, 280, 44]),
        index: Date.now().toString(),
        shapeType: "rect",
        fillColor: "#ffffff",
        strokeColor: "#d1d5db",
        strokeWidth: 1,
        radius: 8,
        text: "Placeholder text...",
        textColor: "#9ca3af",
        fontSize: 14,
        textAlign: "left",
        padding: [12, 12],
      }),
      propertySchema: [
        { key: "text", label: "Placeholder", type: "string", defaultValue: "Enter text...", category: "content" },
        { key: "strokeColor", label: "Border Color", type: "color", defaultValue: "#d1d5db", category: "style" },
        { key: "radius", label: "Border Radius", type: "number", defaultValue: 8, min: 0, max: 22, unit: "px", category: "style" },
      ],
      keywords: ["input", "field", "text", "textbox", "form"],
    });

    this.register({
      id: "checkbox",
      name: "Checkbox",
      description: "A checkbox input",
      category: "form",
      icon: "☑️",
      defaultSize: { width: 200, height: 24 },
      factory: (x, y, id) => new ShapeElement({
        id: id || `checkbox-${Date.now()}`,
        xywh: JSON.stringify([x, y, 200, 24]),
        index: Date.now().toString(),
        shapeType: "rect",
        fillColor: "transparent",
        strokeColor: "transparent",
        strokeWidth: 0,
        text: "☐ Checkbox label",
        textColor: "#374151",
        fontSize: 14,
        textAlign: "left",
      }),
      propertySchema: [
        { key: "text", label: "Label", type: "string", defaultValue: "☐ Checkbox label", category: "content" },
        { key: "textColor", label: "Text Color", type: "color", defaultValue: "#374151", category: "style" },
      ],
      keywords: ["checkbox", "check", "toggle", "option"],
    });

    // Data Components
    this.register({
      id: "table",
      name: "Table",
      description: "A data table",
      category: "data",
      icon: "📊",
      defaultSize: { width: 500, height: 300 },
      factory: (x, y, id) => new TableElement({
        id: id || `table-${Date.now()}`,
        xywh: JSON.stringify([x, y, 500, 300]),
        index: Date.now().toString(),
        columns: [
          { id: "col1", header: "Name", width: 150, align: "left" },
          { id: "col2", header: "Value", width: 100, align: "right" },
          { id: "col3", header: "Status", width: 100, align: "center" },
        ],
        rows: [
          { id: "row1", cells: [{ content: "Item 1" }, { content: "100" }, { content: "Active" }] },
          { id: "row2", cells: [{ content: "Item 2" }, { content: "200" }, { content: "Pending" }] },
          { id: "row3", cells: [{ content: "Item 3" }, { content: "300" }, { content: "Done" }] },
        ],
        showHeader: true,
        alternatingRowColors: true,
        borderRadius: 8,
      }),
      propertySchema: [
        { key: "showHeader", label: "Show Header", type: "boolean", defaultValue: true, category: "layout" },
        { key: "alternatingRowColors", label: "Alternating Colors", type: "boolean", defaultValue: true, category: "style" },
        { key: "borderRadius", label: "Border Radius", type: "number", defaultValue: 8, min: 0, max: 20, unit: "px", category: "style" },
      ],
      keywords: ["table", "grid", "data", "spreadsheet"],
    });

    // Media Components
    this.register({
      id: "image",
      name: "Image",
      description: "An image placeholder",
      category: "media",
      icon: "🖼️",
      defaultSize: { width: 300, height: 200 },
      factory: (x, y, id) => new ImageElement({
        id: id || `image-${Date.now()}`,
        xywh: JSON.stringify([x, y, 300, 200]),
        index: Date.now().toString(),
        src: "",
        alt: "Image placeholder",
        fitMode: "cover",
        borderRadius: 8,
        placeholderColor: "#f3f4f6",
      }),
      propertySchema: [
        { key: "src", label: "Source URL", type: "string", defaultValue: "", category: "content" },
        { key: "alt", label: "Alt Text", type: "string", defaultValue: "Image", category: "content" },
        {
          key: "fitMode", label: "Fit Mode", type: "select", defaultValue: "cover", options: [
            { label: "Cover", value: "cover" },
            { label: "Contain", value: "contain" },
            { label: "Fill", value: "fill" },
            { label: "None", value: "none" },
          ], category: "layout"
        },
        { key: "borderRadius", label: "Border Radius", type: "number", defaultValue: 8, min: 0, max: 100, unit: "px", category: "style" },
      ],
      keywords: ["image", "picture", "photo", "media"],
    });

    this.register({
      id: "icon",
      name: "Icon",
      description: "An icon element",
      category: "media",
      icon: "⭐",
      defaultSize: { width: 48, height: 48 },
      factory: (x, y, id) => new ShapeElement({
        id: id || `icon-${Date.now()}`,
        xywh: JSON.stringify([x, y, 48, 48]),
        index: Date.now().toString(),
        shapeType: "rect",
        fillColor: "transparent",
        strokeColor: "transparent",
        strokeWidth: 0,
        text: "⭐",
        fontSize: 32,
      }),
      propertySchema: [
        { key: "text", label: "Icon", type: "string", defaultValue: "⭐", category: "content" },
        { key: "fontSize", label: "Size", type: "number", defaultValue: 32, min: 12, max: 128, unit: "px", category: "style" },
      ],
      keywords: ["icon", "emoji", "symbol"],
    });

    // Navigation Components
    this.register({
      id: "navbar",
      name: "Navigation Bar",
      description: "A horizontal navigation bar",
      category: "navigation",
      icon: "🧭",
      defaultSize: { width: 600, height: 64 },
      factory: (x, y, id) => new FrameElement({
        id: id || `navbar-${Date.now()}`,
        xywh: JSON.stringify([x, y, 600, 64]),
        index: Date.now().toString(),
        title: "",
        backgroundColor: "#ffffff",
        borderColor: "#e5e7eb",
        borderWidth: 1,
        borderRadius: 0,
        showTitle: false,
      }),
      propertySchema: [
        { key: "backgroundColor", label: "Background", type: "color", defaultValue: "#ffffff", category: "style" },
        { key: "borderColor", label: "Border Color", type: "color", defaultValue: "#e5e7eb", category: "style" },
      ],
      keywords: ["navbar", "nav", "header", "menu", "navigation"],
    });

    // Feedback Components
    this.register({
      id: "badge",
      name: "Badge",
      description: "A small badge/tag element",
      category: "feedback",
      icon: "🏷️",
      defaultSize: { width: 80, height: 28 },
      factory: (x, y, id) => new ShapeElement({
        id: id || `badge-${Date.now()}`,
        xywh: JSON.stringify([x, y, 80, 28]),
        index: Date.now().toString(),
        shapeType: "rect",
        fillColor: "#dbeafe",
        strokeColor: "transparent",
        strokeWidth: 0,
        radius: 14,
        text: "Badge",
        textColor: "#1d4ed8",
        fontSize: 12,
      }),
      propertySchema: [
        { key: "text", label: "Text", type: "string", defaultValue: "Badge", category: "content" },
        { key: "fillColor", label: "Background", type: "color", defaultValue: "#dbeafe", category: "style" },
        { key: "textColor", label: "Text Color", type: "color", defaultValue: "#1d4ed8", category: "style" },
      ],
      keywords: ["badge", "tag", "label", "chip"],
    });

    this.register({
      id: "avatar",
      name: "Avatar",
      description: "A circular avatar placeholder",
      category: "feedback",
      icon: "👤",
      defaultSize: { width: 48, height: 48 },
      factory: (x, y, id) => new ShapeElement({
        id: id || `avatar-${Date.now()}`,
        xywh: JSON.stringify([x, y, 48, 48]),
        index: Date.now().toString(),
        shapeType: "circle",
        fillColor: "#e5e7eb",
        strokeColor: "transparent",
        strokeWidth: 0,
        text: "👤",
        fontSize: 24,
      }),
      propertySchema: [
        { key: "text", label: "Initials/Icon", type: "string", defaultValue: "👤", category: "content" },
        { key: "fillColor", label: "Background", type: "color", defaultValue: "#e5e7eb", category: "style" },
      ],
      keywords: ["avatar", "user", "profile", "picture"],
    });
  }

  /**
   * Register a component definition
   */
  register(definition: UIComponentDefinition): void {
    this.components.set(definition.id, definition);

    // Add to category list
    const categoryList = this.categories.get(definition.category) || [];
    categoryList.push(definition);
    this.categories.set(definition.category, categoryList);
  }

  /**
   * Unregister a component
   */
  unregister(id: string): void {
    const definition = this.components.get(id);
    if (definition) {
      this.components.delete(id);

      const categoryList = this.categories.get(definition.category) || [];
      const index = categoryList.findIndex(c => c.id === id);
      if (index !== -1) {
        categoryList.splice(index, 1);
      }
    }
  }

  /**
   * Get a component by ID
   */
  get(id: string): UIComponentDefinition | undefined {
    return this.components.get(id);
  }

  /**
   * Get all components
   */
  getAll(): UIComponentDefinition[] {
    return Array.from(this.components.values());
  }

  /**
   * Get components by category
   */
  getByCategory(category: UIComponentCategory): UIComponentDefinition[] {
    return this.categories.get(category) || [];
  }

  /**
   * Get all categories
   */
  getCategories(): UIComponentCategory[] {
    return Array.from(this.categories.keys());
  }

  /**
   * Search components
   */
  search(query: string): UIComponentDefinition[] {
    const lowerQuery = query.toLowerCase();
    return this.getAll().filter(component =>
      component.name.toLowerCase().includes(lowerQuery) ||
      component.description.toLowerCase().includes(lowerQuery) ||
      component.keywords.some(kw => kw.toLowerCase().includes(lowerQuery))
    );
  }

  /**
   * Create a component instance
   */
  createComponent(id: string, x: number, y: number): CanvasElement | null {
    const definition = this.components.get(id);
    if (!definition) {
      console.warn(`[UIComponentLibrary] Unknown component: ${id}`);
      return null;
    }

    return definition.factory(x, y);
  }
}

/**
 * Property Editor state for a selected element
 */
export interface PropertyEditorState {
  element: CanvasElement | null;
  properties: Map<string, unknown>;
  schema: PropertySchema[];
  isDirty: boolean;
}

/**
 * Property Editor Manager
 */
export class PropertyEditorManager {
  private state: PropertyEditorState = {
    element: null,
    properties: new Map(),
    schema: [],
    isDirty: false,
  };

  private listeners: Set<(state: PropertyEditorState) => void> = new Set();

  /**
   * Select an element for editing
   */
  selectElement(element: CanvasElement, schema: PropertySchema[]): void {
    // Extract current property values from element
    const properties = new Map<string, unknown>();

    for (const prop of schema) {
      const value = (element as unknown as Record<string, unknown>)[prop.key];
      properties.set(prop.key, value ?? prop.defaultValue);
    }

    this.state = {
      element,
      properties,
      schema,
      isDirty: false,
    };

    this.notifyListeners();
  }

  /**
   * Clear selection
   */
  clearSelection(): void {
    this.state = {
      element: null,
      properties: new Map(),
      schema: [],
      isDirty: false,
    };

    this.notifyListeners();
  }

  /**
   * Update a property value
   */
  setProperty(key: string, value: unknown): void {
    if (!this.state.element) return;

    this.state.properties.set(key, value);
    this.state.isDirty = true;

    // Apply to element
    (this.state.element as unknown as Record<string, unknown>)[key] = value;

    this.notifyListeners();
  }

  /**
   * Get current property value
   */
  getProperty(key: string): unknown {
    return this.state.properties.get(key);
  }

  /**
   * Get all properties
   */
  getProperties(): Map<string, unknown> {
    return new Map(this.state.properties);
  }

  /**
   * Get property schema
   */
  getSchema(): PropertySchema[] {
    return [...this.state.schema];
  }

  /**
   * Get schema by category
   */
  getSchemaByCategory(category: PropertySchema["category"]): PropertySchema[] {
    return this.state.schema.filter(p => p.category === category);
  }

  /**
   * Check if a property is valid
   */
  validateProperty(key: string): boolean | string {
    const prop = this.state.schema.find(p => p.key === key);
    if (!prop || !prop.validate) return true;

    const value = this.state.properties.get(key);
    return prop.validate(value);
  }

  /**
   * Validate all properties
   */
  validateAll(): Map<string, boolean | string> {
    const results = new Map<string, boolean | string>();

    for (const prop of this.state.schema) {
      results.set(prop.key, this.validateProperty(prop.key));
    }

    return results;
  }

  /**
   * Subscribe to state changes
   */
  subscribe(listener: (state: PropertyEditorState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners(): void {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }
}

// Export singleton instances
export const uiComponentLibrary = new UIComponentLibrary();
export const propertyEditorManager = new PropertyEditorManager();

// ==================================
// REACT COMPONENT RENDERING (Ghatana UI Integration)
// ==================================
export * from "../elements/ui-component.js";
export * from "../managers/dom-overlay-manager.js";
export * from "../managers/react-component-renderer.js";
