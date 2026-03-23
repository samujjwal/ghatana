/**
 * Table Element - Editable table on the canvas
 * 
 * @doc.type class
 * @doc.purpose Canvas element for data tables with rows and columns
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements table functionality for:
 * - Rows and columns
 * - Cell merging
 * - Header row/column
 * - Sorting and filtering indicators
 * - Column/row resizing
 */

import { BaseElementProps, CanvasElementType, PointTestOptions } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export type CellAlignment = "left" | "center" | "right";
export type CellVerticalAlignment = "top" | "middle" | "bottom";

export interface TableCell {
  /** Cell content */
  content: string;
  /** Colspan */
  colspan?: number;
  /** Rowspan */
  rowspan?: number;
  /** Horizontal alignment */
  align?: CellAlignment;
  /** Vertical alignment */
  verticalAlign?: CellVerticalAlignment;
  /** Background color */
  backgroundColor?: string;
  /** Text color */
  textColor?: string;
  /** Bold text */
  bold?: boolean;
}

export interface TableColumn {
  /** Column ID */
  id: string;
  /** Column header */
  header: string;
  /** Column width */
  width: number;
  /** Minimum width */
  minWidth?: number;
  /** Alignment */
  align?: CellAlignment;
  /** Whether column is sortable */
  sortable?: boolean;
  /** Sort direction */
  sortDirection?: "asc" | "desc" | null;
  /** Whether column is resizable */
  resizable?: boolean;
}

export interface TableRow {
  /** Row ID */
  id: string;
  /** Row cells */
  cells: TableCell[];
  /** Row height */
  height?: number;
  /** Whether row is header */
  isHeader?: boolean;
}

export interface TableProps extends BaseElementProps {
  /** Column definitions */
  columns: TableColumn[];
  /** Row data */
  rows: TableRow[];
  /** Whether to show header row */
  showHeader?: boolean;
  /** Header background color */
  headerBackgroundColor?: string;
  /** Alternating row colors */
  alternatingRowColors?: boolean;
  /** Row background colors */
  rowBackgroundColors?: [string, string];
  /** Border color */
  borderColor?: string;
  /** Border width */
  borderWidth?: number;
  /** Cell padding */
  cellPadding?: number;
  /** Font size */
  fontSize?: number;
  /** Font family */
  fontFamily?: string;
  /** Whether to show grid lines */
  showGridLines?: boolean;
  /** Corner radius */
  borderRadius?: number;
  /** Selected cell (row, col) */
  selectedCell?: [number, number] | null;
  /** Hover cell (row, col) */
  hoverCell?: [number, number] | null;
  /** Whether table is editable */
  editable?: boolean;
}

export class TableElement extends CanvasElement {
  public columns: TableColumn[];
  public rows: TableRow[];
  public showHeader: boolean;
  public headerBackgroundColor: string;
  public alternatingRowColors: boolean;
  public rowBackgroundColors: [string, string];
  public borderColor: string;
  public borderWidth: number;
  public cellPadding: number;
  public fontSize: number;
  public fontFamily: string;
  public showGridLines: boolean;
  public borderRadius: number;
  public selectedCell: [number, number] | null;
  public hoverCell: [number, number] | null;
  public editable: boolean;

  private static readonly DEFAULT_ROW_HEIGHT = 40;
  private static readonly HEADER_ROW_HEIGHT = 44;
  private static readonly MIN_CELL_WIDTH = 50;

  constructor(props: TableProps) {
    super(props);
    const theme = themeManager.getTheme();

    this.columns = props.columns;
    this.rows = props.rows;
    this.showHeader = props.showHeader ?? true;
    this.headerBackgroundColor = props.headerBackgroundColor || "rgba(0, 0, 0, 0.04)";
    this.alternatingRowColors = props.alternatingRowColors ?? true;
    this.rowBackgroundColors = props.rowBackgroundColors || ["#ffffff", "#f9fafb"];
    this.borderColor = props.borderColor || theme.colors.border.light;
    this.borderWidth = props.borderWidth ?? 1;
    this.cellPadding = props.cellPadding ?? 12;
    this.fontSize = props.fontSize ?? 14;
    this.fontFamily = props.fontFamily ?? theme.typography.fontFamily;
    this.showGridLines = props.showGridLines ?? true;
    this.borderRadius = props.borderRadius ?? 8;
    this.selectedCell = props.selectedCell || null;
    this.hoverCell = props.hoverCell || null;
    this.editable = props.editable ?? true;
  }

