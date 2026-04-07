/**
 * @file Advanced Diagram Templates
 * BPMN 2.0, ER diagrams, and other advanced diagram types
 * 
 * @doc.type utility
 * @doc.purpose Provide advanced diagram templates for YAPPC
 * @doc.layer presentation
 * @doc.pattern TemplateLibrary
 */

import { type LayoutElement, type LayoutEdge } from '../layout/AutoLayoutEngine';

// ============================================================================
// Types
// ============================================================================

export interface DiagramTemplate {
  id: string;
  name: string;
  description: string;
  category: 'flowchart' | 'uml' | 'bpmn' | 'er' | 'network' | 'architecture';
  elements: LayoutElement[];
  edges: LayoutEdge[];
  metadata?: Record<string, any>;
}

// ============================================================================
// BPMN 2.0 Templates
// ============================================================================

/**
 * BPMN Process Template
 * Standard business process modeling elements
 */
export const bpmnProcessTemplate: DiagramTemplate = {
  id: 'bpmn-process',
  name: 'BPMN Process',
  description: 'Standard BPMN 2.0 business process with start, tasks, gateways, and end events',
  category: 'bpmn',
  elements: [
    { id: 'start', x: 50, y: 200, width: 40, height: 40, type: 'node' },
    { id: 'task1', x: 150, y: 180, width: 120, height: 80, type: 'node' },
    { id: 'gateway1', x: 320, y: 200, width: 50, height: 50, type: 'node' },
    { id: 'task2a', x: 450, y: 120, width: 120, height: 80, type: 'node' },
    { id: 'task2b', x: 450, y: 280, width: 120, height: 80, type: 'node' },
    { id: 'gateway2', x: 620, y: 200, width: 50, height: 50, type: 'node' },
    { id: 'task3', x: 750, y: 180, width: 120, height: 80, type: 'node' },
    { id: 'end', x: 900, y: 200, width: 40, height: 40, type: 'node' },
  ],
  edges: [
    { id: 'e1', source: 'start', target: 'task1', type: 'straight' },
    { id: 'e2', source: 'task1', target: 'gateway1', type: 'straight' },
    { id: 'e3a', source: 'gateway1', target: 'task2a', type: 'straight' },
    { id: 'e3b', source: 'gateway1', target: 'task2b', type: 'straight' },
    { id: 'e4a', source: 'task2a', target: 'gateway2', type: 'straight' },
    { id: 'e4b', source: 'task2b', target: 'gateway2', type: 'straight' },
    { id: 'e5', source: 'gateway2', target: 'task3', type: 'straight' },
    { id: 'e6', source: 'task3', target: 'end', type: 'straight' },
  ],
  metadata: {
    bpmnVersion: '2.0',
    supportsSimulation: true,
    elementTypes: ['startEvent', 'task', 'exclusiveGateway', 'endEvent'],
  },
};

/**
 * BPMN Collaboration Template
 * Pool and lane structure for multi-participant processes
 */
export const bpmnCollaborationTemplate: DiagramTemplate = {
  id: 'bpmn-collaboration',
  name: 'BPMN Collaboration',
  description: 'Multi-pool collaboration diagram with message flows',
  category: 'bpmn',
  elements: [
    // Pool 1
    { id: 'pool1', x: 0, y: 0, width: 400, height: 300, type: 'group' },
    { id: 'lane1a', x: 0, y: 0, width: 400, height: 150, type: 'group', parentId: 'pool1' },
    { id: 'lane1b', x: 0, y: 150, width: 400, height: 150, type: 'group', parentId: 'pool1' },
    { id: 'p1-start', x: 50, y: 55, width: 40, height: 40, type: 'node', parentId: 'lane1a' },
    { id: 'p1-task', x: 150, y: 35, width: 120, height: 80, type: 'node', parentId: 'lane1a' },
    { id: 'p1-end', x: 320, y: 55, width: 40, height: 40, type: 'node', parentId: 'lane1a' },
    
    // Pool 2
    { id: 'pool2', x: 500, y: 0, width: 400, height: 300, type: 'group' },
    { id: 'lane2', x: 500, y: 0, width: 400, height: 300, type: 'group', parentId: 'pool2' },
    { id: 'p2-start', x: 550, y: 130, width: 40, height: 40, type: 'node', parentId: 'lane2' },
    { id: 'p2-task', x: 650, y: 110, width: 120, height: 80, type: 'node', parentId: 'lane2' },
    { id: 'p2-end', x: 820, y: 130, width: 40, height: 40, type: 'node', parentId: 'lane2' },
  ],
  edges: [
    { id: 'p1-flow1', source: 'p1-start', target: 'p1-task', type: 'straight' },
    { id: 'p1-flow2', source: 'p1-task', target: 'p1-end', type: 'straight' },
    { id: 'p2-flow1', source: 'p2-start', target: 'p2-task', type: 'straight' },
    { id: 'p2-flow2', source: 'p2-task', target: 'p2-end', type: 'straight' },
    { id: 'msg-flow', source: 'p1-task', target: 'p2-task', type: 'straight' },
  ],
  metadata: {
    bpmnVersion: '2.0',
    hasPools: true,
    hasLanes: true,
    hasMessageFlows: true,
  },
};

