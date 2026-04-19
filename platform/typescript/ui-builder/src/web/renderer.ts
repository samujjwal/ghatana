/**
 * @fileoverview Vanilla DOM/HTML/TypeScript rendering target for BuilderDocument.
 *
 * Provides a framework-agnostic renderer that serialises a BuilderDocument
 * to plain HTML strings or live DOM nodes, suitable for server-side rendering,
 * email templates, static site generation, or non-React product surfaces.
 */

import type { ComponentContract } from '@ghatana/ds-schema';
import type { BuilderDocument, ComponentInstance, NodeId } from '../core/types.js';
import { projectInstanceToPlatformPlan, type ContractLookup, type ManifestLookup } from '../core/platform-plan.js';

// ============================================================================
// Renderer Configuration
// ============================================================================

export interface WebRendererConfig {
  /** Indentation per level for HTML output. Default: 2 */
  readonly indent: number;
  /** Whether to emit data-builder-* attributes for debuggability. Default: false */
  readonly emitDebugAttributes: boolean;
  /** Custom attribute prefix for builder annotations. Default: 'data-builder' */
  readonly debugAttributePrefix: string;
  /** Slot separator in multi-slot components. Default: '' */
  readonly slotSeparator: string;
  /** Optional contract lookup for platform-aware annotations */
  readonly contracts?: ContractLookup;
  /** Optional manifest lookup from design-system recipes */
  readonly manifests?: ManifestLookup;
}

const DEFAULT_CONFIG: WebRendererConfig = {
  indent: 2,
  emitDebugAttributes: false,
  debugAttributePrefix: 'data-builder',
  slotSeparator: '',
  contracts: undefined,
  manifests: undefined,
};

function toContractMap(contracts?: ContractLookup): ReadonlyMap<string, ComponentContract> {
  if (!contracts) return new Map();
  if (contracts instanceof Map) return contracts;
  if (Array.isArray(contracts)) {
    return new Map(contracts.map((contract) => [contract.name, contract]));
  }
  return new Map();
}

// ============================================================================
// Prop Serialization
// ============================================================================

/** Convert a prop value to an HTML attribute string. */
function propToAttr(name: string, value: unknown): string {
  if (typeof value === 'boolean') {
    return value ? name : '';
  }
  if (typeof value === 'string') {
    return `${name}="${escapeAttr(value)}"`;
  }
  if (typeof value === 'number') {
    return `${name}="${value}"`;
  }
  if (value === null || value === undefined) {
    return '';
  }
  return `${name}="${escapeAttr(JSON.stringify(value))}"`;
}

