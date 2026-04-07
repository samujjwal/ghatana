/**
 * Advanced Diagram Templates Tests
 * @doc.type test
 * @doc.purpose Verify diagram template structures, registry functions, and instantiation helpers
 * @doc.layer unit
 */

import { describe, it, expect } from 'vitest';
import {
  bpmnProcessTemplate,
  bpmnCollaborationTemplate,
  umlClassDiagramTemplate,
  umlSequenceTemplate,
  erDiagramTemplate,
  networkTopologyTemplate,
  microservicesTemplate,
  serverlessTemplate,
  diagramTemplates,
  getTemplatesByCategory,
  getTemplateById,
  createFromTemplate,
} from './AdvancedDiagrams';
import type { DiagramTemplate } from './AdvancedDiagrams';

// ─── Template structure invariants ───────────────────────────────────────────

describe('DiagramTemplate structure', () => {
  const allTemplates: DiagramTemplate[] = [
    bpmnProcessTemplate,
    bpmnCollaborationTemplate,
    umlClassDiagramTemplate,
    umlSequenceTemplate,
    erDiagramTemplate,
    networkTopologyTemplate,
    microservicesTemplate,
    serverlessTemplate,
  ];

  it.each(allTemplates.map((t) => [t.id, t] as [string, DiagramTemplate]))(
    '%s has a non-empty id, name, description and a valid category',
    (_id, template) => {
      expect(template.id).toBeTruthy();
      expect(template.name).toBeTruthy();
      expect(template.description).toBeTruthy();
      expect([
        'flowchart',
        'uml',
        'bpmn',
        'er',
        'network',
        'architecture',
      ]).toContain(template.category);
    }
  );

  it.each(allTemplates.map((t) => [t.id, t] as [string, DiagramTemplate]))(
    '%s has at least one element and one edge',
    (_id, template) => {
      expect(template.elements.length).toBeGreaterThan(0);
      expect(template.edges.length).toBeGreaterThan(0);
    }
  );

  it.each(allTemplates.map((t) => [t.id, t] as [string, DiagramTemplate]))(
    '%s elements all reference unique IDs',
    (_id, template) => {
      const ids = template.elements.map((e) => e.id);
      const unique = new Set(ids);
      expect(unique.size).toBe(ids.length);
    }
  );

  it.each(allTemplates.map((t) => [t.id, t] as [string, DiagramTemplate]))(
    '%s edge source and target IDs exist in elements (or qualify as cross-pool refs)',
    (_id, template) => {
      const elementIds = new Set(template.elements.map((e) => e.id));
      for (const edge of template.edges) {
        expect(elementIds.has(edge.source)).toBe(true);
        expect(elementIds.has(edge.target)).toBe(true);
      }
    }
  );
});

// ─── BPMN templates ───────────────────────────────────────────────────────────

describe('bpmnProcessTemplate', () => {
  it('has category "bpmn"', () => {
    expect(bpmnProcessTemplate.category).toBe('bpmn');
  });

  it('contains a start and end node', () => {
    expect(bpmnProcessTemplate.elements.some((e) => e.id === 'start')).toBe(
      true
    );
    expect(bpmnProcessTemplate.elements.some((e) => e.id === 'end')).toBe(true);
  });

  it('metadata declares bpmnVersion 2.0', () => {
    expect(bpmnProcessTemplate.metadata?.['bpmnVersion']).toBe('2.0');
  });
});

describe('bpmnCollaborationTemplate', () => {
  it('has pools and lanes in metadata', () => {
    expect(bpmnCollaborationTemplate.metadata?.['hasPools']).toBe(true);
    expect(bpmnCollaborationTemplate.metadata?.['hasLanes']).toBe(true);
    expect(bpmnCollaborationTemplate.metadata?.['hasMessageFlows']).toBe(true);
  });

  it('contains a message-flow edge between pools', () => {
    const msgFlow = bpmnCollaborationTemplate.edges.find(
      (e) => e.id === 'msg-flow'
    );
    expect(msgFlow).toBeDefined();
  });
});

// ─── UML templates ────────────────────────────────────────────────────────────

describe('umlClassDiagramTemplate', () => {
  it('has category "uml"', () => {
    expect(umlClassDiagramTemplate.category).toBe('uml');
  });

  it('includes an inheritance edge', () => {
    const inheritance = umlClassDiagramTemplate.edges.find(
      (e) => e.id === 'inheritance'
    );
    expect(inheritance).toBeDefined();
  });
});

describe('umlSequenceTemplate', () => {
  it('has at least one actor element', () => {
    expect(umlSequenceTemplate.elements.some((e) => e.id === 'actor')).toBe(
      true
    );
  });

  it('metadata declares supportsFragments', () => {
    expect(umlSequenceTemplate.metadata?.['supportsFragments']).toBe(true);
  });
});

// ─── ER template ─────────────────────────────────────────────────────────────