// ============================================================================
// UML Diagram Templates
// ============================================================================

/**
 * UML Class Diagram Template
 * Standard class diagram structure
 */
export const umlClassDiagramTemplate: DiagramTemplate = {
  id: 'uml-class',
  name: 'UML Class Diagram',
  description: 'Standard UML class diagram with classes, attributes, and relationships',
  category: 'uml',
  elements: [
    { id: 'class1', x: 50, y: 50, width: 200, height: 150, type: 'node' },
    { id: 'class2', x: 400, y: 50, width: 200, height: 150, type: 'node' },
    { id: 'class3', x: 225, y: 300, width: 200, height: 150, type: 'node' },
    { id: 'interface1', x: 700, y: 50, width: 200, height: 100, type: 'node' },
  ],
  edges: [
    { id: 'inheritance', source: 'class2', target: 'class1', type: 'straight' },
    { id: 'association', source: 'class1', target: 'class3', type: 'straight' },
    { id: 'implementation', source: 'class2', target: 'interface1', type: 'straight' },
  ],
  metadata: {
    umlVersion: '2.5',
    supportsStereotypes: true,
    relationshipTypes: ['inheritance', 'association', 'aggregation', 'composition', 'dependency', 'implementation'],
  },
};

/**
 * UML Sequence Diagram Template
 * Lifelines and message flows
 */
export const umlSequenceTemplate: DiagramTemplate = {
  id: 'uml-sequence',
  name: 'UML Sequence Diagram',
  description: 'Sequence diagram with lifelines, activations, and messages',
  category: 'uml',
  elements: [
    { id: 'actor', x: 50, y: 20, width: 60, height: 80, type: 'node' },
    { id: 'obj1', x: 200, y: 20, width: 100, height: 60, type: 'node' },
    { id: 'obj2', x: 400, y: 20, width: 100, height: 60, type: 'node' },
    { id: 'obj3', x: 600, y: 20, width: 100, height: 60, type: 'node' },
  ],
  edges: [
    { id: 'msg1', source: 'actor', target: 'obj1', type: 'straight' },
    { id: 'msg2', source: 'obj1', target: 'obj2', type: 'straight' },
    { id: 'msg3', source: 'obj2', target: 'obj3', type: 'straight' },
    { id: 'return', source: 'obj3', target: 'obj1', type: 'straight' },
  ],
  metadata: {
    umlVersion: '2.5',
    supportsFragments: true,
    supportsLoops: true,
    supportsAlts: true,
  },
};

// ============================================================================
// ER Diagram Templates
// ============================================================================

/**
 * ER Diagram Template
 * Entity-Relationship diagram structure
 */
