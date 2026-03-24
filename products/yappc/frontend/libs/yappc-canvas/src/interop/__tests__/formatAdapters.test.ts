/**
 * Format Adapters Tests (Feature 2.13)
 *
 * Test coverage for Mermaid export, PlantUML import, and C4 DSL round-trip
 */

import { describe, it, expect } from 'vitest';

import {
  exportToMermaid,
  importFromPlantUML,
  exportToC4,
  importFromC4,
  validateC4RoundTrip,
  getFormatCapabilities,
  type CanvasDocument,
  type CanvasNode,
  type CanvasEdge,
} from '../formatAdapters';

describe('formatAdapters', () => {
  describe('Mermaid Export', () => {
    it('should export simple flowchart', () => {
      const document: CanvasDocument = {
        nodes: [
          {
            id: 'node1',
            type: 'rectangle',
            label: 'Start',
            position: { x: 0, y: 0 },
          },
          {
            id: 'node2',
            type: 'diamond',
            label: 'Decision?',
            position: { x: 100, y: 100 },
          },
        ],
        edges: [
          {
            id: 'edge1',
            source: 'node1',
            target: 'node2',
            label: 'Go',
          },
        ],
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('flowchart TB');
      expect(result.data).toContain('node1["Start"]');
      expect(result.data).toContain('node2{"Decision?"}');
      expect(result.data).toContain('node1 --Go--> node2');
      expect(result.coverage).toBeGreaterThanOrEqual(90);
    });

    it('should support different node types', () => {
      const document: CanvasDocument = {
        nodes: [
          { id: 'rect', type: 'rectangle', label: 'Rectangle', position: { x: 0, y: 0 } },
          { id: 'ellipse', type: 'ellipse', label: 'Ellipse', position: { x: 0, y: 0 } },
          { id: 'diamond', type: 'diamond', label: 'Diamond', position: { x: 0, y: 0 } },
          { id: 'cylinder', type: 'cylinder', label: 'Database', position: { x: 0, y: 0 } },
          { id: 'hexagon', type: 'hexagon', label: 'Hexagon', position: { x: 0, y: 0 } },
        ],
        edges: [],
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('rect["Rectangle"]');
      expect(result.data).toContain('ellipse(["Ellipse"])');
      expect(result.data).toContain('diamond{"Diamond"}');
      expect(result.data).toContain('cylinder[("Database")]');
      expect(result.data).toContain('hexagon{{"Hexagon"}}');
    });

    it('should handle note types with warning', () => {
      const document: CanvasDocument = {
        nodes: [
          { id: 'note1', type: 'note', label: 'Note', position: { x: 0, y: 0 } },
        ],
        edges: [],
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('note1["Note"]');
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain('note');
    });

    it('should support dashed edges', () => {
      const document: CanvasDocument = {
        nodes: [
          { id: 'a', type: 'rectangle', label: 'A', position: { x: 0, y: 0 } },
          { id: 'b', type: 'rectangle', label: 'B', position: { x: 0, y: 0 } },
        ],
        edges: [
          {
            id: 'edge1',
            source: 'a',
            target: 'b',
            type: 'dashed',
            label: 'Optional',
          },
        ],
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('-.Optional.->');
    });

    it('should sanitize IDs', () => {
      const document: CanvasDocument = {
        nodes: [
          { id: 'node-with-dashes', type: 'rectangle', label: 'Test', position: { x: 0, y: 0 } },
        ],
        edges: [],
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('node_with_dashes');
    });

    it('should escape special characters in labels', () => {
      const document: CanvasDocument = {
        nodes: [
          { id: 'node1', type: 'rectangle', label: 'Test "quotes"', position: { x: 0, y: 0 } },
        ],
        edges: [],
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('#quot;');
    });

    it('should include styling when enabled', () => {
      const document: CanvasDocument = {
        nodes: [
          {
            id: 'styled',
            type: 'rectangle',
            label: 'Styled',
            position: { x: 0, y: 0 },
            style: {
              fill: '#ff0000',
              stroke: '#000000',
              strokeWidth: 2,
            },
          },
        ],
        edges: [],
      };

      const result = exportToMermaid(document, { includeStyles: true });

      expect(result.data).toContain('style styled');
      expect(result.data).toContain('fill:#ff0000');
      expect(result.data).toContain('stroke:#000000');
      expect(result.data).toContain('stroke-width:2px');
    });

    it('should support different diagram types', () => {
      const document: CanvasDocument = {
        nodes: [{ id: 'node1', type: 'rectangle', label: 'State', position: { x: 0, y: 0 } }],
        edges: [],
      };

      const result = exportToMermaid(document, { diagramType: 'stateDiagram' });

      expect(result.data).toContain('stateDiagram-v2');
    });

    it('should support different directions', () => {
      const document: CanvasDocument = {
        nodes: [{ id: 'node1', type: 'rectangle', label: 'Node', position: { x: 0, y: 0 } }],
        edges: [],
      };

      const result = exportToMermaid(document, { direction: 'LR' });

      expect(result.data).toContain('flowchart LR');
    });

    it('should handle empty document', () => {
      const document: CanvasDocument = {
        nodes: [],
        edges: [],
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('flowchart TB');
      expect(result.coverage).toBe(100);
    });

    it('should include metadata as comments', () => {
      const document: CanvasDocument = {
        nodes: [],
        edges: [],
        metadata: {
          title: 'Test Diagram',
        },
      };

      const result = exportToMermaid(document);

      expect(result.data).toContain('%% Test Diagram');
    });

    it('should achieve ≥90% coverage for typical diagrams', () => {
      const document: CanvasDocument = {
        nodes: [
          { id: 'n1', type: 'rectangle', label: 'Node 1', position: { x: 0, y: 0 } },
          { id: 'n2', type: 'ellipse', label: 'Node 2', position: { x: 0, y: 0 } },
          { id: 'n3', type: 'diamond', label: 'Node 3', position: { x: 0, y: 0 } },
          { id: 'n4', type: 'cylinder', label: 'Node 4', position: { x: 0, y: 0 } },
        ],
        edges: [
          { id: 'e1', source: 'n1', target: 'n2', type: 'solid' },
          { id: 'e2', source: 'n2', target: 'n3', type: 'dashed' },
          { id: 'e3', source: 'n3', target: 'n4', type: 'solid', label: 'Yes' },
        ],
      };

      const result = exportToMermaid(document);

      expect(result.coverage).toBeGreaterThanOrEqual(90);
    });
  });

  describe('PlantUML Import', () => {
    it('should import simple diagram', () => {
      const plantuml = `
@startuml
node1 [Node 1]
node2 [Node 2]
node1 --> node2
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      expect(result.data.nodes.length).toBe(2);
      expect(result.data.edges.length).toBe(1);
      expect(result.data.nodes[0].id).toBe('node1');
      expect(result.data.nodes[0].label).toBe('Node 1');
      expect(result.data.edges[0].source).toBe('node1');
      expect(result.data.edges[0].target).toBe('node2');
    });

    it('should detect database nodes', () => {
      const plantuml = `
@startuml
database db1
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      expect(result.data.nodes[0].type).toBe('cylinder');
    });

    it('should detect note nodes', () => {
      const plantuml = `
@startuml
note left: This is a note
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      expect(result.warnings.length).toBeGreaterThan(0);
    });

    it('should parse dashed relationships', () => {
      const plantuml = `
@startuml
a [A]
b [B]
a ..> b
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      const edge = result.data.edges[0];
      expect(edge.type).toBe('dashed');
    });

    it('should parse edge labels', () => {
      const plantuml = `
@startuml
a [A]
b [B]
a --> "uses" b
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      const edge = result.data.edges[0];
      expect(edge.label).toBeDefined();
    });

    it('should auto-layout nodes vertically by default', () => {
      const plantuml = `
@startuml
a [A]
b [B]
c [C]
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      expect(result.data.nodes[0].position.y).toBe(0);
      expect(result.data.nodes[1].position.y).toBe(150);
      expect(result.data.nodes[2].position.y).toBe(300);
    });

    it('should support horizontal layout', () => {
      const plantuml = `
@startuml
a [A]
b [B]
@enduml
      `;

      const result = importFromPlantUML(plantuml, { layoutDirection: 'LR' });

      expect(result.data.nodes[0].position.x).toBe(0);
      expect(result.data.nodes[1].position.x).toBe(150);
    });

    it('should preserve node fidelity ≥95%', () => {
      const plantuml = `
@startuml
node1 [Node 1]
node2 [Node 2]
database db1
node1 --> node2
node2 --> db1
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      expect(result.coverage).toBeGreaterThanOrEqual(95);
      expect(result.data.nodes.length).toBe(3);
      expect(result.data.edges.length).toBe(2);
    });

    it('should skip comments', () => {
      const plantuml = `
@startuml
' This is a comment
node1 [Node 1]
' Another comment
node2 [Node 2]
@enduml
      `;

      const result = importFromPlantUML(plantuml);

      expect(result.data.nodes.length).toBe(2);
    });
  });

  describe('C4 DSL Export', () => {
    it('should export to C4 format', () => {
      const document: CanvasDocument = {
        nodes: [
          {
            id: 'person1',
            type: 'ellipse',
            label: 'User',
            position: { x: 0, y: 0 },
            data: {
              c4Type: 'Person',
              description: 'End user',
            },
          },
          {
            id: 'system1',
            type: 'rectangle',
            label: 'System',
            position: { x: 0, y: 0 },
            data: {
              c4Type: 'System',
              technology: 'Node.js',
            },
          },
        ],
        edges: [
          {
            id: 'rel1',
            source: 'person1',
            target: 'system1',
            label: 'Uses',
          },
        ],
        metadata: {
          title: 'Test System',
        },
      };

      const result = exportToC4(document);

      expect(result.data).toContain('workspace {');
      expect(result.data).toContain('name "Test System"');
      expect(result.data).toContain('person1 = person "User"');
      expect(result.data).toContain('system1 = system "System"');
      expect(result.data).toContain('person1 -> system1 "Uses"');
      expect(result.coverage).toBeGreaterThanOrEqual(90);
    });

    it('should include descriptions', () => {
      const document: CanvasDocument = {
        nodes: [
          {
            id: 'sys1',
            type: 'rectangle',
            label: 'System',
            position: { x: 0, y: 0 },
            data: {
              c4Type: 'System',
              description: 'Main system',
            },
          },
        ],
        edges: [],
      };

      const result = exportToC4(document);

      expect(result.data).toContain('"Main system"');
    });

    it('should include technology stack', () => {
      const document: CanvasDocument = {
        nodes: [
          {
            id: 'container1',
            type: 'rectangle',
            label: 'API',
            position: { x: 0, y: 0 },
            data: {
              c4Type: 'Container',
              technology: 'Spring Boot',
            },
          },
        ],
        edges: [],
      };

      const result = exportToC4(document);

      expect(result.data).toContain('"Spring Boot"');
    });

    it('should sanitize IDs for C4', () => {
      const document: CanvasDocument = {
        nodes: [
          {
            id: 'node-with-dashes',
            type: 'rectangle',
            label: 'Node',
            position: { x: 0, y: 0 },
          },
        ],
        edges: [],
      };

      const result = exportToC4(document);

      expect(result.data).toContain('node_with_dashes');
    });
  });

  describe('C4 DSL Import', () => {
    it('should import C4 format', () => {
      const c4 = `
workspace {
  model {
    user = person "User"
    system = system "System"
    user -> system "Uses"
  }
}
      `;

      const result = importFromC4(c4);

      expect(result.data.nodes.length).toBe(2);
      expect(result.data.edges.length).toBe(1);
      expect(result.data.nodes[0].id).toBe('user');
      expect(result.data.nodes[0].label).toBe('User');
    });

    it('should parse element types', () => {
      const c4 = `
workspace {
  model {
    person1 = person "User"
    system1 = system "System"
    container1 = container "API"
    component1 = component "Controller"
  }
}
      `;

      const result = importFromC4(c4);

      expect(result.data.nodes.length).toBe(4);
      expect(result.data.nodes[0].data?.c4Type).toBe('person');
      expect(result.data.nodes[1].data?.c4Type).toBe('system');
      expect(result.data.nodes[2].data?.c4Type).toBe('container');
      expect(result.data.nodes[3].data?.c4Type).toBe('component');
    });

    it('should parse descriptions', () => {
      const c4 = `
workspace {
  model {
    sys = system "System" "Main application"
  }
}
      `;

      const result = importFromC4(c4);

      expect(result.data.nodes[0].data?.description).toBe('Main application');
    });

    it('should parse technology', () => {
      const c4 = `
workspace {
  model {
    api = container "API" "REST API" "Node.js"
  }
}
      `;

      const result = importFromC4(c4);

      expect(result.data.nodes[0].data?.technology).toBe('Node.js');
    });

    it('should skip comments', () => {
      const c4 = `
workspace {
  // This is a comment
  model {
    user = person "User"
  }
}
      `;

      const result = importFromC4(c4);

      expect(result.data.nodes.length).toBe(1);
    });
  });

  describe('C4 DSL Round-Trip', () => {
    it('should validate zero-diff round-trip', () => {
      const original: CanvasDocument = {
        nodes: [
          {
            id: 'person1',
            type: 'ellipse',
            label: 'User',
            position: { x: 0, y: 0 },
            data: {
              c4Type: 'Person',
            },
          },
          {
            id: 'system1',
            type: 'rectangle',
            label: 'System',
            position: { x: 0, y: 0 },
            data: {
              c4Type: 'System',
            },
          },
        ],
        edges: [
          {
            id: 'rel1',
            source: 'person1',
            target: 'system1',
            label: 'Uses',
          },
        ],
      };

      const exported = exportToC4(original).data;
      const reimported = importFromC4(exported).data;
      const validation = validateC4RoundTrip(original, exported, reimported);

      expect(validation.isValid).toBe(true);
      expect(validation.differences.length).toBe(0);
      expect(validation.fidelityScore).toBe(100);
    });

    it('should detect node count differences', () => {
      const original: CanvasDocument = {
        nodes: [
          { id: 'n1', type: 'rectangle', label: 'Node 1', position: { x: 0, y: 0 } },
          { id: 'n2', type: 'rectangle', label: 'Node 2', position: { x: 0, y: 0 } },
        ],
        edges: [],
      };

      const reimported: CanvasDocument = {
        nodes: [{ id: 'n1', type: 'rectangle', label: 'Node 1', position: { x: 0, y: 0 } }],
        edges: [],
      };

      const validation = validateC4RoundTrip(original, '', reimported);

      expect(validation.isValid).toBe(false);
      expect(validation.differences).toContain('Node count mismatch: 2 vs 1');
    });

    it('should detect missing labels', () => {
      const original: CanvasDocument = {
        nodes: [{ id: 'n1', type: 'rectangle', label: 'Lost Label', position: { x: 0, y: 0 } }],
        edges: [],
      };

      const reimported: CanvasDocument = {
        nodes: [{ id: 'n1', type: 'rectangle', label: 'Different Label', position: { x: 0, y: 0 } }],
        edges: [],
      };

      const validation = validateC4RoundTrip(original, '', reimported);

      expect(validation.differences).toContain('Missing node label: "Lost Label"');
    });

    it('should detect missing edges', () => {
      const original: CanvasDocument = {
        nodes: [
          { id: 'n1', type: 'rectangle', label: 'N1', position: { x: 0, y: 0 } },
          { id: 'n2', type: 'rectangle', label: 'N2', position: { x: 0, y: 0 } },
        ],
        edges: [{ id: 'e1', source: 'n1', target: 'n2' }],
      };

      const reimported: CanvasDocument = {
        nodes: [
          { id: 'n1', type: 'rectangle', label: 'N1', position: { x: 0, y: 0 } },
          { id: 'n2', type: 'rectangle', label: 'N2', position: { x: 0, y: 0 } },
        ],
        edges: [],
      };

      const validation = validateC4RoundTrip(original, '', reimported);

      expect(validation.differences).toContain('Missing edge: n1-n2');
    });

    it('should calculate fidelity score', () => {
      const original: CanvasDocument = {
        nodes: [
          { id: 'n1', type: 'rectangle', label: 'N1', position: { x: 0, y: 0 } },
          { id: 'n2', type: 'rectangle', label: 'N2', position: { x: 0, y: 0 } },
        ],
        edges: [{ id: 'e1', source: 'n1', target: 'n2' }],
      };

      const reimported: CanvasDocument = {
        nodes: [
          { id: 'n1', type: 'rectangle', label: 'N1', position: { x: 0, y: 0 } },
          { id: 'n2', type: 'rectangle', label: 'N2', position: { x: 0, y: 0 } },
        ],
        edges: [],
      };

      const validation = validateC4RoundTrip(original, '', reimported);

      // Missing 1 edge and 1 edge string out of 6 checks = 66.67%
      expect(validation.fidelityScore).toBeCloseTo(66.67, 1);
      expect(validation.fidelityScore).toBeLessThan(100);
    });
  });

  describe('Format Capabilities', () => {
    it('should return Mermaid capabilities', () => {
      const caps = getFormatCapabilities('mermaid');

      expect(caps.export).toBe(true);
      expect(caps.import).toBe(false);
      expect(caps.roundTrip).toBe(false);
      expect(caps.expectedCoverage).toBeGreaterThanOrEqual(90);
      expect(caps.supportedNodeTypes).toContain('rectangle');
      expect(caps.supportedNodeTypes).toContain('diamond');
    });

    it('should return PlantUML capabilities', () => {
      const caps = getFormatCapabilities('plantuml');

      expect(caps.export).toBe(false);
      expect(caps.import).toBe(true);
      expect(caps.roundTrip).toBe(false);
      expect(caps.expectedCoverage).toBeGreaterThanOrEqual(90);
      expect(caps.supportedNodeTypes).toContain('cylinder');
    });

    it('should return C4 capabilities', () => {
      const caps = getFormatCapabilities('c4');

      expect(caps.export).toBe(true);
      expect(caps.import).toBe(true);
      expect(caps.roundTrip).toBe(true);
      expect(caps.expectedCoverage).toBeGreaterThanOrEqual(90);
    });
  });
});
