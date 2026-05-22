/**
 * Interaction Graph View - displays product interaction relationships.
 *
 * @doc.type component
 * @doc.purpose Visualize product interaction graph and evidence
 * @doc.layer studio
 */

import React from "react";

interface InteractionNode {
  productId: string;
  type: "provider" | "consumer";
  interactions: readonly string[];
}

interface InteractionEdge {
  from: string;
  to: string;
  contractId: string;
  status: "active" | "denied" | "error";
  lastInteraction?: string;
}

interface InteractionGraphViewProps {
  nodes: readonly InteractionNode[];
  edges: readonly InteractionEdge[];
  onNodeClick?: (productId: string) => void;
  onEdgeClick?: (contractId: string) => void;
}

export function InteractionGraphView({ nodes, edges, onNodeClick, onEdgeClick }: InteractionGraphViewProps) {
  return (
    <div className="interaction-graph-view">
      <div className="graph-header">
        <h2>Product Interaction Graph</h2>
        <div className="graph-legend">
          <div className="legend-item provider">
            <span className="legend-icon" />
            <span>Provider</span>
          </div>
          <div className="legend-item consumer">
            <span className="legend-icon" />
            <span>Consumer</span>
          </div>
          <div className="legend-item active">
            <span className="legend-icon" />
            <span>Active</span>
          </div>
          <div className="legend-item denied">
            <span className="legend-icon" />
            <span>Denied</span>
          </div>
          <div className="legend-item error">
            <span className="legend-icon" />
            <span>Error</span>
          </div>
        </div>
      </div>

      <div className="graph-container">
        <svg className="interaction-graph" viewBox="0 0 800 600">
          {edges.map((edge, index) => (
            <g key={`edge-${index}`} onClick={() => onEdgeClick?.(edge.contractId)} className="graph-edge">
              <line
                x1={getNodePosition(edge.from).x}
                y1={getNodePosition(edge.from).y}
                x2={getNodePosition(edge.to).x}
                y2={getNodePosition(edge.to).y}
                className={`edge-line ${edge.status}`}
              />
              <text
                x={(getNodePosition(edge.from).x + getNodePosition(edge.to).x) / 2}
                y={(getNodePosition(edge.from).y + getNodePosition(edge.to).y) / 2}
                className="edge-label"
              >
                {edge.contractId}
              </text>
            </g>
          ))}
          {nodes.map((node) => (
            <g
              key={node.productId}
              onClick={() => onNodeClick?.(node.productId)}
              className={`graph-node ${node.type}`}
            >
              <circle
                cx={getNodePosition(node.productId).x}
                cy={getNodePosition(node.productId).y}
                r={30}
                className="node-circle"
              />
              <text
                x={getNodePosition(node.productId).x}
                y={getNodePosition(node.productId).y + 45}
                className="node-label"
              >
                {node.productId}
              </text>
            </g>
          ))}
        </svg>
      </div>

      <div className="interaction-details">
        <h3>Interaction Details</h3>
        {edges.map((edge, index) => (
          <div key={`detail-${index}`} className="interaction-detail">
            <div className="detail-header">
              <span className="contract-id">{edge.contractId}</span>
              <span className={`status ${edge.status}`}>{edge.status}</span>
            </div>
            <div className="detail-paths">
              <span className="from">{edge.from}</span>
              <span className="arrow">→</span>
              <span className="to">{edge.to}</span>
            </div>
            {edge.lastInteraction && (
              <div className="detail-meta">
                <span className="last-interaction">Last: {new Date(edge.lastInteraction).toLocaleString()}</span>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// Simple layout algorithm - in production would use a proper graph layout library
function getNodePosition(productId: string): { x: number; y: number } {
  const hash = productId.split("").reduce((acc, char) => acc + char.charCodeAt(0), 0);
  const angle = (hash * 137.5) % 360;
  const radius = 150 + (hash % 100);
  const x = 400 + radius * Math.cos((angle * Math.PI) / 180);
  const y = 300 + radius * Math.sin((angle * Math.PI) / 180);
  return { x, y };
}
