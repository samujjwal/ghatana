#!/usr/bin/env node

/**
 * Canvas Seed Script - Generate demo canvas data
 */

import type { CanvasState, CanvasElement, CanvasConnection } from '../apps/web/src/components/canvas/workspace/canvasAtoms';

/**
 *
 */
interface SeedOptions {
  nodeCount?: number;
  connectionDensity?: number; // 0-1, how connected the nodes are
  includeShapes?: boolean;
  includeStrokes?: boolean;
  canvasSize?: { width: number; height: number };
}

/**
 *
 */
export function generateSeedData(options: SeedOptions = {}): CanvasState {
  const {
    nodeCount = 20,
    connectionDensity = 0.3,
    includeShapes = true,
    includeStrokes = true,
    canvasSize = { width: 1200, height: 800 },
  } = options;

  const elements: CanvasElement[] = [];
  const connections: CanvasConnection[] = [];

  // Generate nodes
  const nodeTypes = ['api', 'data', 'component', 'flow'];
  const nodeLabels = [
    'User Service', 'Payment Gateway', 'Database', 'Cache Layer',
    'Load Balancer', 'API Gateway', 'Auth Service', 'Notification Service',
    'Analytics Engine', 'Search Index', 'File Storage', 'CDN',
    'Message Queue', 'Worker Pool', 'Monitoring', 'Logging Service',
    'Config Service', 'Backup System', 'Security Scanner', 'Health Check',
  ];

  for (let i = 0; i < nodeCount; i++) {
    const nodeType = nodeTypes[Math.floor(Math.random() * nodeTypes.length)];
    const label = nodeLabels[i % nodeLabels.length];
    
    const element: CanvasElement = {
      id: `node-${i + 1}`,
      kind: 'node',
      type: nodeType,
      position: {
        x: Math.random() * (canvasSize.width - 200) + 100,
        y: Math.random() * (canvasSize.height - 200) + 100,
      },
      size: {
        width: 150 + Math.random() * 50,
        height: 80 + Math.random() * 20,
      },
      data: {
        label,
        description: `${label} component`,
        version: `v${Math.floor(Math.random() * 3) + 1}.${Math.floor(Math.random() * 10)}`,
      },
      style: {
        backgroundColor: getNodeColor(nodeType),
        borderColor: '#cccccc',
        color: '#333333',
      },
    };

    elements.push(element);
  }

  // Generate connections
  const nodeIds = elements.map(el => el.id);
  const targetConnections = Math.floor(nodeCount * connectionDensity);

  for (let i = 0; i < targetConnections; i++) {
    const sourceId = nodeIds[Math.floor(Math.random() * nodeIds.length)];
    const targetId = nodeIds[Math.floor(Math.random() * nodeIds.length)];
    
    // Avoid self-connections and duplicates
    if (sourceId === targetId) continue;
    if (connections.some(c => c.source === sourceId && c.target === targetId)) continue;

    const connection: CanvasConnection = {
      id: `connection-${i + 1}`,
      source: sourceId,
      target: targetId,
      type: 'default',
      data: {
        label: getConnectionLabel(),
      },
      style: {
        stroke: '#666666',
        strokeWidth: 2,
      },
    };

    connections.push(connection);
  }

  // Generate shapes if requested
  if (includeShapes) {
    const shapeTypes = ['rectangle', 'ellipse'];
    const shapeCount = Math.floor(nodeCount * 0.3);

    for (let i = 0; i < shapeCount; i++) {
      const shapeType = shapeTypes[Math.floor(Math.random() * shapeTypes.length)];
      
      const element: CanvasElement = {
        id: `shape-${i + 1}`,
        kind: 'shape',
        type: 'shape',
        position: {
          x: Math.random() * (canvasSize.width - 150) + 50,
          y: Math.random() * (canvasSize.height - 150) + 50,
        },
        size: {
          width: 100 + Math.random() * 100,
          height: 60 + Math.random() * 60,
        },
        data: {
          shapeType,
        },
        style: {
          fill: getShapeColor(),
          stroke: '#999999',
          strokeWidth: 2,
        },
      };

      elements.push(element);
    }
  }

  // Generate strokes if requested
  if (includeStrokes) {
    const strokeCount = Math.floor(nodeCount * 0.2);

    for (let i = 0; i < strokeCount; i++) {
      const startX = Math.random() * canvasSize.width;
      const startY = Math.random() * canvasSize.height;
      const points = [{ x: startX, y: startY }];

      // Generate smooth curve
      const pointCount = 5 + Math.floor(Math.random() * 10);
      for (let j = 1; j < pointCount; j++) {
        const prevPoint = points[j - 1];
        points.push({
          x: prevPoint.x + (Math.random() - 0.5) * 100,
          y: prevPoint.y + (Math.random() - 0.5) * 100,
        });
      }

      const element: CanvasElement = {
        id: `stroke-${i + 1}`,
        kind: 'stroke',
        type: 'stroke',
        position: { x: 0, y: 0 },
        data: {
          points,
          tool: 'pen',
        },
        style: {
          stroke: getStrokeColor(),
          strokeWidth: 2 + Math.random() * 3,
        },
      };

      elements.push(element);
    }
  }

  return {
    elements,
    connections,
    viewport: {
      x: 0,
      y: 0,
      zoom: 1,
    },
    metadata: {
      title: 'Demo Canvas',
      description: 'Generated demo canvas with sample architecture',
      createdAt: new Date().toISOString(),
      version: '1.0.0',
    },
  };
}

