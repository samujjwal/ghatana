/**
 * @fileoverview Tests for canvas interaction helpers.
 */
import { describe, it, expect, vi } from 'vitest';
import {
  makePointerEvent,
  dispatchPointerEvent,
  simulateClick,
  simulateDrag,
  simulateKeyPress,
  makeTestViewport,
  assertViewportEqual,
  assertRenderedRole,
  assertNotRenderedRole,
  assertHasClass,
  assertAriaAttribute,
} from '../interaction-helpers.js';

// ── makePointerEvent ─────────────────────────────────────────────────────────

describe('makePointerEvent', () => {
  it('creates a valid pointer event descriptor with defaults', () => {
    const ev = makePointerEvent('click', { x: 10, y: 20 });
    expect(ev.type).toBe('click');
    expect(ev.position).toEqual({ x: 10, y: 20 });
    expect(ev.button).toBe(0);
    expect(ev.shiftKey).toBe(false);
    expect(ev.ctrlKey).toBe(false);
    expect(ev.metaKey).toBe(false);
    expect(ev.altKey).toBe(false);
  });

  it('passes through modifier keys', () => {
    const ev = makePointerEvent('pointerdown', { x: 0, y: 0 }, { shiftKey: true, ctrlKey: true });
    expect(ev.shiftKey).toBe(true);
    expect(ev.ctrlKey).toBe(true);
  });
});

// ── dispatchPointerEvent ─────────────────────────────────────────────────────

describe('dispatchPointerEvent', () => {
  it('dispatches a PointerEvent on the target element', () => {
    const div = document.createElement('div');
    const listener = vi.fn();
    div.addEventListener('pointerdown', listener);
    dispatchPointerEvent(div, 'pointerdown', { x: 5, y: 10 });
    expect(listener).toHaveBeenCalledOnce();
    const event = listener.mock.calls[0][0] as PointerEvent;
    expect(event.clientX).toBe(5);
    expect(event.clientY).toBe(10);
  });
});

// ── simulateClick ────────────────────────────────────────────────────────────

describe('simulateClick', () => {
  it('fires pointerdown, pointerup, and click events', () => {
    const div = document.createElement('div');
    const calls: string[] = [];
    div.addEventListener('pointerdown', () => calls.push('pointerdown'));
    div.addEventListener('pointerup', () => calls.push('pointerup'));
    div.addEventListener('click', () => calls.push('click'));
    simulateClick(div, { x: 1, y: 2 });
    expect(calls).toEqual(['pointerdown', 'pointerup', 'click']);
  });
});

// ── simulateDrag ─────────────────────────────────────────────────────────────

describe('simulateDrag', () => {
  it('fires pointerdown, then multiple pointermove, then pointerup', () => {
    const div = document.createElement('div');
    const downs: number[] = [];
    const moves: number[] = [];
    const ups: number[] = [];
    div.addEventListener('pointerdown', (e) => downs.push((e as PointerEvent).clientX));
    div.addEventListener('pointermove', (e) => moves.push((e as PointerEvent).clientX));
    div.addEventListener('pointerup', (e) => ups.push((e as PointerEvent).clientX));
    simulateDrag(div, { x: 0, y: 0 }, { x: 100, y: 0 }, 5);
    expect(downs).toHaveLength(1);
    expect(moves).toHaveLength(5);
    expect(ups).toHaveLength(1);
    expect(ups[0]).toBe(100);
  });
});

// ── simulateKeyPress ─────────────────────────────────────────────────────────

describe('simulateKeyPress', () => {
  it('fires keydown and keyup', () => {
    const div = document.createElement('div');
    const events: string[] = [];
    div.addEventListener('keydown', (e) => events.push(`down:${(e as KeyboardEvent).key}`));
    div.addEventListener('keyup', (e) => events.push(`up:${(e as KeyboardEvent).key}`));
    simulateKeyPress(div, 'z');
    expect(events).toEqual(['down:z', 'up:z']);
  });

  it('passes modifier keys', () => {
    const div = document.createElement('div');
    div.addEventListener('keydown', (e) => {
      expect((e as KeyboardEvent).metaKey).toBe(true);
    });
    simulateKeyPress(div, 'z', { metaKey: true });
  });
});

