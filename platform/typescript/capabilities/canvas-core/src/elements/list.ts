/**
 * List Canvas Element
 * 
 * @doc.type class
 * @doc.purpose Renders ordered, unordered, and checkbox lists on canvas
 * @doc.layer core
 * @doc.pattern ValueObject
 * 
 * Features:
 * - Ordered (numbered) lists
 * - Unordered (bullet) lists
 * - Checkbox/todo lists
 * - Nested list support
 * - Custom markers/icons
 * - Rich text item content
 */

import { CanvasElement } from "./base.js";
import type { BaseElementProps, CanvasElementType } from "../types/index.js";
import { Bound } from "../utils/bounds.js";
import { themeManager } from "../theme/index.js";

/**
 * List type
 */
export type ListType = "ordered" | "unordered" | "checkbox";

/**
 * Bullet style for unordered lists
 */
export type BulletStyle = "disc" | "circle" | "square" | "dash" | "arrow" | "custom";

/**
 * Numbering style for ordered lists
 */
export type NumberingStyle = "decimal" | "alpha-lower" | "alpha-upper" | "roman-lower" | "roman-upper";

/**
 * List item
 */
export interface ListItem {
  id: string;
  content: string;
  /** Whether checkbox is checked (for checkbox lists) */
  checked?: boolean;
  /** Nested children */
  children?: ListItem[];
  /** Indent level (0-based) */
  indent?: number;
  /** Custom marker/icon */
  customMarker?: string;
}

/**
 * List element properties
 */
export interface ListElementProps extends BaseElementProps {
  /** List type */
  listType: ListType;
  /** List items */
  items: ListItem[];
  /** Bullet style (for unordered lists) */
  bulletStyle?: BulletStyle;
  /** Custom bullet character */
  customBullet?: string;
  /** Numbering style (for ordered lists) */
  numberingStyle?: NumberingStyle;
  /** Starting number (for ordered lists) */
  startNumber?: number;
  /** Text color */
  textColor?: string;
  /** Font size */
  fontSize?: number;
  /** Font family */
  fontFamily?: string;
  /** Line height multiplier */
  lineHeight?: number;
  /** Item spacing */
  itemSpacing?: number;
  /** Indent per level */
  indentSize?: number;
  /** Marker color */
  markerColor?: string;
  /** Show marker */
  showMarker?: boolean;
  /** Strikethrough when checked */
  strikethroughChecked?: boolean;
  /** Checked item opacity */
  checkedOpacity?: number;
  /** Border radius */
  borderRadius?: number;
  /** Background color */
  backgroundColor?: string;
  /** Padding */
  padding?: number;
}

/**
 * Bullet markers by style
 */
const BULLET_MARKERS: Record<BulletStyle, string> = {
  disc: "•",
  circle: "◦",
  square: "▪",
  dash: "–",
  arrow: "→",
  custom: "",
};

/**
 * Roman numeral conversion
 */
function toRoman(num: number, isLower: boolean): string {
  const romanNumerals: [number, string][] = [
    [1000, "M"],
    [900, "CM"],
    [500, "D"],
    [400, "CD"],
    [100, "C"],
    [90, "XC"],
    [50, "L"],
    [40, "XL"],
    [10, "X"],
    [9, "IX"],
    [5, "V"],
    [4, "IV"],
    [1, "I"],
  ];

  let result = "";
  for (const [value, symbol] of romanNumerals) {
    while (num >= value) {
      result += symbol;
      num -= value;
    }
  }

  return isLower ? result.toLowerCase() : result;
}

/**
 * Get number marker based on style
 */
function getNumberMarker(num: number, style: NumberingStyle): string {
  switch (style) {
    case "decimal":
      return `${num}.`;
    case "alpha-lower":
      return `${String.fromCharCode(96 + ((num - 1) % 26) + 1)}.`;
    case "alpha-upper":
      return `${String.fromCharCode(64 + ((num - 1) % 26) + 1)}.`;
    case "roman-lower":
      return `${toRoman(num, true)}.`;
    case "roman-upper":
      return `${toRoman(num, false)}.`;
    default:
      return `${num}.`;
  }
}

/**
 * List Canvas Element
 */
export class ListElement extends CanvasElement {
  listType: ListType;
  items: ListItem[];
  bulletStyle: BulletStyle;
  customBullet: string;
  numberingStyle: NumberingStyle;
  startNumber: number;
  textColor: string;
  fontSize: number;
  fontFamily: string;
  lineHeight: number;
  itemSpacing: number;
  indentSize: number;
  markerColor: string;
  showMarker: boolean;
  strikethroughChecked: boolean;
  checkedOpacity: number;
  borderRadius: number;
  backgroundColor: string;
  padding: number;

