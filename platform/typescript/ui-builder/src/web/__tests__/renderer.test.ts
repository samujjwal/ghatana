/**
 * @fileoverview Tests for the vanilla DOM/HTML/TS renderer (Web target).
 *
 * @doc.type test
 * @doc.purpose Ensure serializeToHtml and mountToDOM produce correct output.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { serializeToHtml, mountToDOM } from '../renderer.js';
import type { ComponentContract } from '@ghatana/ds-schema';
import type { BuilderComponentManifest } from '@ghatana/ds-schema';
import type { BuilderDocument, ComponentInstance, NodeId } from '../../core/types.js';
import { createDocumentId, createNodeId } from '../../core/types.js';

// ----------------------------------------------------------------------------
// Fixtures
// ----------------------------------------------------------------------------

function makeDoc(nodes: Map<NodeId, ComponentInstance> = new Map()): BuilderDocument {
  return {
    id: createDocumentId(),
    version: '1',
    name: 'Test Document',
    designSystem: {
      id: 'ds-1',
      name: 'Test DS',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'theme-1',
    },
    rootNodes: Array.from(nodes.keys()) as readonly NodeId[],
    nodes,
    metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
  };
}

function makeButton(id: string, props: Record<string, unknown> = {}): ComponentInstance {
  return {
    id: createNodeId(id),
    contractName: 'Button',
    props: { children: 'Click me', variant: 'contained', ...props },
    slots: {},
    bindings: [],
    metadata: {},
  };
}

function makeTextField(id: string, props: Record<string, unknown> = {}): ComponentInstance {
  return {
    id: createNodeId(id),
    contractName: 'TextField',
    props: { label: 'Name', placeholder: 'Enter name...', ...props },
    slots: {},
    bindings: [],
    metadata: {},
  };
}

const buttonContract: ComponentContract = {
  name: 'Button',
  version: '1.0.0',
  metadata: {
    category: 'input',
    status: 'stable',
    platforms: ['web', 'android'],
    a11y: {
      role: 'button',
      ariaSupported: true,
      keyboardNavigation: true,
      screenReader: 'supported',
    },
    dataClassification: 'internal',
  },
  props: [],
  slots: [],
  events: [{ name: 'onClick' }],
};

const buttonManifest: BuilderComponentManifest = {
  name: 'Button',
  version: '1.0.0',
  targets: ['react', 'swiftui'],
  features: ['manifest-driven'],
  semantics: {
    role: 'button',
    eventNames: ['click'],
  },
  slots: [],
  capabilities: {
    interactive: true,
    collection: false,
    virtualizable: false,
    async: false,
    privacy: 'internal',
    optimizedFor: ['builder-handoff'],
  },
  dataClassification: 'internal',
  reviewRequired: false,
};

// ----------------------------------------------------------------------------
// serializeToHtml tests
// ----------------------------------------------------------------------------

describe('serializeToHtml', () => {
  it('serializes empty document to HTML shell', () => {
    const doc = makeDoc();
    const html = serializeToHtml(doc);
    expect(html).toContain('<!DOCTYPE html>');
    expect(html).toContain('<html');
    expect(html).toContain('</html>');
  });

  it('serializes a single Button component', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1');
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc);
    expect(html).toContain('data-builder-contract="Button"');
    expect(html).toContain('data-builder-platforms=');
    expect(html).toContain('data-builder-signature=');
    expect(html).toContain('Click me');
  });

  it('serializes platform annotations from contracts', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1');
    btn.metadata = { dataClassification: 'internal' };
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc, { contracts: [buttonContract], manifests: [buttonManifest] });
    expect(html).toContain('data-builder-platforms="react swiftui"');
    expect(html).toContain('data-builder-classification="internal"');
  });

  it('serializes multiple components', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1');
    const input = makeTextField('input-1');
    nodes.set(btn.id, btn);
    nodes.set(input.id, input);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc);
    expect(html).toContain('data-builder-contract="Button"');
    expect(html).toContain('data-builder-contract="TextField"');
  });

  it('serializes named slot children with slot attributes', () => {
    const header = makeButton('header-1', { children: 'Header action' });
    const card: ComponentInstance = {
      id: createNodeId('card-1'),
      contractName: 'Card',
      props: {},
      slots: {
        header: [header.id],
      },
      bindings: [],
      metadata: {},
    };

    const nodes = new Map<NodeId, ComponentInstance>();
    nodes.set(card.id, card);
    nodes.set(header.id, header);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc);
    expect(html).toContain('slot="header"');
    expect(html).toContain('data-builder-slot="header"');
  });

  it('respects emitDebugAttributes config', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1');
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    const withDebug = serializeToHtml(doc, { emitDebugAttributes: true });
    expect(withDebug).toContain('data-builder-node-id=');

    const withoutDebug = serializeToHtml(doc, { emitDebugAttributes: false });
    expect(withoutDebug).not.toContain('data-builder-node-id=');
  });

  it('escapes special characters in props', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1', { children: 'Click "here" & <there>' });
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc);
    expect(html).not.toContain('Click "here"');
    expect(html).toContain('&quot;');
    expect(html).toContain('&lt;');
    expect(html).toContain('&gt;');
    expect(html).toContain('&amp;');
  });

  it('serializes boolean props correctly', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1', { disabled: true, fullWidth: false });
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc);
    expect(html).toContain('disabled');
    expect(html).not.toMatch(/fullWidth="false"/);
  });
});

// ----------------------------------------------------------------------------
// mountToDOM tests
// ----------------------------------------------------------------------------

describe('mountToDOM', () => {
  let container: HTMLElement;

  beforeEach(() => {
    container = document.createElement('div');
    container.id = 'test-container';
    document.body.appendChild(container);
  });

  afterEach(() => {
    if (container.parentNode) {
      container.parentNode.removeChild(container);
    }
  });

  it('mounts a single Button to the DOM', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1');
    nodes.set(btn.id as NodeId, btn);
    const doc = makeDoc(nodes as Map<NodeId, ComponentInstance>);

    mountToDOM(doc, container, { contracts: [buttonContract] });

    const mounted = container.querySelector('[data-builder-contract="Button"]');
    expect(mounted).not.toBeNull();
    expect(mounted?.textContent).toContain('Click me');
    expect(mounted?.getAttribute('data-builder-platforms')).toContain('react');
  });

  it('mounts multiple components', () => {
    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1');
    const input = makeTextField('input-1');
    nodes.set(btn.id, btn);
    nodes.set(input.id, input);
    const doc = makeDoc(nodes);

    mountToDOM(doc, container);

    const buttons = container.querySelectorAll('[data-builder-contract="Button"]');
    const inputs = container.querySelectorAll('[data-builder-contract="TextField"]');
    expect(buttons.length).toBe(1);
    expect(inputs.length).toBe(1);
  });

  it('mounts named slot children with slot metadata', () => {
    const header = makeButton('header-1', { children: 'Header action' });
    const card: ComponentInstance = {
      id: createNodeId('card-1'),
      contractName: 'Card',
      props: {},
      slots: {
        header: [header.id],
      },
      bindings: [],
      metadata: {},
    };

    const nodes = new Map<NodeId, ComponentInstance>();
    nodes.set(card.id, card);
    nodes.set(header.id, header);
    const doc = makeDoc(nodes);

    mountToDOM(doc, container);

    const slottedChild = container.querySelector('[slot="header"]');
    expect(slottedChild).not.toBeNull();
    expect(slottedChild?.getAttribute('data-builder-slot')).toBe('header');
  });

  it('clears previous content when mounting', () => {
    const existing = document.createElement('span');
    existing.textContent = 'existing';
    container.appendChild(existing);

    const nodes = new Map<NodeId, ComponentInstance>();
    const btn = makeButton('btn-1');
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    mountToDOM(doc, container);

    expect(container.textContent).not.toContain('existing');
    expect(container.textContent).toContain('Click me');
  });

  it('throws when container is not found', () => {
    const doc = makeDoc();
    expect(() => mountToDOM(doc, '#nonexistent')).toThrow();
  });
});