  get type(): CanvasElementType {
    return "table" as CanvasElementType;
  }

  get tableWidth(): number {
    return this.columns.reduce((sum, col) => sum + col.width, 0);
  }

  get tableHeight(): number {
    const headerHeight = this.showHeader ? TableElement.HEADER_ROW_HEIGHT : 0;
    const rowsHeight = this.rows.reduce((sum, row) => 
      sum + (row.height || TableElement.DEFAULT_ROW_HEIGHT), 0
    );
    return headerHeight + rowsHeight;
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const bound = this.getBounds();
    const theme = themeManager.getTheme();

    // Clip to rounded corners
    if (this.borderRadius > 0) {
      ctx.beginPath();
      this.roundedRectPath(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
      ctx.clip();
    }

    // Draw background
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(bound.x, bound.y, bound.w, bound.h);

    // Calculate scale factor if table doesn't fit
    const scaleX = bound.w / this.tableWidth;
    const scaleY = bound.h / this.tableHeight;
    const scale = Math.min(scaleX, scaleY, 1);

    // Apply scale if needed
    if (scale < 1) {
      ctx.translate(bound.x, bound.y);
      ctx.scale(scale, scale);
      ctx.translate(-bound.x, -bound.y);
    }

    // Simplified rendering at low zoom
    if (zoom * scale < 0.3) {
      this.renderSimplified(ctx, bound, theme);
      ctx.restore();
      return;
    }

    // Draw header
    let currentY = bound.y;
    if (this.showHeader) {
      currentY = this.drawHeader(ctx, bound.x, currentY, zoom * scale, theme);
    }

    // Draw rows
    this.rows.forEach((row, rowIndex) => {
      currentY = this.drawRow(ctx, row, rowIndex, bound.x, currentY, zoom * scale, theme);
    });

    // Draw outer border
    if (this.borderWidth > 0) {
      ctx.strokeStyle = this.borderColor;
      ctx.lineWidth = this.borderWidth / (zoom * scale);
      this.roundedRectPath(ctx, bound.x, bound.y, bound.w, bound.h, this.borderRadius);
      ctx.stroke();
    }

    ctx.restore();
  }

  private renderSimplified(ctx: CanvasRenderingContext2D, bound: Bound, theme: YAPPCTheme): void {
    // Draw grid pattern
    const cellSize = Math.min(bound.w / 4, bound.h / 3);
    
    ctx.strokeStyle = this.borderColor;
    ctx.lineWidth = 1;

    // Horizontal lines
    for (let y = bound.y; y <= bound.y + bound.h; y += cellSize) {
      ctx.beginPath();
      ctx.moveTo(bound.x, y);
      ctx.lineTo(bound.x + bound.w, y);
      ctx.stroke();
    }

    // Vertical lines
    for (let x = bound.x; x <= bound.x + bound.w; x += cellSize) {
      ctx.beginPath();
      ctx.moveTo(x, bound.y);
      ctx.lineTo(x, bound.y + bound.h);
      ctx.stroke();
    }

    // Draw header row
    if (this.showHeader) {
      ctx.fillStyle = this.headerBackgroundColor;
      ctx.fillRect(bound.x, bound.y, bound.w, cellSize);
    }
  }

  private drawHeader(ctx: CanvasRenderingContext2D, startX: number, startY: number, zoom: number, theme: YAPPCTheme): number {
    const rowHeight = TableElement.HEADER_ROW_HEIGHT;

    // Draw header background
    ctx.fillStyle = this.headerBackgroundColor;
    let x = startX;
    for (const col of this.columns) {
      ctx.fillRect(x, startY, col.width, rowHeight);
      x += col.width;
    }

    // Draw header cells
    x = startX;
    for (let colIndex = 0; colIndex < this.columns.length; colIndex++) {
      const col = this.columns[colIndex];
      
      // Draw cell content
      this.drawHeaderCell(ctx, col, x, startY, rowHeight, zoom, theme);
      
      // Draw vertical grid line
      if (this.showGridLines && colIndex < this.columns.length - 1) {
        ctx.strokeStyle = this.borderColor;
        ctx.lineWidth = this.borderWidth / zoom;
        ctx.beginPath();
        ctx.moveTo(x + col.width, startY);
        ctx.lineTo(x + col.width, startY + rowHeight);
        ctx.stroke();
      }
      
      x += col.width;
    }

    // Draw bottom border
    if (this.showGridLines) {
      ctx.strokeStyle = this.borderColor;
      ctx.lineWidth = this.borderWidth / zoom;
      ctx.beginPath();
      ctx.moveTo(startX, startY + rowHeight);
      ctx.lineTo(x, startY + rowHeight);
      ctx.stroke();
    }

    return startY + rowHeight;
  }

  private drawHeaderCell(
    ctx: CanvasRenderingContext2D,
    col: TableColumn,
    x: number,
    y: number,
    height: number,
    zoom: number,
    theme: YAPPCTheme
  ): void {
    const padding = this.cellPadding;
    const textX = x + padding;
    const textY = y + height / 2;
    const maxTextWidth = col.width - padding * 2 - (col.sortable ? 20 : 0);

    // Draw text
    ctx.fillStyle = theme.colors.text.primary;
    ctx.font = `bold ${this.fontSize}px ${this.fontFamily}`;
    ctx.textAlign = col.align || "left";
    ctx.textBaseline = "middle";

    let displayTextX = textX;
    if (col.align === "center") {
      displayTextX = x + col.width / 2;
    } else if (col.align === "right") {
      displayTextX = x + col.width - padding - (col.sortable ? 20 : 0);
    }

    const displayText = this.truncateText(ctx, col.header, maxTextWidth);
    ctx.fillText(displayText, displayTextX, textY);

    // Draw sort indicator
    if (col.sortable && col.sortDirection) {
      const arrowX = x + col.width - padding - 8;
      const arrowY = textY;
      
      ctx.fillStyle = theme.colors.text.secondary;
      ctx.font = `${12}px Arial`;
      ctx.textAlign = "center";
      ctx.fillText(col.sortDirection === "asc" ? "▲" : "▼", arrowX, arrowY);
    }
  }

  private drawRow(
    ctx: CanvasRenderingContext2D,
    row: TableRow,
    rowIndex: number,
    startX: number,
    startY: number,
    zoom: number,
    theme: YAPPCTheme
  ): number {
    const rowHeight = row.height || TableElement.DEFAULT_ROW_HEIGHT;

    // Draw row background
    const bgColor = this.alternatingRowColors 
      ? this.rowBackgroundColors[rowIndex % 2]
      : this.rowBackgroundColors[0];
    
    ctx.fillStyle = bgColor;
    ctx.fillRect(startX, startY, this.tableWidth, rowHeight);

    // Draw cells
    let x = startX;
    for (let colIndex = 0; colIndex < this.columns.length; colIndex++) {
      const col = this.columns[colIndex];
      const cell = row.cells[colIndex] || { content: "" };

      // Check if this is selected or hovered
      const isSelected = this.selectedCell && 
        this.selectedCell[0] === rowIndex && 
        this.selectedCell[1] === colIndex;
      const isHovered = this.hoverCell && 
        this.hoverCell[0] === rowIndex && 
        this.hoverCell[1] === colIndex;

      // Draw cell background
      if (cell.backgroundColor || isSelected || isHovered) {
        ctx.fillStyle = isSelected 
          ? theme.colors.primary + "20"
          : isHovered 
            ? "rgba(0, 0, 0, 0.02)"
            : cell.backgroundColor!;
        ctx.fillRect(x, startY, col.width, rowHeight);
      }

      // Draw selected border
      if (isSelected) {
        ctx.strokeStyle = theme.colors.primary;
        ctx.lineWidth = 2 / zoom;
        ctx.strokeRect(x, startY, col.width, rowHeight);
      }

      // Draw cell content
      this.drawCell(ctx, cell, col, x, startY, rowHeight, zoom, theme);

      // Draw vertical grid line
      if (this.showGridLines && colIndex < this.columns.length - 1) {
        ctx.strokeStyle = this.borderColor;
        ctx.lineWidth = this.borderWidth / zoom;
        ctx.beginPath();
        ctx.moveTo(x + col.width, startY);
        ctx.lineTo(x + col.width, startY + rowHeight);
        ctx.stroke();
      }

      x += col.width;
    }

    // Draw bottom border
    if (this.showGridLines) {
      ctx.strokeStyle = this.borderColor;
      ctx.lineWidth = this.borderWidth / zoom;
      ctx.beginPath();
      ctx.moveTo(startX, startY + rowHeight);
      ctx.lineTo(x, startY + rowHeight);
      ctx.stroke();
    }

    return startY + rowHeight;
  }

  private drawCell(
    ctx: CanvasRenderingContext2D,
    cell: TableCell,
    col: TableColumn,
    x: number,
    y: number,
    height: number,
    zoom: number,
    theme: YAPPCTheme
  ): void {
    const padding = this.cellPadding;
    const align = cell.align || col.align || "left";
    const verticalAlign = cell.verticalAlign || "middle";

    // Calculate text position
    let textX = x + padding;
    if (align === "center") {
      textX = x + col.width / 2;
    } else if (align === "right") {
      textX = x + col.width - padding;
    }

    let textY = y + height / 2;
    if (verticalAlign === "top") {
      textY = y + padding + this.fontSize / 2;
    } else if (verticalAlign === "bottom") {
      textY = y + height - padding - this.fontSize / 2;
    }

    // Set text style
    const fontWeight = cell.bold ? "bold " : "";
    ctx.font = `${fontWeight}${this.fontSize}px ${this.fontFamily}`;
    ctx.fillStyle = cell.textColor || theme.colors.text.primary;
    ctx.textAlign = align;
    ctx.textBaseline = "middle";

    // Draw text
    const maxTextWidth = col.width - padding * 2;
    const displayText = this.truncateText(ctx, cell.content, maxTextWidth);
    ctx.fillText(displayText, textX, textY);
  }

  private roundedRectPath(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number
  ): void {
    r = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  private truncateText(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string {
    if (ctx.measureText(text).width <= maxWidth) {
      return text;
    }

    let truncated = text;
    while (ctx.measureText(truncated + "...").width > maxWidth && truncated.length > 0) {
      truncated = truncated.slice(0, -1);
    }
    return truncated + "...";
  }

  includesPoint(x: number, y: number, options?: PointTestOptions): boolean {
    const bound = this.getBounds();
    return x >= bound.x && x <= bound.x + bound.w &&
           y >= bound.y && y <= bound.y + bound.h;
  }

  // Public API

  /**
   * Get cell at position
   */
  getCellAt(x: number, y: number): { row: number; col: number } | null {
    const bound = this.getBounds();
    const relX = x - bound.x;
    const relY = y - bound.y;

    // Check if in header
    const headerOffset = this.showHeader ? TableElement.HEADER_ROW_HEIGHT : 0;
    const rowY = relY - headerOffset;

    if (rowY < 0) return null;

    // Find row
    let currentY = 0;
    let rowIndex = -1;
    for (let i = 0; i < this.rows.length; i++) {
      const rowHeight = this.rows[i].height || TableElement.DEFAULT_ROW_HEIGHT;
      if (rowY >= currentY && rowY < currentY + rowHeight) {
        rowIndex = i;
        break;
      }
      currentY += rowHeight;
    }

    if (rowIndex === -1) return null;

    // Find column
    let currentX = 0;
    let colIndex = -1;
    for (let i = 0; i < this.columns.length; i++) {
      if (relX >= currentX && relX < currentX + this.columns[i].width) {
        colIndex = i;
        break;
      }
      currentX += this.columns[i].width;
    }

    if (colIndex === -1) return null;

    return { row: rowIndex, col: colIndex };
  }

  /**
   * Set cell value
   */
  setCellValue(row: number, col: number, value: string): void {
    if (row >= 0 && row < this.rows.length && col >= 0 && col < this.columns.length) {
      if (!this.rows[row].cells[col]) {
        this.rows[row].cells[col] = { content: value };
      } else {
        this.rows[row].cells[col].content = value;
      }
    }
  }

  /**
   * Get cell value
   */
  getCellValue(row: number, col: number): string {
    if (row >= 0 && row < this.rows.length && col >= 0 && col < this.columns.length) {
      return this.rows[row].cells[col]?.content || "";
    }
    return "";
  }

  /**
   * Add row
   */
  addRow(cells?: TableCell[]): void {
    const newCells = cells || this.columns.map(() => ({ content: "" }));
    this.rows.push({
      id: `row-${Date.now()}`,
      cells: newCells,
    });
  }

  /**
   * Remove row
   */
  removeRow(rowIndex: number): void {
    if (rowIndex >= 0 && rowIndex < this.rows.length) {
      this.rows.splice(rowIndex, 1);
    }
  }

  /**
   * Add column
   */
  addColumn(column: TableColumn): void {
    this.columns.push(column);
    // Add empty cells to existing rows
    for (const row of this.rows) {
      row.cells.push({ content: "" });
    }
  }

  /**
   * Remove column
   */
  removeColumn(colIndex: number): void {
    if (colIndex >= 0 && colIndex < this.columns.length) {
      this.columns.splice(colIndex, 1);
      for (const row of this.rows) {
        row.cells.splice(colIndex, 1);
      }
    }
  }

  /**
   * Resize column
   */
  resizeColumn(colIndex: number, newWidth: number): void {
    if (colIndex >= 0 && colIndex < this.columns.length) {
      const col = this.columns[colIndex];
      const minWidth = col.minWidth || TableElement.MIN_CELL_WIDTH;
      this.columns[colIndex].width = Math.max(newWidth, minWidth);
    }
  }

  /**
   * Sort by column
   */
  sortByColumn(colIndex: number): void {
    const col = this.columns[colIndex];
    if (!col.sortable) return;

    // Toggle sort direction
    const newDirection = col.sortDirection === "asc" ? "desc" : "asc";
    
    // Reset other columns
    this.columns.forEach((c, i) => {
      if (i !== colIndex) c.sortDirection = null;
    });

    col.sortDirection = newDirection;

    // Sort rows
    this.rows.sort((a, b) => {
      const aVal = a.cells[colIndex]?.content || "";
      const bVal = b.cells[colIndex]?.content || "";

      const comparison = aVal.localeCompare(bVal, undefined, { numeric: true });
      return newDirection === "asc" ? comparison : -comparison;
    });
  }

  /**
   * Export to CSV
   */
  toCsv(): string {
    const lines: string[] = [];

    // Header
    if (this.showHeader) {
      lines.push(this.columns.map(col => this.escapeCsvValue(col.header)).join(","));
    }

    // Rows
    for (const row of this.rows) {
      const values = row.cells.map(cell => this.escapeCsvValue(cell?.content || ""));
      lines.push(values.join(","));
    }

    return lines.join("\n");
  }

  private escapeCsvValue(value: string): string {
    if (value.includes(",") || value.includes('"') || value.includes("\n")) {
      return `"${value.replace(/"/g, '""')}"`;
    }
    return value;
  }

  /**
   * Import from CSV
   */
  static fromCsv(csv: string, hasHeader: boolean = true): { columns: TableColumn[]; rows: TableRow[] } {
    const lines = csv.split("\n").filter(line => line.trim());
    if (lines.length === 0) {
      return { columns: [], rows: [] };
    }

    const parseLine = (line: string): string[] => {
      const values: string[] = [];
      let current = "";
      let inQuotes = false;

      for (let i = 0; i < line.length; i++) {
        const char = line[i];
        
        if (char === '"') {
          if (inQuotes && line[i + 1] === '"') {
            current += '"';
            i++;
          } else {
            inQuotes = !inQuotes;
          }
        } else if (char === "," && !inQuotes) {
          values.push(current);
          current = "";
        } else {
          current += char;
        }
      }
      values.push(current);

      return values;
    };

    let columns: TableColumn[];
    let dataStartIndex: number;

    if (hasHeader) {
      const headers = parseLine(lines[0]);
      columns = headers.map((header, i) => ({
        id: `col-${i}`,
        header,
        width: Math.max(100, header.length * 10),
        align: "left" as CellAlignment,
      }));
      dataStartIndex = 1;
    } else {
      const firstRow = parseLine(lines[0]);
      columns = firstRow.map((_, i) => ({
        id: `col-${i}`,
        header: `Column ${i + 1}`,
        width: 100,
        align: "left" as CellAlignment,
      }));
      dataStartIndex = 0;
    }

    const rows: TableRow[] = [];
    for (let i = dataStartIndex; i < lines.length; i++) {
      const values = parseLine(lines[i]);
      rows.push({
        id: `row-${i}`,
        cells: values.map(content => ({ content })),
      });
    }

    return { columns, rows };
  }
}