export const erDiagramTemplate: DiagramTemplate = {
  id: 'er-diagram',
  name: 'ER Diagram',
  description: 'Entity-Relationship diagram with entities, attributes, and relationships',
  category: 'er',
  elements: [
    { id: 'entity1', x: 50, y: 150, width: 150, height: 200, type: 'node' },
    { id: 'entity2', x: 350, y: 150, width: 150, height: 200, type: 'node' },
    { id: 'entity3', x: 650, y: 150, width: 150, height: 200, type: 'node' },
    { id: 'relationship1', x: 250, y: 200, width: 100, height: 80, type: 'node' },
    { id: 'relationship2', x: 550, y: 200, width: 100, height: 80, type: 'node' },
  ],
  edges: [
    { id: 'rel1-e1', source: 'entity1', target: 'relationship1', type: 'straight' },
    { id: 'rel1-e2', source: 'relationship1', target: 'entity2', type: 'straight' },
    { id: 'rel2-e2', source: 'entity2', target: 'relationship2', type: 'straight' },
    { id: 'rel2-e3', source: 'relationship2', target: 'entity3', type: 'straight' },
  ],
  metadata: {
    notation: 'crow-foot',
    supportsCardinality: true,
    supportsWeakEntities: true,
  },
};

// ============================================================================
// Network Diagram Templates
// ============================================================================

/**
 * Network Topology Template
 * Network architecture visualization
 */
export const networkTopologyTemplate: DiagramTemplate = {
  id: 'network-topology',
  name: 'Network Topology',
  description: 'Network architecture with routers, switches, and hosts',
  category: 'network',
  elements: [
    { id: 'internet', x: 400, y: 20, width: 100, height: 60, type: 'node' },
    { id: 'firewall', x: 400, y: 120, width: 80, height: 80, type: 'node' },
    { id: 'router', x: 400, y: 250, width: 60, height: 60, type: 'node' },
    { id: 'switch1', x: 200, y: 380, width: 60, height: 60, type: 'node' },
    { id: 'switch2', x: 600, y: 380, width: 60, height: 60, type: 'node' },
    { id: 'server1', x: 100, y: 500, width: 80, height: 80, type: 'node' },
    { id: 'server2', x: 250, y: 500, width: 80, height: 80, type: 'node' },
    { id: 'workstation1', x: 550, y: 500, width: 60, height: 60, type: 'node' },
    { id: 'workstation2', x: 650, y: 500, width: 60, height: 60, type: 'node' },
  ],
  edges: [
    { id: 'conn1', source: 'internet', target: 'firewall', type: 'straight' },
    { id: 'conn2', source: 'firewall', target: 'router', type: 'straight' },
    { id: 'conn3', source: 'router', target: 'switch1', type: 'straight' },
    { id: 'conn4', source: 'router', target: 'switch2', type: 'straight' },
    { id: 'conn5', source: 'switch1', target: 'server1', type: 'straight' },
    { id: 'conn6', source: 'switch1', target: 'server2', type: 'straight' },
    { id: 'conn7', source: 'switch2', target: 'workstation1', type: 'straight' },
    { id: 'conn8', source: 'switch2', target: 'workstation2', type: 'straight' },
  ],
  metadata: {
    supportsIpLabels: true,
    supportsVlans: true,
    supportsSecurityZones: true,
  },
};

// ============================================================================
// Architecture Templates
// ============================================================================

/**
 * Microservices Architecture Template
 * Modern distributed system architecture
 */
export const microservicesTemplate: DiagramTemplate = {
  id: 'microservices',
  name: 'Microservices Architecture',
  description: 'Microservices architecture with API gateway, services, and data stores',
  category: 'architecture',
  elements: [
    { id: 'gateway', x: 400, y: 20, width: 120, height: 60, type: 'node' },
    { id: 'service1', x: 100, y: 150, width: 120, height: 80, type: 'node' },
    { id: 'service2', x: 350, y: 150, width: 120, height: 80, type: 'node' },
    { id: 'service3', x: 600, y: 150, width: 120, height: 80, type: 'node' },
    { id: 'db1', x: 100, y: 300, width: 100, height: 80, type: 'node' },
    { id: 'db2', x: 350, y: 300, width: 100, height: 80, type: 'node' },
    { id: 'db3', x: 600, y: 300, width: 100, height: 80, type: 'node' },
    { id: 'message-queue', x: 400, y: 280, width: 80, height: 80, type: 'node' },
  ],
  edges: [
    { id: 'api1', source: 'gateway', target: 'service1', type: 'straight' },
    { id: 'api2', source: 'gateway', target: 'service2', type: 'straight' },
    { id: 'api3', source: 'gateway', target: 'service3', type: 'straight' },
    { id: 'db-conn1', source: 'service1', target: 'db1', type: 'straight' },
    { id: 'db-conn2', source: 'service2', target: 'db2', type: 'straight' },
    { id: 'db-conn3', source: 'service3', target: 'db3', type: 'straight' },
    { id: 'mq1', source: 'service1', target: 'message-queue', type: 'straight' },
    { id: 'mq2', source: 'service2', target: 'message-queue', type: 'straight' },
    { id: 'mq3', source: 'message-queue', target: 'service3', type: 'straight' },
  ],
  metadata: {
    architectureStyle: 'microservices',
    supportsDocker: true,
    supportsKubernetes: true,
  },
};

