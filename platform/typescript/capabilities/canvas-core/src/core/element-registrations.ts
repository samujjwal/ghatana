/**
 * Default Element Registrations
 * 
 * @doc.type module
 * @doc.purpose Registers all built-in elements with the ElementRegistry
 * @doc.layer core
 * @doc.pattern Registry
 * 
 * This module initializes the ElementRegistry with all available
 * canvas elements, enabling dynamic element creation and rendering.
 */

import { elementRegistry } from "./element-registry.js";
import type { ElementDefinition } from "./element-registry.js";

// Core elements
import { ShapeElement, type ShapeProps } from "../elements/shape.js";
import { TextElement, type TextProps } from "../elements/text.js";
import { BrushElement, type BrushProps } from "../elements/brush.js";
import { ConnectorElement, type ConnectorProps } from "../elements/connector.js";
import { CodeElement, type CodeProps } from "../elements/code.js";
import { DiagramElement, type DiagramProps } from "../elements/diagram.js";
import { GroupElement, type GroupProps } from "../elements/group.js";
import { FrameElement, type FrameProps } from "../elements/frame.js";
import { MindmapElement, type MindmapProps } from "../elements/mindmap.js";
import { HighlighterElement, type HighlighterProps } from "../elements/highlighter.js";
import { PipelineNodeElement, type PipelineNodeProps } from "../elements/pipeline-node.js";

// Rich content elements (AFFiNE parity)
import { ImageElement, type ImageProps } from "../elements/image.js";
import { AttachmentElement, type AttachmentProps } from "../elements/attachment.js";
import { EmbedElement, type EmbedProps } from "../elements/embed.js";
import { RichTextElement, type RichTextProps } from "../elements/rich-text.js";
import { NoteElement, type NoteProps } from "../elements/note.js";
import { TableElement, type TableProps } from "../elements/table.js";
import { CalloutElement, type CalloutProps } from "../elements/callout.js";
import { ListElement, type ListElementProps } from "../elements/list.js";
import { DividerElement, type DividerElementProps } from "../elements/divider.js";
import { LatexElement, type LatexElementProps } from "../elements/latex.js";
import { BookmarkElement, type BookmarkElementProps } from "../elements/bookmark.js";

import type { BaseElementProps } from "../types/index.js";

/**
 * Register all built-in elements
 */