function escapeAttr(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// ============================================================================
// HTML Serializer
// ============================================================================

/**
 * Serializes a BuilderDocument to an HTML string.
 * Components are rendered as semantic custom elements: `<ghatana-{contract-name}>`.
 */
export function serializeToHtml(
  document: BuilderDocument,
  config: Partial<WebRendererConfig> = {},
): string {
  const cfg = { ...DEFAULT_CONFIG, ...config };
  const lines: string[] = ['<!DOCTYPE html>', '<html>', '<body>'];

  for (const rootId of document.rootNodes) {
    const node = document.nodes.get(rootId);
    if (node) {
      lines.push(...renderNodeHtml(node, document, cfg, 1));
    }
  }

  lines.push('</body>', '</html>');
  return lines.join('\n');
}

function renderNodeHtml(
  node: ComponentInstance,
  document: BuilderDocument,
  cfg: WebRendererConfig,
  depth: number,
  inheritedAttrs: Readonly<Record<string, string>> = {},
): string[] {
  const pad = ' '.repeat(cfg.indent * depth);
  const tagName = `ghatana-${node.contractName.toLowerCase().replace(/[^a-z0-9]/g, '-')}`;
  const manifestMap = cfg.manifests instanceof Map
    ? cfg.manifests
    : Array.isArray(cfg.manifests)
      ? new Map(cfg.manifests.map((manifest) => [manifest.name, manifest]))
      : new Map();
  const platformPlan = projectInstanceToPlatformPlan(
    node,
    toContractMap(cfg.contracts).get(node.contractName),
    manifestMap.get(node.contractName),
  );

  const attrs: string[] = [
    // Always emit data-builder-contract — it is a semantic attribute, not a debug attribute
    `${cfg.debugAttributePrefix}-contract="${escapeAttr(node.contractName)}"`,
    `${cfg.debugAttributePrefix}-platforms="${escapeAttr(platformPlan.targets.join(' '))}"`,
    `${cfg.debugAttributePrefix}-signature="${escapeAttr(platformPlan.signature)}"`,
  ];

  if (platformPlan.features.length > 0) {
    attrs.push(`${cfg.debugAttributePrefix}-features="${escapeAttr(platformPlan.features.join(' '))}"`);
  }
  if (platformPlan.dataClassification) {
    attrs.push(`${cfg.debugAttributePrefix}-classification="${escapeAttr(platformPlan.dataClassification)}"`);
  }
  if (platformPlan.reviewRequired) {
    attrs.push(`${cfg.debugAttributePrefix}-review-required="true"`);
  }

  if (cfg.emitDebugAttributes) {
    attrs.push(`${cfg.debugAttributePrefix}-node-id="${node.id}"`);
  }

  for (const [key, value] of Object.entries(inheritedAttrs)) {
    attrs.push(`${key}="${escapeAttr(value)}"`);
  }

  for (const [key, value] of Object.entries(node.props)) {
    if (key === 'children' || key === 'className') continue;
    const attr = propToAttr(key, value);
    if (attr) attrs.push(attr);
  }

  const attrStr = attrs.length > 0 ? ' ' + attrs.join(' ') : '';
  const lines: string[] = [`${pad}<${tagName}${attrStr}>`];

  // Render text children
  if (typeof node.props['children'] === 'string') {
    lines.push(`${pad}${' '.repeat(cfg.indent)}${escapeHtml(node.props['children'])}`);
  }

  // Render slot children
  for (const slotPlan of platformPlan.slots) {
    for (const childId of slotPlan.childIds) {
      const child = document.nodes.get(childId as NodeId);
      if (child) {
        const childAttrs = slotPlan.name === 'default'
          ? {}
          : { slot: slotPlan.name, [`${cfg.debugAttributePrefix}-slot`]: slotPlan.name };
        lines.push(...renderNodeHtml(child, document, cfg, depth + 1, childAttrs));
      }
    }
    if (cfg.slotSeparator) lines.push(`${pad}${cfg.slotSeparator}`);
  }

  lines.push(`${pad}</${tagName}>`);
  return lines;
}

// ============================================================================
// DOM Renderer (live nodes)
// ============================================================================

/**
 * Mounts a BuilderDocument into a live DOM container element or a CSS selector.
 *
 * - Clears any existing content in the container before mounting.
 * - Returns a cleanup function that removes all created nodes.
 * - Throws a `RangeError` when a selector string resolves to no element.
 */
export function mountToDOM(
  document: BuilderDocument,
  containerOrSelector: Element | string,
  config: Partial<WebRendererConfig> = {},
): () => void {
  const container = resolveContainer(containerOrSelector);
  const cfg = { ...DEFAULT_CONFIG, ...config };

  // Clear existing content
  container.innerHTML = '';

  const mountedElements: Element[] = [];

  for (const rootId of document.rootNodes) {
    const node = document.nodes.get(rootId);
    if (node) {
      const el = renderNodeDOM(node, document, cfg);
      container.appendChild(el);
      mountedElements.push(el);
    }
  }

  return () => {
    for (const el of mountedElements) {
      if (el.parentNode === container) {
        container.removeChild(el);
      }
    }
  };
}

/**
 * Resolves `containerOrSelector` to an `Element`.
 * Throws a `RangeError` when a string selector finds no element.
 */
function resolveContainer(containerOrSelector: Element | string): Element {
  if (typeof containerOrSelector === 'string') {
    const el = globalThis.document?.querySelector(containerOrSelector);
    if (!el) {
      throw new RangeError(
        `mountToDOM: no element matching selector "${containerOrSelector}"`,
      );
    }
    return el;
  }
  return containerOrSelector;
}

function renderNodeDOM(
  node: ComponentInstance,
  document: BuilderDocument,
  cfg: WebRendererConfig,
  inheritedAttrs: Readonly<Record<string, string>> = {},
): Element {
  const tagName = `ghatana-${node.contractName.toLowerCase().replace(/[^a-z0-9]/g, '-')}`;
  const manifestMap = cfg.manifests instanceof Map
    ? cfg.manifests
    : Array.isArray(cfg.manifests)
      ? new Map(cfg.manifests.map((manifest) => [manifest.name, manifest]))
      : new Map();
  const platformPlan = projectInstanceToPlatformPlan(
    node,
    toContractMap(cfg.contracts).get(node.contractName),
    manifestMap.get(node.contractName),
  );

  const el = globalThis.document?.createElement(tagName) ??
    ({
      setAttribute: () => { /* noop */ },
      appendChild: () => { /* noop */ },
      textContent: '',
    } as unknown as Element);

  // Always emit data-builder-contract
  el.setAttribute(`${cfg.debugAttributePrefix}-contract`, node.contractName);
  el.setAttribute(`${cfg.debugAttributePrefix}-platforms`, platformPlan.targets.join(' '));
  el.setAttribute(`${cfg.debugAttributePrefix}-signature`, platformPlan.signature);
  if (platformPlan.features.length > 0) {
    el.setAttribute(`${cfg.debugAttributePrefix}-features`, platformPlan.features.join(' '));
  }
  if (platformPlan.dataClassification) {
    el.setAttribute(`${cfg.debugAttributePrefix}-classification`, platformPlan.dataClassification);
  }
  if (platformPlan.reviewRequired) {
    el.setAttribute(`${cfg.debugAttributePrefix}-review-required`, 'true');
  }

  if (cfg.emitDebugAttributes) {
    el.setAttribute(`${cfg.debugAttributePrefix}-node-id`, node.id);
  }

  for (const [key, value] of Object.entries(inheritedAttrs)) {
    el.setAttribute(key, value);
  }

  for (const [key, value] of Object.entries(node.props)) {
    if (key === 'children') {
      if (typeof value === 'string') {
        el.textContent = value;
      }
      continue;
    }
    const attr = propToAttr(key, value);
    if (attr) {
      const eqIdx = attr.indexOf('=');
      if (eqIdx === -1) {
        el.setAttribute(attr, '');
      } else {
        el.setAttribute(attr.slice(0, eqIdx), attr.slice(eqIdx + 2, -1));
      }
    }
  }

  for (const slotPlan of platformPlan.slots) {
    for (const childId of slotPlan.childIds) {
      const child = document.nodes.get(childId as NodeId);
      if (child) {
        const childAttrs = slotPlan.name === 'default'
          ? {}
          : { slot: slotPlan.name, [`${cfg.debugAttributePrefix}-slot`]: slotPlan.name };
        el.appendChild(renderNodeDOM(child, document, cfg, childAttrs));
      }
    }
  }

  return el;
}
