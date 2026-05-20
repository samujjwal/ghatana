/**
 * @fileoverview Tests for HybridCanvasProvider and createHybridCanvasStore.
 *
 * Verifies that the provider renders children correctly and that isolated
 * stores prevent cross-canvas state bleed.
 *
 * @doc.type test
 * @doc.purpose HybridCanvasProvider isolation and rendering
 * @doc.layer canvas
 */

import { describe, it, expect, afterEach } from 'vitest';
import React from 'react';
import { createRoot } from 'react-dom/client';
import { act } from 'react-dom/test-utils';
import { HybridCanvasProvider } from '../HybridCanvasProvider.js';
import { createHybridCanvasStore, createCanvasStore, hybridCanvasStateAtom } from '../state.js';

let container: HTMLDivElement | null = null;

afterEach(() => {
  if (container) {
    document.body.removeChild(container);
    container = null;
  }
});

describe('createHybridCanvasStore', () => {
  it('creates an isolated store with a pre-seeded canvas state', () => {
    const store = createHybridCanvasStore();
    const state = store.get(hybridCanvasStateAtom);
    expect(state).toBeDefined();
    expect(state.mode).toBe('hybrid-freeform');
    expect(state.elements).toHaveLength(0);
    expect(state.nodes).toHaveLength(0);
    expect(state.edges).toHaveLength(0);
  });

  it('two stores are independent — mutations do not cross over', () => {
    const store1 = createHybridCanvasStore();
    const store2 = createHybridCanvasStore();

    // Mutate store1 independently
    store1.set(hybridCanvasStateAtom, {
      ...store1.get(hybridCanvasStateAtom),
      tool: 'pan',
    });

    const state1 = store1.get(hybridCanvasStateAtom);
    const state2 = store2.get(hybridCanvasStateAtom);

    expect(state1.tool).toBe('pan');
    expect(state2.tool).toBe('select'); // unchanged
  });

  it('is an alias for createCanvasStore (same shape)', () => {
    const storeA = createHybridCanvasStore();
    const storeB = createCanvasStore();

    const stateA = storeA.get(hybridCanvasStateAtom);
    const stateB = storeB.get(hybridCanvasStateAtom);

    // Both produce the same initial state shape
    expect(stateA.mode).toBe(stateB.mode);
    expect(stateA.tool).toBe(stateB.tool);
    expect(stateA.elements).toEqual(stateB.elements);
  });
});

describe('HybridCanvasProvider', () => {
  it('renders children without throwing', async () => {
    container = document.createElement('div');
    document.body.appendChild(container);

    const store = createHybridCanvasStore();

    await act(async () => {
      createRoot(container!).render(
        React.createElement(
          HybridCanvasProvider,
          { store },
          React.createElement('span', { id: 'child' }, 'hello canvas'),
        ),
      );
    });

    expect(container.querySelector('#child')?.textContent).toBe('hello canvas');
  });

  it('renders children without an explicit store prop (auto-creates store)', async () => {
    container = document.createElement('div');
    document.body.appendChild(container);

    await act(async () => {
      createRoot(container!).render(
        React.createElement(
          HybridCanvasProvider,
          null,
          React.createElement('span', { id: 'auto-child' }, 'auto'),
        ),
      );
    });

    expect(container.querySelector('#auto-child')?.textContent).toBe('auto');
  });

  it('two providers use independent stores', () => {
    const store1 = createHybridCanvasStore();
    const store2 = createHybridCanvasStore();

    store1.set(hybridCanvasStateAtom, {
      ...store1.get(hybridCanvasStateAtom),
      readOnly: true,
    });

    expect(store1.get(hybridCanvasStateAtom).readOnly).toBe(true);
    expect(store2.get(hybridCanvasStateAtom).readOnly).toBe(false);
  });
});