export function registerBuiltInElements(): void {
  // Core drawing elements
  elementRegistry.register<ShapeProps>({
    type: "shape",
    name: "Shape",
    description: "Basic geometric shapes (rectangle, ellipse, triangle, diamond)",
    category: "drawing",
    icon: "⬜",
    factory: (props) => new ShapeElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,100,100]",
      index: "0",
      shapeType: "rect",
      fillColor: "#ffffff",
      strokeColor: "#000000",
      strokeWidth: 2,
    },
    capabilities: {
      resizable: true,
      rotatable: true,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<TextProps>({
    type: "text",
    name: "Text",
    description: "Simple text element",
    category: "content",
    icon: "T",
    factory: (props) => new TextElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,200,50]",
      index: "0",
      text: "Text",
      fontSize: 16,
      fontFamily: "Inter",
      color: "#000000",
    },
    capabilities: {
      resizable: true,
      rotatable: true,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<BrushProps>({
    type: "brush",
    name: "Brush",
    description: "Freehand drawing strokes",
    category: "drawing",
    icon: "🖌️",
    factory: (props) => new BrushElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,100,100]",
      index: "0",
      points: [],
      color: "#000000",
      lineWidth: 2,
    },
    capabilities: {
      resizable: false,
      rotatable: false,
      connectable: false,
      groupable: true,
      editable: false,
    },
  });

  elementRegistry.register<ConnectorProps>({
    type: "connector",
    name: "Connector",
    description: "Lines connecting elements",
    category: "drawing",
    icon: "↗️",
    factory: (props) => new ConnectorElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,100,100]",
      index: "0",
      startPoint: { x: 0, y: 0 },
      endPoint: { x: 100, y: 100 },
      strokeColor: "#000000",
      strokeWidth: 2,
    },
    capabilities: {
      resizable: false,
      rotatable: false,
      connectable: false,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<CodeProps>({
    type: "code",
    name: "Code",
    description: "Syntax-highlighted code blocks",
    category: "content",
    icon: "💻",
    factory: (props) => new CodeElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,400,200]",
      index: "0",
      code: "",
      language: "javascript",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<DiagramProps>({
    type: "diagram",
    name: "Diagram",
    description: "Structured diagrams",
    category: "structure",
    icon: "📊",
    factory: (props) => new DiagramElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,300,200]",
      index: "0",
      diagramType: "flowchart",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<GroupProps>({
    type: "group",
    name: "Group",
    description: "Groups multiple elements together",
    category: "structure",
    icon: "📦",
    factory: (props) => new GroupElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,200,200]",
      index: "0",
    },
    capabilities: {
      resizable: true,
      rotatable: true,
      connectable: true,
      groupable: false,
      editable: false,
    },
  });

  elementRegistry.register<FrameProps>({
    type: "frame",
    name: "Frame",
    description: "Presentation frame/slide container",
    category: "structure",
    icon: "🖼️",
    factory: (props) => new FrameElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,800,600]",
      index: "0",
      title: "Frame",
      backgroundColor: "#ffffff",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: false,
      groupable: false,
      editable: true,
    },
  });

  elementRegistry.register<MindmapProps>({
    type: "mindmap",
    name: "Mindmap",
    description: "Mind map with hierarchical nodes",
    category: "structure",
    icon: "🧠",
    factory: (props) => new MindmapElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,400,300]",
      index: "0",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<HighlighterProps>({
    type: "highlighter",
    name: "Highlighter",
    description: "Semi-transparent highlight strokes",
    category: "drawing",
    icon: "🖍️",
    factory: (props) => new HighlighterElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,100,100]",
      index: "0",
      points: [],
      color: "#FFFF00",
      lineWidth: 20,
      opacity: 0.4,
    },
    capabilities: {
      resizable: false,
      rotatable: false,
      connectable: false,
      groupable: true,
      editable: false,
    },
  });

  elementRegistry.register<PipelineNodeProps>({
    type: "pipeline-node",
    name: "Pipeline Node",
    description: "Data pipeline processing node",
    category: "data",
    icon: "⚙️",
    factory: (props) => new PipelineNodeElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,200,120]",
      index: "0",
      nodeType: "transform",
      label: "Node",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  // Rich content elements (AFFiNE parity)
  elementRegistry.register<ImageProps>({
    type: "image",
    name: "Image",
    description: "Images with lazy loading and filters",
    category: "media",
    icon: "🖼️",
    factory: (props) => new ImageElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,300,200]",
      index: "0",
      src: "",
      alt: "Image",
      fitMode: "cover",
    },
    capabilities: {
      resizable: true,
      rotatable: true,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<AttachmentProps>({
    type: "attachment",
    name: "Attachment",
    description: "File attachments with preview",
    category: "media",
    icon: "📎",
    factory: (props) => new AttachmentElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,280,80]",
      index: "0",
      name: "file.pdf",
      size: 0,
      mimeType: "application/pdf",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<EmbedProps>({
    type: "embed",
    name: "Embed",
    description: "Embedded content (YouTube, Figma, etc.)",
    category: "media",
    icon: "🌐",
    factory: (props) => new EmbedElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,560,315]",
      index: "0",
      embedType: "iframe",
      url: "",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<RichTextProps>({
    type: "rich-text",
    name: "Rich Text",
    description: "Formatted text with inline styles",
    category: "content",
    icon: "📝",
    factory: (props) => new RichTextElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,300,100]",
      index: "0",
      content: [],
      fontSize: 16,
    },
    capabilities: {
      resizable: true,
      rotatable: true,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<NoteProps>({
    type: "note",
    name: "Note",
    description: "Document-like container with blocks",
    category: "content",
    icon: "📄",
    factory: (props) => new NoteElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,400,300]",
      index: "0",
      children: [],
      backgroundColor: "#ffffff",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<TableProps>({
    type: "table",
    name: "Table",
    description: "Editable data tables",
    category: "data",
    icon: "📊",
    factory: (props) => new TableElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,500,300]",
      index: "0",
      columns: [],
      rows: [],
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<CalloutProps>({
    type: "callout",
    name: "Callout",
    description: "Info/warning/tip blocks",
    category: "content",
    icon: "💡",
    factory: (props) => new CalloutElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,400,100]",
      index: "0",
      calloutType: "info",
      title: "",
      content: "",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<ListElementProps>({
    type: "list",
    name: "List",
    description: "Ordered, unordered, and checkbox lists",
    category: "content",
    icon: "📋",
    factory: (props) => new ListElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,300,200]",
      index: "0",
      listType: "unordered",
      items: [],
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<DividerElementProps>({
    type: "divider",
    name: "Divider",
    description: "Horizontal or vertical dividers",
    category: "structure",
    icon: "➖",
    factory: (props) => new DividerElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,300,20]",
      index: "0",
      orientation: "horizontal",
      lineStyle: "solid",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: false,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<LatexElementProps>({
    type: "latex",
    name: "LaTeX",
    description: "Mathematical equations",
    category: "content",
    icon: "∑",
    factory: (props) => new LatexElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,300,60]",
      index: "0",
      latex: "",
      displayMode: "display",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  elementRegistry.register<BookmarkElementProps>({
    type: "bookmark",
    name: "Bookmark",
    description: "URL bookmarks with preview cards",
    category: "media",
    icon: "🔗",
    factory: (props) => new BookmarkElement(props),
    defaultProps: {
      id: "",
      xywh: "[0,0,400,160]",
      index: "0",
      url: "",
      displayMode: "card",
    },
    capabilities: {
      resizable: true,
      rotatable: false,
      connectable: true,
      groupable: true,
      editable: true,
    },
  });

  console.log(`[ElementRegistrations] Registered ${elementRegistry.getRegisteredTypes().length} element types`);
}

// Auto-register on module load
registerBuiltInElements();