  constructor(props: ListElementProps) {
    super(props);
    this.listType = props.listType;
    this.items = props.items || [];
    this.bulletStyle = props.bulletStyle || "disc";
    this.customBullet = props.customBullet || "•";
    this.numberingStyle = props.numberingStyle || "decimal";
    this.startNumber = props.startNumber || 1;
    this.textColor = props.textColor || themeManager.getTheme().colors.text.primary;
    this.fontSize = props.fontSize || 14;
    this.fontFamily = props.fontFamily || "Inter, -apple-system, sans-serif";
    this.lineHeight = props.lineHeight || 1.6;
    this.itemSpacing = props.itemSpacing || 4;
    this.indentSize = props.indentSize || 24;
    this.markerColor = props.markerColor || themeManager.getTheme().colors.text.muted;
    this.showMarker = props.showMarker !== false;
    this.strikethroughChecked = props.strikethroughChecked !== false;
    this.checkedOpacity = props.checkedOpacity || 0.6;
    this.borderRadius = props.borderRadius || 0;
    this.backgroundColor = props.backgroundColor || "transparent";
    this.padding = props.padding || 0;
  }

  get type(): CanvasElementType {
    return "list";
  }

  /**
   * Flatten items with their visual indices
   */
  private flattenItems(
    items: ListItem[],
    parentIndex: number[] = [],
    startNum: number = this.startNumber
  ): Array<{ item: ListItem; index: number[]; depth: number; visualIndex: number }> {
    const result: Array<{ item: ListItem; index: number[]; depth: number; visualIndex: number }> = [];
    
    items.forEach((item, i) => {
      const currentIndex = [...parentIndex, i];
      const depth = item.indent || parentIndex.length;
      
      result.push({
        item,
        index: currentIndex,
        depth,
        visualIndex: startNum + i,
      });

      // Process children recursively
      if (item.children && item.children.length > 0) {
        result.push(...this.flattenItems(item.children, currentIndex, 1));
      }
    });

    return result;
  }

  /**
   * Get marker for an item
   */
  private getMarker(visualIndex: number, depth: number): string {
    if (!this.showMarker) return "";

    switch (this.listType) {
      case "ordered":
        return getNumberMarker(visualIndex, this.numberingStyle);
      
      case "unordered":
        if (this.bulletStyle === "custom") {
          return this.customBullet;
        }
        // Cycle through bullet styles for nested levels
        const styles: BulletStyle[] = ["disc", "circle", "square"];
        const styleIndex = depth % styles.length;
        return BULLET_MARKERS[styles[styleIndex]];
      
      case "checkbox":
        return ""; // Checkbox rendered separately
      
      default:
        return BULLET_MARKERS.disc;
    }
  }

  render(ctx: CanvasRenderingContext2D, _viewport: unknown): void {
    const bounds = this.getBounds();
    const { x, y, w, h } = bounds;

    // Background
    if (this.backgroundColor !== "transparent") {
      ctx.fillStyle = this.backgroundColor;
      if (this.borderRadius > 0) {
        this.roundRect(ctx, x, y, w, h, this.borderRadius);
        ctx.fill();
      } else {
        ctx.fillRect(x, y, w, h);
      }
    }

    // Setup text rendering
    ctx.font = `${this.fontSize}px ${this.fontFamily}`;
    ctx.textBaseline = "top";

    const lineH = this.fontSize * this.lineHeight;
    const flatItems = this.flattenItems(this.items);
    
    let currentY = y + this.padding;

    for (const { item, depth, visualIndex } of flatItems) {
      const indent = this.padding + depth * this.indentSize;
      const isChecked = this.listType === "checkbox" && item.checked;
      
      // Apply checked styling
      if (isChecked) {
        ctx.globalAlpha = this.checkedOpacity;
      }

      // Render checkbox
      if (this.listType === "checkbox") {
        const checkboxSize = this.fontSize;
        const checkboxX = x + indent;
        const checkboxY = currentY + (lineH - checkboxSize) / 2;

        // Checkbox box
        ctx.strokeStyle = this.markerColor;
        ctx.lineWidth = 1.5;
        this.roundRect(ctx, checkboxX, checkboxY, checkboxSize, checkboxSize, 3);
        ctx.stroke();

        // Checkmark
        if (isChecked) {
          ctx.fillStyle = themeManager.getTheme().colors.primary;
          this.roundRect(ctx, checkboxX, checkboxY, checkboxSize, checkboxSize, 3);
          ctx.fill();

          // Draw checkmark
          ctx.strokeStyle = "#ffffff";
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(checkboxX + checkboxSize * 0.2, checkboxY + checkboxSize * 0.5);
          ctx.lineTo(checkboxX + checkboxSize * 0.4, checkboxY + checkboxSize * 0.7);
          ctx.lineTo(checkboxX + checkboxSize * 0.8, checkboxY + checkboxSize * 0.3);
          ctx.stroke();
        }

        // Text after checkbox
        const textX = checkboxX + checkboxSize + 8;
        ctx.fillStyle = this.textColor;
        ctx.fillText(item.content, textX, currentY);

        // Strikethrough
        if (isChecked && this.strikethroughChecked) {
          const textWidth = ctx.measureText(item.content).width;
          ctx.strokeStyle = this.textColor;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(textX, currentY + lineH / 2);
          ctx.lineTo(textX + textWidth, currentY + lineH / 2);
          ctx.stroke();
        }
      } else {
        // Render marker
        const marker = this.getMarker(visualIndex, depth);
        const markerWidth = ctx.measureText(marker).width;
        
        ctx.fillStyle = this.markerColor;
        ctx.fillText(marker, x + indent, currentY);

        // Render text
        const textX = x + indent + markerWidth + 8;
        ctx.fillStyle = this.textColor;
        ctx.fillText(item.content, textX, currentY);
      }

      ctx.globalAlpha = 1;
      currentY += lineH + this.itemSpacing;
    }
  }