/**
 *
 */
function getNodeColor(type: string): string {
  const colors = {
    api: '#e3f2fd',
    data: '#f3e5f5',
    component: '#e8f5e8',
    flow: '#fff3e0',
  };
  return colors[type as keyof typeof colors] || '#f5f5f5';
}

/**
 *
 */
function getShapeColor(): string {
  const colors = [
    'rgba(255, 235, 59, 0.3)',
    'rgba(255, 193, 7, 0.3)',
    'rgba(255, 152, 0, 0.3)',
    'rgba(255, 87, 34, 0.3)',
    'rgba(244, 67, 54, 0.3)',
  ];
  return colors[Math.floor(Math.random() * colors.length)];
}

/**
 *
 */
function getStrokeColor(): string {
  const colors = [
    '#2196f3',
    '#4caf50',
    '#ff9800',
    '#f44336',
    '#9c27b0',
    '#607d8b',
  ];
  return colors[Math.floor(Math.random() * colors.length)];
}

/**
 *
 */
function getConnectionLabel(): string {
  const labels = [
    'HTTP',
    'gRPC',
    'WebSocket',
    'Database',
    'Cache',
    'Queue',
    'Event',
    'API Call',
  ];
  return labels[Math.floor(Math.random() * labels.length)];
}

// Predefined seed scenarios
export const seedScenarios = {
  small: (): CanvasState => generateSeedData({
    nodeCount: 5,
    connectionDensity: 0.4,
    includeShapes: false,
    includeStrokes: false,
  }),

  medium: (): CanvasState => generateSeedData({
    nodeCount: 15,
    connectionDensity: 0.3,
    includeShapes: true,
    includeStrokes: true,
  }),

  large: (): CanvasState => generateSeedData({
    nodeCount: 50,
    connectionDensity: 0.2,
    includeShapes: true,
    includeStrokes: true,
  }),

  performance: (): CanvasState => generateSeedData({
    nodeCount: 100,
    connectionDensity: 0.15,
    includeShapes: true,
    includeStrokes: true,
    canvasSize: { width: 2000, height: 1500 },
  }),

  microservices: (): CanvasState => {
    const data = generateSeedData({
      nodeCount: 25,
      connectionDensity: 0.25,
      includeShapes: false,
      includeStrokes: false,
    });

    // Override with microservices-specific data
    const services = [
      'API Gateway', 'User Service', 'Auth Service', 'Payment Service',
      'Order Service', 'Inventory Service', 'Notification Service',
      'Analytics Service', 'Search Service', 'Recommendation Engine',
      'File Storage', 'CDN', 'Load Balancer', 'Database Cluster',
      'Redis Cache', 'Message Queue', 'Event Bus', 'Monitoring',
      'Logging', 'Config Service', 'Service Mesh', 'Circuit Breaker',
      'Rate Limiter', 'Health Check', 'Backup Service',
    ];

    data.elements.forEach((element, index) => {
      if (element.kind === 'node' && services[index]) {
        element.data = {
          ...element.data,
          label: services[index],
          description: `${services[index]} microservice`,
        };
      }
    });

    data.metadata = {
      ...data.metadata,
      title: 'Microservices Architecture',
      description: 'Sample microservices architecture diagram',
    };

    return data;
  },
};

// CLI interface
if (require.main === module) {
  const scenario = process.argv[2] || 'medium';
  
  if (!(scenario in seedScenarios)) {
    console.error(`Unknown scenario: ${scenario}`);
    console.error(`Available scenarios: ${Object.keys(seedScenarios).join(', ')}`);
    process.exit(1);
  }

  const data = seedScenarios[scenario as keyof typeof seedScenarios]();
  console.log(JSON.stringify(data, null, 2));
}

export default generateSeedData;
