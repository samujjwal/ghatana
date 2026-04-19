import * as React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import {
  createFeatureAttributes,
  createPressableBehavior,
  createStateAttributes,
  useComponentComposition,
  type ComponentBehavior,
} from '../core';
import { Card } from '../molecules/Card';
import { PolicyCard } from '../molecules/PolicyCard';

describe('component composition model', () => {
  it('creates aggregate feature and state attributes', () => {
    expect(createStateAttributes({
      interactive: true,
      status: 'Active',
      count: 3,
      hidden: false,
    })).toEqual({
      'data-state-interactive': 'true',
      'data-state-status': 'active',
      'data-state-count': '3',
      'data-state': 'interactive status:active count:3',
    });

    expect(createFeatureAttributes(['surface', 'dismissible', 'surface'])).toEqual({
      'data-features': 'surface dismissible',
      'data-feature-surface': 'true',
      'data-feature-dismissible': 'true',
    });
  });

  it('merges behaviors, telemetry, and slot handoff through useComponentComposition', () => {
    const rootClick = vi.fn();
    const badgeClick = vi.fn();
    const telemetryListener = vi.fn();
    document.addEventListener('ghatana:component-event', telemetryListener);

    const analyticsBehavior: ComponentBehavior = {
      name: 'analytics',
      features: ['instrumented'],
      state: { tracked: true },
      slots: {
        badge: {
          className: 'behavior-badge',
          onClick: badgeClick,
        },
      },
    };

    function Harness() {
      const composition = useComponentComposition({
        metadata: {
          component: 'composed-banner',
          variant: 'soft',
          privacy: 'internal',
        },
        state: {
          open: true,
        },
        features: ['banner'],
        behaviors: [
          analyticsBehavior,
          createPressableBehavior(),
        ],
        rootProps: {
          onClick: rootClick,
          className: 'root-node',
        },
        slotProps: {
          badge: {
            className: 'instance-badge',
          },
        },
      });

      return (
        <div {...composition.rootProps}>
          <button
            type="button"
            {...composition.getSlotProps('badge', {
              className: 'override-badge',
            })}
          >
            Badge
          </button>
        </div>
      );
    }

    render(<Harness />);

    const root = screen.getByText('Badge').parentElement;
    expect(root).not.toBeNull();
    expect(root).toHaveAttribute('role', 'button');
    expect(root).toHaveAttribute('data-component', 'composed-banner');
    expect(root).toHaveAttribute('data-privacy', 'internal');
    expect(root).toHaveAttribute('data-features', 'banner instrumented pressable keyboard-accessible');
    expect(root).toHaveAttribute('data-state', 'open tracked interactive');

    const badge = screen.getByText('Badge');
    expect(badge.className).toContain('behavior-badge');
    expect(badge.className).toContain('instance-badge');
    expect(badge.className).toContain('override-badge');
    expect(badge).toHaveAttribute('data-slot', 'badge');

    fireEvent.click(badge);
    expect(badgeClick).toHaveBeenCalledTimes(1);
    rootClick.mockClear();

    fireEvent.keyDown(root!, { key: 'Enter' });
    expect(rootClick).toHaveBeenCalledTimes(1);
    expect(telemetryListener).toHaveBeenCalled();

    document.removeEventListener('ghatana:component-event', telemetryListener);
  });

  it('applies composition metadata to interactive cards', () => {
    const handleClick = vi.fn();

    render(
      <Card interactive title="Security policy" onClick={handleClick}>
        Card body
      </Card>
    );

    const card = screen.getByRole('button');
    expect(card).toHaveAttribute('data-component', 'card');
    expect(card).toHaveAttribute('data-feature-interactive', 'true');
    expect(screen.getByText('Security policy')).toHaveAttribute('data-slot', 'title');
    expect(screen.getByText('Card body').closest('[data-slot="body"]')).not.toBeNull();

    fireEvent.keyDown(card, { key: 'Enter' });
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('marks policy cards as internal and selectable', () => {
    render(
      <PolicyCard
        isSelected
        showActions={false}
        policy={{
          id: 'policy-1',
          name: 'Student Safety',
          status: 'active',
          appsCount: 3,
          rulesCount: 7,
        }}
      />
    );

    const card = screen.getByRole('button');
    expect(card).toHaveAttribute('data-component', 'policy-card');
    expect(card).toHaveAttribute('data-privacy', 'internal');
    expect(card).toHaveAttribute('data-feature-selectable', 'true');
    expect(card).toHaveAttribute('aria-pressed', 'true');
  });
});