// ── makeTestViewport ─────────────────────────────────────────────────────────

describe('makeTestViewport', () => {
  it('returns default viewport', () => {
    const vp = makeTestViewport();
    expect(vp).toEqual({ x: 0, y: 0, zoom: 1 });
  });

  it('merges overrides', () => {
    const vp = makeTestViewport({ zoom: 2, x: 10 });
    expect(vp.zoom).toBe(2);
    expect(vp.x).toBe(10);
    expect(vp.y).toBe(0);
  });
});

// ── assertViewportEqual ──────────────────────────────────────────────────────

describe('assertViewportEqual', () => {
  it('passes for equal viewports', () => {
    expect(() =>
      assertViewportEqual({ x: 0, y: 0, zoom: 1 }, { x: 0, y: 0, zoom: 1 }),
    ).not.toThrow();
  });

  it('passes within tolerance', () => {
    expect(() =>
      assertViewportEqual({ x: 0.0001, y: 0, zoom: 1 }, { x: 0, y: 0, zoom: 1 }),
    ).not.toThrow();
  });

  it('throws when outside tolerance', () => {
    expect(() =>
      assertViewportEqual({ x: 1, y: 0, zoom: 1 }, { x: 0, y: 0, zoom: 1 }),
    ).toThrow('ViewportState mismatch');
  });
});

// ── DOM assertion helpers ────────────────────────────────────────────────────

describe('assertRenderedRole', () => {
  it('passes when role exists', () => {
    const div = document.createElement('div');
    div.innerHTML = '<button role="button" aria-label="Save">Save</button>';
    expect(() => assertRenderedRole(div, 'button')).not.toThrow();
  });

  it('passes when role and label match', () => {
    const div = document.createElement('div');
    div.innerHTML = '<button role="button" aria-label="Save">Save</button>';
    expect(() => assertRenderedRole(div, 'button', 'Save')).not.toThrow();
  });

  it('throws when role is not found', () => {
    const div = document.createElement('div');
    expect(() => assertRenderedRole(div, 'dialog')).toThrow('role="dialog"');
  });

  it('throws when label does not match', () => {
    const div = document.createElement('div');
    div.innerHTML = '<button role="button" aria-label="Cancel">Cancel</button>';
    expect(() => assertRenderedRole(div, 'button', 'Save')).toThrow('Save');
  });
});

describe('assertNotRenderedRole', () => {
  it('passes when role is absent', () => {
    const div = document.createElement('div');
    expect(() => assertNotRenderedRole(div, 'dialog')).not.toThrow();
  });

  it('throws when role is present', () => {
    const div = document.createElement('div');
    div.innerHTML = '<div role="dialog"></div>';
    expect(() => assertNotRenderedRole(div, 'dialog')).toThrow('dialog');
  });
});

describe('assertHasClass', () => {
  it('passes when class exists', () => {
    const div = document.createElement('div');
    div.classList.add('active');
    expect(() => assertHasClass(div, 'active')).not.toThrow();
  });

  it('throws when class is absent', () => {
    const div = document.createElement('div');
    expect(() => assertHasClass(div, 'active')).toThrow('"active"');
  });
});

describe('assertAriaAttribute', () => {
  it('passes when attribute matches', () => {
    const div = document.createElement('div');
    div.setAttribute('aria-expanded', 'true');
    expect(() => assertAriaAttribute(div, 'aria-expanded', 'true')).not.toThrow();
  });

  it('throws when attribute value differs', () => {
    const div = document.createElement('div');
    div.setAttribute('aria-expanded', 'false');
    expect(() => assertAriaAttribute(div, 'aria-expanded', 'true')).toThrow('true');
  });

  it('throws when attribute is missing', () => {
    const div = document.createElement('div');
    expect(() => assertAriaAttribute(div, 'aria-expanded', 'true')).toThrow('missing');
  });
});
