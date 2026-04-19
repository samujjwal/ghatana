import * as React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';

import {
  createPrimitiveAttributes,
  createSlotProps,
  mergePrimitiveProps,
  useComponentTelemetry,
} from '../core';

describe('runtime composition primitives', () => {
  it('creates safe component metadata attributes', () => {
    expect(createPrimitiveAttributes({
      component: 'Secure Card',
      slot: 'Action Bar',
      variant: 'Elevated',
      state: 'Read Only',
      privacy: 'internal',
      observabilityId: 'Card_01',
    })).toEqual({
      'data-component': 'secure-card',
      'data-scope': 'secure-card',
      'data-slot': 'action-bar',
      'data-part': 'action-bar',
      'data-variant': 'elevated',
      'data-state': 'read-only',
      'data-privacy': 'internal',
      'data-o11y-id': 'card_01',
    });
  });

  it('merges classes, styles, aria descriptions, and handlers', () => {
    const first = vi.fn();
    const second = vi.fn();

    const props = mergePrimitiveProps(
      {
        className: 'alpha',
        style: { color: 'red' },
        'aria-describedby': 'hint',
        onClick: first,
      },
      {
        className: 'beta',
        style: { backgroundColor: 'black' },
        'aria-describedby': 'error hint',
        onClick: second,
      }
    );

    expect(props.className).toContain('alpha');
    expect(props.className).toContain('beta');
    expect(props.style).toMatchObject({ color: 'red', backgroundColor: 'black' });
    expect(props['aria-describedby']).toBe('hint error');

    props.onClick?.({ type: 'click' } as React.MouseEvent<HTMLElement>);
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);
  });

  it('creates slot props with metadata and overrides', () => {
    const props = createSlotProps(
      { component: 'PolicyCard', slot: 'actions', state: 'idle' },
      { className: 'base' },
      { className: 'override', title: 'Actions' }
    );

    expect(props.className).toContain('base');
    expect(props.className).toContain('override');
    expect(props.title).toBe('Actions');
    expect(props['data-component']).toBe('policycard');
    expect(props['data-slot']).toBe('actions');
    expect(props['data-state']).toBe('idle');
  });

  it('emits sanitized telemetry events', () => {
    const listener = vi.fn();
    document.addEventListener('ghatana:component-event', listener);

    function Harness() {
      const track = useComponentTelemetry({
        metadata: {
          component: 'ActivityFeed',
          slot: 'item',
          privacy: 'sensitive',
          observabilityId: 'feed-1',
        },
      });

      React.useEffect(() => {
        track('Item Clicked', {
          action: 'open-detail',
          recordId: 'User Email: jane@example.com',
          count: 2,
        });
      }, [track]);

      return null;
    }

    render(<Harness />);

    expect(listener).toHaveBeenCalledTimes(1);
    const customEvent = listener.mock.calls[0]?.[0] as CustomEvent;
    expect(customEvent.detail).toMatchObject({
      event: 'item-clicked',
      metadata: {
        'data-component': 'activityfeed',
        'data-slot': 'item',
        'data-privacy': 'sensitive',
        'data-o11y-id': 'feed-1',
      },
      payload: {
        action: 'open-detail',
        recordId: '[redacted]',
        count: 2,
      },
    });

    document.removeEventListener('ghatana:component-event', listener);
  });
});