describe('erDiagramTemplate', () => {
  it('has category "er"', () => {
    expect(erDiagramTemplate.category).toBe('er');
  });

  it('metadata includes crow-foot notation', () => {
    expect(erDiagramTemplate.metadata?.['notation']).toBe('crow-foot');
  });
});

// ─── Network template ─────────────────────────────────────────────────────────

describe('networkTopologyTemplate', () => {
  it('has category "network"', () => {
    expect(networkTopologyTemplate.category).toBe('network');
  });

  it('contains a firewall element', () => {
    expect(
      networkTopologyTemplate.elements.some((e) => e.id === 'firewall')
    ).toBe(true);
  });
});

// ─── Architecture templates ───────────────────────────────────────────────────

describe('microservicesTemplate', () => {
  it('has category "architecture"', () => {
    expect(microservicesTemplate.category).toBe('architecture');
  });

  it('metadata declares architectureStyle microservices', () => {
    expect(microservicesTemplate.metadata?.['architectureStyle']).toBe(
      'microservices'
    );
  });
});

describe('serverlessTemplate', () => {
  it('metadata declares provider as aws', () => {
    expect(serverlessTemplate.metadata?.['provider']).toBe('aws');
  });

  it('metadata declares event trigger support', () => {
    expect(serverlessTemplate.metadata?.['supportsEventTriggers']).toBe(true);
  });
});

// ─── Template registry ────────────────────────────────────────────────────────

describe('diagramTemplates registry', () => {
  it('contains all 8 built-in templates', () => {
    expect(diagramTemplates).toHaveLength(8);
  });

  it('all registered templates have unique IDs', () => {
    const ids = diagramTemplates.map((t) => t.id);
    const unique = new Set(ids);
    expect(unique.size).toBe(ids.length);
  });
});

// ─── getTemplatesByCategory ───────────────────────────────────────────────────

describe('getTemplatesByCategory', () => {
  it('returns only bpmn templates when queried for bpmn', () => {
    const results = getTemplatesByCategory('bpmn');
    expect(results.length).toBeGreaterThan(0);
    results.forEach((t) => expect(t.category).toBe('bpmn'));
  });

  it('returns only uml templates when queried for uml', () => {
    const results = getTemplatesByCategory('uml');
    expect(results.length).toBeGreaterThan(0);
    results.forEach((t) => expect(t.category).toBe('uml'));
  });

  it('returns only er templates when queried for er', () => {
    const results = getTemplatesByCategory('er');
    expect(results.length).toBeGreaterThan(0);
    results.forEach((t) => expect(t.category).toBe('er'));
  });

  it('returns empty array for a category with no templates (flowchart)', () => {
    const results = getTemplatesByCategory('flowchart');
    expect(results).toEqual([]);
  });

  it('returns architecture templates correctly', () => {
    const results = getTemplatesByCategory('architecture');
    expect(results.length).toBeGreaterThanOrEqual(2);
  });
});

// ─── getTemplateById ──────────────────────────────────────────────────────────

describe('getTemplateById', () => {
  it('returns the correct template for a known ID', () => {
    const template = getTemplateById('bpmn-process');
    expect(template).toBeDefined();
    expect(template?.id).toBe('bpmn-process');
  });

  it('returns undefined for an unknown ID', () => {
    expect(getTemplateById('does-not-exist')).toBeUndefined();
  });

  it('returns correct template for er-diagram', () => {
    const t = getTemplateById('er-diagram');
    expect(t?.category).toBe('er');
  });
});

// ─── createFromTemplate ───────────────────────────────────────────────────────

describe('createFromTemplate', () => {
  it('returns a deep clone without sharing element references', () => {
    const original = getTemplateById('bpmn-process')!;
    const clone = createFromTemplate('bpmn-process')!;

    expect(clone).not.toBe(original);
    expect(clone.elements).not.toBe(original.elements);
    expect(clone.edges).not.toBe(original.edges);
    expect(clone.elements[0]).not.toBe(original.elements[0]);
  });

  it('clone has identical structure to the original template', () => {
    const clone = createFromTemplate('microservices')!;
    const original = getTemplateById('microservices')!;

    expect(clone.elements.length).toBe(original.elements.length);
    expect(clone.edges.length).toBe(original.edges.length);
    expect(clone.id).toBe(original.id);
    expect(clone.category).toBe(original.category);
  });

  it('returns null for an unknown template ID', () => {
    expect(createFromTemplate('ghost-template')).toBeNull();
  });

  it('mutating the clone does not affect the original', () => {
    const clone = createFromTemplate('uml-class')!;
    const original = getTemplateById('uml-class')!;

    clone.elements[0].x = 9999;

    expect(original.elements[0].x).not.toBe(9999);
  });

  it('creates instances for all registered template IDs', () => {
    for (const template of diagramTemplates) {
      const instance = createFromTemplate(template.id);
      expect(instance).not.toBeNull();
      expect(instance?.id).toBe(template.id);
    }
  });
});
