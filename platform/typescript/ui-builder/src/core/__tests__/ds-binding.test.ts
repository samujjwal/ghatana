import { describe, it, expect } from 'vitest';
import type { ComponentContract } from '@ghatana/ds-schema';
import {
  validateDocumentAgainstDS,
  dsViolationsToValidationResult,
  checkStoryContractParity,
} from '../ds-binding.js';
import type { BuilderDocument, ComponentInstance, NodeId } from '../types.js';
import { createDocumentId, createNodeId } from '../types.js';

// ============================================================================
// Helpers
// ============================================================================

function makeContract(
  name: string,
  props: ComponentContract['props'] = [],
): ComponentContract {
  return {
    name,
    version: '1.0.0',
    metadata: {
      category: 'test',
      status: 'stable',
      platforms: ['web'],
    },
    props,
    slots: [],
    events: [],
  };
}

function makeDoc(nodes: ComponentInstance[]): BuilderDocument {
  return {
    id: createDocumentId(),
    version: '1',
    name: 'Test',
    designSystem: {
      id: 'ds-1',
      name: 'DS',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'theme-1',
    },
    rootNodes: nodes.map((n) => n.id),
    nodes: new Map(nodes.map((n) => [n.id, n])),
    metadata: { createdAt: '', updatedAt: '' },
  };
}

function makeNode(id: NodeId, contractName: string, props: Record<string, unknown> = {}): ComponentInstance {
  return {
    id,
    contractName,
    props,
    slots: {},
    bindings: [],
    metadata: {},
  };
}

// ============================================================================
// validateDocumentAgainstDS
// ============================================================================

describe('validateDocumentAgainstDS', () => {
  it('passes when all nodes match registered contracts with correct props', () => {
    const contract = makeContract('Button', [
      { name: 'label', type: 'string', required: true },
    ]);
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Button', { label: 'Click me' })]);

    const result = validateDocumentAgainstDS(doc, [contract]);
    expect(result.valid).toBe(true);
    expect(result.violations).toHaveLength(0);
  });

  it('reports contract-not-found for unknown component', () => {
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'UnknownWidget')]);

    const result = validateDocumentAgainstDS(doc, []);
    expect(result.valid).toBe(false);
    expect(result.violations[0]?.kind).toBe('contract-not-found');
    expect(result.violations[0]?.contractName).toBe('UnknownWidget');
  });

  it('reports required-prop-missing when required prop is absent', () => {
    const contract = makeContract('Input', [
      { name: 'value', type: 'string', required: true },
    ]);
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Input', {})]);

    const result = validateDocumentAgainstDS(doc, [contract]);
    expect(result.violations.some((v) => v.kind === 'required-prop-missing' && v.propName === 'value')).toBe(true);
  });

  it('reports prop-type-mismatch when prop has wrong type', () => {
    const contract = makeContract('Badge', [
      { name: 'count', type: 'number', required: false },
    ]);
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Badge', { count: 'not-a-number' })]);

    const result = validateDocumentAgainstDS(doc, [contract]);
    expect(result.violations.some((v) => v.kind === 'prop-type-mismatch' && v.propName === 'count')).toBe(true);
  });

  it('reports unknown-prop for props not in contract', () => {
    const contract = makeContract('Label', [
      { name: 'text', type: 'string', required: false },
    ]);
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Label', { text: 'OK', typo: 'unexpected' })]);

    const result = validateDocumentAgainstDS(doc, [contract]);
    expect(result.violations.some((v) => v.kind === 'unknown-prop' && v.propName === 'typo')).toBe(true);
  });

  it('validates all nodes, not just the first', () => {
    const id1 = createNodeId();
    const id2 = createNodeId();
    const doc = makeDoc([makeNode(id1, 'Missing1'), makeNode(id2, 'Missing2')]);

    const result = validateDocumentAgainstDS(doc, []);
    expect(result.violations).toHaveLength(2);
  });

  it('accepts boolean props correctly', () => {
    const contract = makeContract('Toggle', [
      { name: 'checked', type: 'boolean', required: false },
    ]);
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Toggle', { checked: true })]);

    const result = validateDocumentAgainstDS(doc, [contract]);
    expect(result.valid).toBe(true);
  });
});

// ============================================================================
// dsViolationsToValidationResult
// ============================================================================

describe('dsViolationsToValidationResult', () => {
  it('maps contract-not-found to errors', () => {
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Ghost')]);
    const dsResult = validateDocumentAgainstDS(doc, []);
    const vr = dsViolationsToValidationResult(dsResult);
    expect(vr.valid).toBe(false);
    expect(vr.errors.length).toBeGreaterThan(0);
  });

  it('maps unknown-prop to warnings, not errors', () => {
    const contract = makeContract('Box', [{ name: 'size', type: 'string', required: false }]);
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Box', { size: 'md', unknown: true })]);
    const dsResult = validateDocumentAgainstDS(doc, [contract]);
    const vr = dsViolationsToValidationResult(dsResult);
    expect(vr.warnings.some((w) => w.code === 'unknown-prop')).toBe(true);
    expect(vr.errors.some((e) => e.code === 'unknown-prop')).toBe(false);
  });

  it('returns valid=true when there are only warnings', () => {
    const contract = makeContract('Box', [{ name: 'size', type: 'string', required: false }]);
    const id = createNodeId();
    const doc = makeDoc([makeNode(id, 'Box', { size: 'md', extra: 'oops' })]);
    const dsResult = validateDocumentAgainstDS(doc, [contract]);
    const vr = dsViolationsToValidationResult(dsResult);
    expect(vr.valid).toBe(true);
  });
});

// ============================================================================
// checkStoryContractParity
// ============================================================================

describe('checkStoryContractParity', () => {
  it('reports no issues when all enum values are covered', () => {
    const contract = makeContract('Button', [
      { name: 'variant', type: 'enum', typeDetails: ['primary', 'secondary'], required: false },
    ]);
    const stories = [{ variant: 'primary' }, { variant: 'secondary' }];
    const report = checkStoryContractParity(contract, stories);
    expect(report.isComplete).toBe(true);
    expect(report.missingStories).toHaveLength(0);
  });

  it('reports missing stories for uncovered enum values', () => {
    const contract = makeContract('Button', [
      { name: 'variant', type: 'enum', typeDetails: ['primary', 'secondary', 'danger'], required: false },
    ]);
    const stories = [{ variant: 'primary' }];
    const report = checkStoryContractParity(contract, stories);
    expect(report.missingStories).toContain('variant="secondary"');
    expect(report.missingStories).toContain('variant="danger"');
  });

  it('reports undocumented story props not in contract', () => {
    const contract = makeContract('Button', [
      { name: 'label', type: 'string', required: false },
    ]);
    const stories = [{ label: 'Click', undocumentedProp: 'oops' }];
    const report = checkStoryContractParity(contract, stories);
    expect(report.undocumentedVariants).toContain('undocumentedProp');
  });

  it('is complete for a contract with no enum props and matching stories', () => {
    const contract = makeContract('Card', [
      { name: 'title', type: 'string', required: false },
    ]);
    const stories = [{ title: 'Hello' }];
    const report = checkStoryContractParity(contract, stories);
    expect(report.isComplete).toBe(true);
  });
});