  /**
   * Helper to draw rounded rectangles
   */
  private roundRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number
  ): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  includesPoint(px: number, py: number): boolean {
    const bounds = this.getBounds();
    return (
      px >= bounds.x &&
      px <= bounds.x + bounds.w &&
      py >= bounds.y &&
      py <= bounds.y + bounds.h
    );
  }

  /**
   * Add a new item
   */
  addItem(content: string, options?: Partial<ListItem>): ListItem {
    const item: ListItem = {
      id: `item-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
      content,
      ...options,
    };
    this.items.push(item);
    return item;
  }

  /**
   * Remove an item by ID
   */
  removeItem(id: string): boolean {
    const index = this.items.findIndex(item => item.id === id);
    if (index !== -1) {
      this.items.splice(index, 1);
      return true;
    }
    return false;
  }

  /**
   * Toggle checkbox state
   */
  toggleItem(id: string): boolean {
    const item = this.findItem(id);
    if (item && this.listType === "checkbox") {
      item.checked = !item.checked;
      return true;
    }
    return false;
  }

  /**
   * Find an item by ID (recursive)
   */
  private findItem(id: string, items: ListItem[] = this.items): ListItem | null {
    for (const item of items) {
      if (item.id === id) return item;
      if (item.children) {
        const found = this.findItem(id, item.children);
        if (found) return found;
      }
    }
    return null;
  }

  /**
   * Indent an item
   */
  indentItem(id: string): void {
    const item = this.findItem(id);
    if (item) {
      item.indent = Math.min((item.indent || 0) + 1, 5);
    }
  }

  /**
   * Outdent an item
   */
  outdentItem(id: string): void {
    const item = this.findItem(id);
    if (item) {
      item.indent = Math.max((item.indent || 0) - 1, 0);
    }
  }

  /**
   * Convert to Markdown
   */
  toMarkdown(): string {
    const lines: string[] = [];
    const flatItems = this.flattenItems(this.items);

    for (const { item, depth, visualIndex } of flatItems) {
      const indent = "  ".repeat(depth);
      
      switch (this.listType) {
        case "ordered":
          lines.push(`${indent}${visualIndex}. ${item.content}`);
          break;
        case "unordered":
          lines.push(`${indent}- ${item.content}`);
          break;
        case "checkbox":
          const checkbox = item.checked ? "[x]" : "[ ]";
          lines.push(`${indent}- ${checkbox} ${item.content}`);
          break;
      }
    }

    return lines.join("\n");
  }

  /**
   * Parse from Markdown
   */
  static fromMarkdown(markdown: string, baseProps: BaseElementProps): ListElement {
    const lines = markdown.split("\n").filter(line => line.trim());
    const items: ListItem[] = [];
    
    // Detect list type
    let listType: ListType = "unordered";
    const firstLine = lines[0]?.trim() || "";
    
    if (/^\d+\./.test(firstLine)) {
      listType = "ordered";
    } else if (/^\s*-\s*\[[ x]\]/.test(firstLine)) {
      listType = "checkbox";
    }

    for (const line of lines) {
      // Calculate indent
      const leadingSpaces = line.match(/^(\s*)/)?.[1].length || 0;
      const indent = Math.floor(leadingSpaces / 2);

      // Parse content
      let content = line.trim();
      let checked = false;

      // Remove list markers
      content = content.replace(/^[-*+]\s*/, "");
      content = content.replace(/^\d+\.\s*/, "");

      // Check for checkbox
      const checkboxMatch = content.match(/^\[([ x])\]\s*/);
      if (checkboxMatch) {
        checked = checkboxMatch[1] === "x";
        content = content.replace(/^\[([ x])\]\s*/, "");
      }

      items.push({
        id: `item-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
        content,
        checked: listType === "checkbox" ? checked : undefined,
        indent,
      });
    }

    return new ListElement({
      ...baseProps,
      listType,
      items,
    });
  }

  /**
   * Get all items as plain text
   */
  toPlainText(): string {
    return this.items.map(item => item.content).join("\n");
  }

  /**
   * Calculate required height for content
   */
  calculateContentHeight(): number {
    const lineH = this.fontSize * this.lineHeight;
    const totalItems = this.flattenItems(this.items).length;
    return this.padding * 2 + totalItems * (lineH + this.itemSpacing) - this.itemSpacing;
  }
}
