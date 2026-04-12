/**
 * @fileoverview Tests for the vanilla DOM/HTML/TS renderer (Web target).
 *
 * @doc.type test
 * @doc.purpose Ensure serializeToHtml and mountToDOM produce correct output.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { serializeToHtml, mountToDOM } from '../renderer.js';
import type { BuilderDocument, ComponentInstance } from '../../core/types.js';
import { createDocumentId, createNodeId } from '../../core/types.js';

// ----------------------------------------------------------------------------
// Fixtures
// ----------------------------------------------------------------------------

function makeDoc(nodes: Map<string, ComponentInstance> = new Map()): BuilderDocument {
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
    rootNodes: Array.from(nodes.keys()),
    nodes,
    metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
  };
}

function makeButton(id: string, props: Record<string, unknown> = {}): ComponentInstance {
  return {
    id: createNodeId(id),
    contractName: 'Button',
    props: { children: 'Click me', variant: 'contained', ...props },
    slots: new Map(),
    children: [],
    parentId: undefined,
    metadata: { createdAt: new Date().toISOString() },
  };
}

function makeTextField(id: string, props: Record<string, unknown> = {}): ComponentInstance {
  return {
    id: createNodeId(id),
    contractName: 'TextField',
    props: { label: 'Name', placeholder: 'Enter name...', ...props },
    slots: new Map(),
    children: [],
    parentId: undefined,
    metadata: { createdAt: new Date().toISOString() },
  };
}

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
    const nodes = new Map<string, ComponentInstance>();
    const btn = makeButton('btn-1');
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc);
    expect(html).toContain('data-builder-contract="Button"');
    expect(html).toContain('Click me');
  });

  it('serializes multiple components', () => {
    const nodes = new Map<string, ComponentInstance>();
    const btn = makeButton('btn-1');
    const input = makeTextField('input-1');
    nodes.set(btn.id, btn);
    nodes.set(input.id, input);
    const doc = makeDoc(nodes);

    const html = serializeToHtml(doc);
    expect(html).toContain('data-builder-contract="Button"');
    expect(html).toContain('data-builder-contract="TextField"');
  });

  it('respects emitDebugAttributes config', () => {
    const nodes = new Map<string, ComponentInstance>();
    const btn = makeButton('btn-1');
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    const withDebug = serializeToHtml(doc, { emitDebugAttributes: true });
    expect(withDebug).toContain('data-builder-node-id=');

    const withoutDebug = serializeToHtml(doc, { emitDebugAttributes: false });
    expect(withoutDebug).not.toContain('data-builder-node-id=');
  });

  it('escapes special characters in props', () => {
    const nodes = new Map<string, ComponentInstance>();
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
    const nodes = new Map<string, ComponentInstance>();
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

  it('mounts a single Button to the DOM', () => {
    const nodes = new Map<string, ComponentInstance>();
    const btn = makeButton('btn-1');
    nodes.set(btn.id, btn);
    const doc = makeDoc(nodes);

    mountToDOM(doc, container);

    const mounted = container.querySelector('[data-builder-contract="Button"]');
    expect(mounted).not.toBeNull();
    expect(mounted?.textContent).toContain('Click me');
  });

  it('mounts multiple components', () => {
    const nodes = new Map<string, ComponentInstance>();
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

  it('clears previous content when mounting', () => {
    const existing = document.createElement('span');
    existing.textContent = 'existing';
    container.appendChild(existing);

    const nodes = new Map<string, ComponentInstance>();
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
