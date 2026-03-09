import { BaseElementProps, CanvasElementType } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager, YAPPCTheme } from "../theme/index.js";

export interface DiagramProps extends BaseElementProps {
  diagramType: "flowchart" | "mindmap" | "sequence" | "class";
  nodes: DiagramNode[];
  connections: DiagramConnection[];
  layout: "hierarchical" | "organic" | "circular";
}

export interface DiagramNode {
  id: string;
  type: "process" | "decision" | "data" | "start" | "end" | "connector";
  text: string;
  position: { x: number; y: number };
  size: { width: number; height: number };
  style?: {
    fillColor?: string;
    strokeColor?: string;
    textColor?: string;
  };
}

export interface DiagramConnection {
  id: string;
  from: string;
  to: string;
  type: "arrow" | "line" | "dashed";
  label?: string;
  style?: {
    strokeColor?: string;
    strokeWidth?: number;
  };
}

export class DiagramElement extends CanvasElement {
  public diagramType: "flowchart" | "mindmap" | "sequence" | "class";
  public nodes: DiagramNode[];
  public connections: DiagramConnection[];
  public layout: "hierarchical" | "organic" | "circular";

  constructor(props: DiagramProps) {
    super(props);
    this.diagramType = props.diagramType;
    this.nodes = props.nodes;
    this.connections = props.connections;
    this.layout = props.layout;
  }

  get type(): CanvasElementType {
    return "diagram";
  }

  render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
    ctx.save();
    this.applyTransform(ctx);

    const theme = themeManager.getTheme();

    // Semantic zoom: hide connections if too small or simplify
    const showDetails = zoom > 0.3;

    if (showDetails) {
      // Draw connections first (so they appear behind nodes)
      for (const connection of this.connections) {
        this.drawConnection(ctx, connection, theme);
      }

      // Draw nodes
      for (const node of this.nodes) {
        this.drawNode(ctx, node, theme);
      }
    } else {
      // Draw placeholder for diagram
      ctx.fillStyle = theme.colors.surface;
      const bound = this.getBounds();
      ctx.fillRect(bound.x, bound.y, bound.w, bound.h);
      ctx.strokeStyle = theme.colors.border.medium;
      ctx.lineWidth = 1 / zoom;
      ctx.strokeRect(bound.x, bound.y, bound.w, bound.h);
    }