/**
 * Serverless Architecture Template
 * AWS Lambda style serverless architecture
 */
export const serverlessTemplate: DiagramTemplate = {
  id: 'serverless',
  name: 'Serverless Architecture',
  description: 'Serverless architecture with functions, triggers, and managed services',
  category: 'architecture',
  elements: [
    { id: 'api-gateway', x: 400, y: 20, width: 120, height: 60, type: 'node' },
    { id: 'auth', x: 600, y: 20, width: 100, height: 60, type: 'node' },
    { id: 'function1', x: 200, y: 150, width: 100, height: 80, type: 'node' },
    { id: 'function2', x: 400, y: 150, width: 100, height: 80, type: 'node' },
    { id: 'function3', x: 600, y: 150, width: 100, height: 80, type: 'node' },
    { id: 'database', x: 300, y: 300, width: 120, height: 80, type: 'node' },
    { id: 'storage', x: 500, y: 300, width: 120, height: 80, type: 'node' },
    { id: 'cdn', x: 100, y: 300, width: 100, height: 60, type: 'node' },
  ],
  edges: [
    { id: 'req1', source: 'api-gateway', target: 'function1', type: 'straight' },
    { id: 'req2', source: 'api-gateway', target: 'function2', type: 'straight' },
    { id: 'req3', source: 'api-gateway', target: 'function3', type: 'straight' },
    { id: 'auth-check', source: 'api-gateway', target: 'auth', type: 'straight' },
    { id: 'db-conn', source: 'function1', target: 'database', type: 'straight' },
    { id: 'db-conn2', source: 'function2', target: 'database', type: 'straight' },
    { id: 'storage-conn', source: 'function2', target: 'storage', type: 'straight' },
    { id: 'storage-conn2', source: 'function3', target: 'storage', type: 'straight' },
    { id: 'cdn-conn', source: 'cdn', target: 'storage', type: 'straight' },
  ],
  metadata: {
    architectureStyle: 'serverless',
    provider: 'aws',
    supportsEventTriggers: true,
  },
};

// ============================================================================
// Template Registry
// ============================================================================

/**
 * All available diagram templates
 */
export const diagramTemplates: DiagramTemplate[] = [
  bpmnProcessTemplate,
  bpmnCollaborationTemplate,
  umlClassDiagramTemplate,
  umlSequenceTemplate,
  erDiagramTemplate,
  networkTopologyTemplate,
  microservicesTemplate,
  serverlessTemplate,
];

/**
 * Get templates by category
 */
export function getTemplatesByCategory(category: DiagramTemplate['category']): DiagramTemplate[] {
  return diagramTemplates.filter(t => t.category === category);
}

/**
 * Get template by ID
 */
export function getTemplateById(id: string): DiagramTemplate | undefined {
  return diagramTemplates.find(t => t.id === id);
}

/**
 * Create diagram from template
 */
export function createFromTemplate(templateId: string): DiagramTemplate | null {
  const template = getTemplateById(templateId);
  if (!template) return null;
  
  // Deep clone to create instance
  return {
    ...template,
    elements: template.elements.map(e => ({ ...e })),
    edges: template.edges.map(e => ({ ...e })),
  };
}

export default diagramTemplates;
