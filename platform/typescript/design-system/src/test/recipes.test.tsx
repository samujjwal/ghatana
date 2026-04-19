import * as React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import {
  compileComponentRecipe,
  createBuilderManifestFromRecipe,
  createPressableBehavior,
  useComponentRecipe,
  type PrimitiveStateBag,
} from '../core';

describe('component recipes', () => {
  it('compiles a simple static recipe into a serializable render plan', () => {
    const recipe = compileComponentRecipe<{
      tone?: 'neutral' | 'danger';
      children?: string;
    }>({
      name: 'status-pill',
      defaultProps: {
        tone: 'neutral',
      },
      metadata: {
        component: 'status-pill',
        privacy: 'public',
      },
      deriveState: (props) => ({
        emphasized: props.tone === 'danger',
      }),
      deriveFeatures: ({ props }) => ['inline', props.tone === 'danger' ? 'attention' : undefined],
      slots: {
        label: {
          props: {
            className: 'pill-label',
          },
        },
      },
    });

    const plan = recipe.createRenderPlan({
      props: {
        tone: 'danger',
        children: 'Delete',
      },
    });

    expect(recipe.kind).toBe('compiled-component-recipe');
    expect(recipe.staticMetadata?.component).toBe('status-pill');
    expect(recipe.slotOrder).toEqual(['label']);
    expect(plan.metadata).toMatchObject({
      component: 'status-pill',
      privacy: 'public',
    });
    expect(plan.platform.targets).toEqual(['react']);
    expect(plan.features).toEqual(['inline', 'attention']);
    expect(plan.rootAttributes).toMatchObject({
      'data-component': 'status-pill',
      'data-scope': 'status-pill',
      'data-feature-attention': 'true',
    });
    expect(plan.slots.label.attributes).toMatchObject({
      'data-slot': 'label',
      'data-part': 'label',
    });
    expect(plan.signature).toContain('status-pill');
  });

  it('supports complex data-driven recipes and behavior composition', () => {
    type FeedProps = {
      interactive?: boolean;
      title?: string;
      items?: Array<{ id: string; label: string }>;
      variant?: 'compact' | 'comfortable';
    };

    type FeedState = PrimitiveStateBag & {
      itemCount: number;
      empty: boolean;
    };

    const feedRecipe = compileComponentRecipe<FeedProps, FeedState, 'header' | 'list' | 'empty' | 'footer'>({
      name: 'activity-feed',
      defaultProps: {
        interactive: false,
        variant: 'comfortable',
      },
      metadata: ({ props }) => ({
        component: 'activity-feed',
        variant: props.variant,
        privacy: 'internal',
      }),
      deriveState: (props) => ({
        interactive: Boolean(props.interactive),
        itemCount: props.items?.length ?? 0,
        empty: (props.items?.length ?? 0) === 0,
      }),
      deriveFeatures: ({ state, props }) => [
        'collection',
        state.empty ? 'empty-state' : 'populated',
        props.interactive ? 'interactive' : undefined,
      ],
      rootProps: ({ props, state }) => ({
        className: props.variant === 'compact' ? 'feed feed--compact' : 'feed',
        'aria-busy': state.empty ? undefined : false,
      }),
      slots: {
        header: {
          visible: ({ props }) => Boolean(props.title),
          props: ({ props }) => ({
            'aria-label': props.title ?? 'Activity feed',
          }),
        },
        list: {
          visible: ({ state }) => !state.empty,
          state: ({ state }) => ({
            virtualized: state.itemCount > 20,
          }),
          props: ({ state }) => ({
            'aria-setsize': state.itemCount,
          }),
        },
        empty: {
          visible: ({ state }) => state.empty,
        },
        footer: {
          visible: ({ state }) => !state.empty,
          props: ({ state }) => ({
            'data-count': state.itemCount,
          }),
        },
      },
      behaviors: [
        ({ props }) => (props.interactive ? createPressableBehavior() : undefined),
      ],
    });
    const feedPlan = feedRecipe.createRenderPlan({
      props: {
        interactive: true,
        title: 'Recent events',
        variant: 'compact',
        items: [
          { id: '1', label: 'One' },
          { id: '2', label: 'Two' },
        ],
      },
    });

    const telemetryListener = vi.fn();
    document.addEventListener('ghatana:component-event', telemetryListener);

    function Harness() {
      const recipe = useComponentRecipe(feedRecipe, {
        props: {
          interactive: true,
          title: 'Recent events',
          variant: 'compact',
          items: [
            { id: '1', label: 'One' },
            { id: '2', label: 'Two' },
          ],
        },
      });

      return (
        <section {...recipe.rootProps}>
          {recipe.plan.slots.header.visible ? (
            <header {...recipe.getSlotProps('header')}>Recent events</header>
          ) : null}
          {recipe.plan.slots.list.visible ? (
            <ul {...recipe.getSlotProps('list')}>
              {recipe.props.items?.map((item) => <li key={item.id}>{item.label}</li>)}
            </ul>
          ) : null}
          {recipe.plan.slots.empty.visible ? (
            <div {...recipe.getSlotProps('empty')}>Nothing here</div>
          ) : null}
          {recipe.plan.slots.footer.visible ? (
            <footer {...recipe.getSlotProps('footer')}>Footer</footer>
          ) : null}
        </section>
      );
    }

    render(<Harness />);

    const root = screen.getByRole('button');
    expect(root).toHaveAttribute('data-component', 'activity-feed');
    expect(root).toHaveAttribute('data-scope', 'activity-feed');
    expect(root).toHaveAttribute('data-privacy', 'internal');
    expect(root).toHaveAttribute('data-feature-collection', 'true');
    expect(root).toHaveAttribute('data-feature-interactive', 'true');
    expect(root).toHaveClass('feed--compact');

    expect(screen.getByText('Recent events')).toHaveAttribute('data-part', 'header');
    expect(screen.getByRole('list')).toHaveAttribute('data-slot', 'list');
    expect(screen.getByRole('list')).toHaveAttribute('aria-setsize', '2');
    expect(feedPlan.platform.capabilities).toMatchObject({
      interactive: true,
      collection: true,
    });
    expect(screen.queryByText('Nothing here')).not.toBeInTheDocument();

    fireEvent.keyDown(root, { key: 'Enter' });
    expect(telemetryListener).toHaveBeenCalled();

    document.removeEventListener('ghatana:component-event', telemetryListener);
  });

  it('emits a builder manifest from a compiled recipe', () => {
    const recipe = compileComponentRecipe<{
      interactive?: boolean;
    }>({
      name: 'manifest-button',
      platforms: ['react', 'web-components', 'swiftui'],
      metadata: {
        component: 'manifest-button',
        privacy: 'internal',
      },
      deriveFeatures: ({ props }) => [
        props.interactive ? 'interactive' : undefined,
      ],
      rootProps: {
        role: 'button',
      },
      slots: {
        icon: {},
        label: {},
      },
    });

    const manifest = createBuilderManifestFromRecipe(recipe);

    expect(manifest.name).toBe('manifest-button');
    expect(manifest.targets).toEqual(['react', 'web-components', 'swiftui']);
    expect(manifest.semantics.role).toBe('button');
    expect(manifest.capabilities.privacy).toBe('internal');
    expect(manifest.slots.map((slot) => slot.name)).toEqual(['icon', 'label']);
    expect(manifest.slots.every((slot) => slot.exposure === 'prop')).toBe(true);
  });
});