    ctx.restore();
  }

  includesPoint(x: number, y: number): boolean {
    const bound = this.getBounds();
    return bound.containsPoint({ x, y });
  }

  private drawNode(
    ctx: CanvasRenderingContext2D,
    node: DiagramNode,
    theme: YAPPCTheme,
  ): void {
    const themeColors = node.style || {};

    // Set styles
    ctx.fillStyle = themeColors.fillColor || theme.colors.surface;
    ctx.strokeStyle = themeColors.strokeColor || theme.colors.border.medium;
    ctx.lineWidth = 2;
    ctx.font = `${theme.typography.fontSize.medium}px ${theme.typography.fontFamily}`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    switch (node.type) {
      case "process":
        this.drawProcessNode(ctx, node);
        break;
      case "decision":
        this.drawDecisionNode(ctx, node);
        break;
      case "data":
        this.drawDataNode(ctx, node);
        break;
      case "start":
      case "end":
        this.drawTerminalNode(ctx, node, node.type === "start");
        break;
      case "connector":
        this.drawConnectorNode(ctx, node);
        break;
    }

    // Draw text
    ctx.fillStyle = themeColors.textColor || theme.colors.text.primary;
    ctx.fillText(
      node.text,
      node.position.x + node.size.width / 2,
      node.position.y + node.size.height / 2,
    );
  }

  private drawProcessNode(
    ctx: CanvasRenderingContext2D,
    node: DiagramNode,
  ): void {
    const x = node.position.x;
    const y = node.position.y;
    const w = node.size.width;
    const h = node.size.height;
    const radius = 8;

    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.lineTo(x + w - radius, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + radius);
    ctx.lineTo(x + w, y + h - radius);
    ctx.quadraticCurveTo(x + w, y + h, x + w - radius, y + h);
    ctx.lineTo(x + radius, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - radius);
    ctx.lineTo(x, y + radius);
    ctx.quadraticCurveTo(x, y, x + radius, y);
    ctx.closePath();

    ctx.fill();
    ctx.stroke();
  }

  private drawDecisionNode(
    ctx: CanvasRenderingContext2D,
    node: DiagramNode,
  ): void {
    const x = node.position.x;
    const y = node.position.y;
    const w = node.size.width;
    const h = node.size.height;

    ctx.beginPath();
    ctx.moveTo(x + w / 2, y);
    ctx.lineTo(x + w, y + h / 2);
    ctx.lineTo(x + w / 2, y + h);
    ctx.lineTo(x, y + h / 2);
    ctx.closePath();

    ctx.fill();
    ctx.stroke();
  }

  private drawDataNode(ctx: CanvasRenderingContext2D, node: DiagramNode): void {
    const x = node.position.x;
    const y = node.position.y;
    const w = node.size.width;
    const h = node.size.height;
    const angle = (45 * Math.PI) / 180;
    const offset = (w - h) / 2;

    ctx.save();
    ctx.translate(x + w / 2, y + h / 2);
    ctx.rotate(angle);
    ctx.translate(-w / 2, -h / 2);

    ctx.fillRect(-offset, 0, w, h);
    ctx.strokeRect(-offset, 0, w, h);

    ctx.restore();
  }

  private drawTerminalNode(
    ctx: CanvasRenderingContext2D,
    node: DiagramNode,
    isStart: boolean,
  ): void {
    const x = node.position.x;
    const y = node.position.y;
    const w = node.size.width;
    const h = node.size.height;

    ctx.beginPath();
    ctx.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, 2 * Math.PI);

    if (isStart) {
      ctx.fillStyle = "#10b981";
    } else {
      ctx.fillStyle = "#ef4444";
    }

    ctx.fill();
    ctx.stroke();
  }

  private drawConnectorNode(
    ctx: CanvasRenderingContext2D,
    node: DiagramNode,
  ): void {
    const x = node.position.x;
    const y = node.position.y;
    const w = node.size.width;
    const h = node.size.height;

    ctx.beginPath();
    ctx.ellipse(x + w / 2, y + h / 2, w / 2, h / 3, 0, 0, 2 * Math.PI);

    ctx.fill();
    ctx.stroke();
  }

  private drawConnection(
    ctx: CanvasRenderingContext2D,
    connection: DiagramConnection,
    theme: YAPPCTheme,
  ): void {
    const fromNode = this.nodes.find((n) => n.id === connection.from);
    const toNode = this.nodes.find((n) => n.id === connection.to);

    if (!fromNode || !toNode) return;

    const themeColors = connection.style || {};
    ctx.strokeStyle = themeColors.strokeColor || theme.colors.border.dark;
    ctx.lineWidth = themeColors.strokeWidth || 2;

    if (connection.type === "dashed") {
      ctx.setLineDash([5, 5]);
    } else {
      ctx.setLineDash([]);
    }

    const fromPoint = {
      x: fromNode.position.x + fromNode.size.width / 2,
      y: fromNode.position.y + fromNode.size.height,
    };

    const toPoint = {
      x: toNode.position.x + toNode.size.width / 2,
      y: toNode.position.y,
    };

    ctx.beginPath();
    ctx.moveTo(fromPoint.x, fromPoint.y);

    // Add curve for better visual flow
    const midY = (fromPoint.y + toPoint.y) / 2;
    ctx.quadraticCurveTo(fromPoint.x, midY, toPoint.x, toPoint.y);

    ctx.stroke();

    // Draw arrow
    if (connection.type === "arrow") {
      this.drawArrow(ctx, toPoint, fromPoint);
    }

    // Draw label
    if (connection.label) {
      ctx.save();
      ctx.fillStyle = theme.colors.text.secondary;
      ctx.font = `${theme.typography.fontSize.small}px ${theme.typography.fontFamily}`;
      ctx.textAlign = "center";
      ctx.fillText(connection.label, (fromPoint.x + toPoint.x) / 2, midY);
      ctx.restore();
    }
  }

  private drawArrow(
    ctx: CanvasRenderingContext2D,
    to: { x: number; y: number },
    from: { x: number; y: number },
  ): void {
    const angle = Math.atan2(to.y - from.y, to.x - from.x);
    const arrowLength = 10;
    const arrowAngle = Math.PI / 6;

    ctx.save();
    ctx.translate(to.x, to.y);
    ctx.rotate(angle);

    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(
      -arrowLength * Math.cos(arrowAngle),
      -arrowLength * Math.sin(arrowAngle),
    );
    ctx.moveTo(0, 0);
    ctx.lineTo(
      -arrowLength * Math.cos(arrowAngle),
      arrowLength * Math.sin(arrowAngle),
    );
    ctx.stroke();

    ctx.restore();
  }

  addNode(node: DiagramNode): void {
    this.nodes.push(node);
    this.updateBounds();
  }

  removeNode(nodeId: string): void {
    this.nodes = this.nodes.filter((n) => n.id !== nodeId);
    this.connections = this.connections.filter(
      (c) => c.from !== nodeId && c.to !== nodeId,
    );
    this.updateBounds();
  }

  addConnection(connection: DiagramConnection): void {
    this.connections.push(connection);
  }

  removeConnection(connectionId: string): void {
    this.connections = this.connections.filter((c) => c.id !== connectionId);
  }

  private updateBounds(): void {
    if (this.nodes.length === 0) return;

    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;

    for (const node of this.nodes) {
      minX = Math.min(minX, node.position.x);
      minY = Math.min(minY, node.position.y);
      maxX = Math.max(maxX, node.position.x + node.size.width);
      maxY = Math.max(maxY, node.position.y + node.size.height);
    }

    const padding = 20;
    this.xywh = Bound.fromXYWH(
      minX - padding,
      minY - padding,
      maxX - minX + padding * 2,
      maxY - minY + padding * 2,
    ).serialize();
  }
}
