import { describe, expect, it } from 'vitest';

import {
  compileComponentRecipe,
  createPlatformRenderPlan,
  serializePrimitiveProps,
} from '../core';

describe('platform render plans', () => {
  it('serializes primitive props into platform-neutral semantics', () => {
    const serialized = serializePrimitiveProps({
      id: 'policy-card-1',
      role: 'button',
      tabIndex: 0,
      title: 'Policy card',
      className: 'card card--interactive',
      style: {
        padding: 16,
        backgroundColor: 'red',
      },
      'data-component': 'policy-card',
      'aria-label': 'Policy card',
      onClick: () => undefined,
      onMouseEnter: () => undefined,
    });

    expect(serialized.className).toBe('card card--interactive');
    expect(serialized.attributes).toMatchObject({
      id: 'policy-card-1',
      role: 'button',
      tabIndex: 0,
      title: 'Policy card',
      'data-component': 'policy-card',
      'aria-label': 'Policy card',
    });
    expect(serialized.style).toMatchObject({
      padding: 16,
      backgroundColor: 'red',
    });
    expect(serialized.semantics).toMatchObject({
      role: 'button',
      tabIndex: 0,
      title: 'Policy card',
      id: 'policy-card-1',
    });
    expect(serialized.semantics.eventNames).toEqual(['click', 'mouseenter']);
  });

  it('creates a platform render plan with inferred capabilities', () => {
    const platform = createPlatformRenderPlan({
      metadata: {
        component: 'activity-feed',
        privacy: 'internal',
      },
      state: {
        loading: false,
        itemCount: 40,
      },
      features: ['collection', 'interactive'],
      signature: 'activity-feed|collection',
      targets: ['react', 'swiftui'],
      rootProps: {
        role: 'button',
        onClick: () => undefined,
        'data-component': 'activity-feed',
      },
      slots: {
        list: {
          visible: true,
          state: { virtualized: true },
          props: {
            'data-slot': 'list',
            'aria-setsize': 40,
          },
        },
      },
    });

    expect(platform.targets).toEqual(['react', 'swiftui']);
    expect(platform.capabilities).toMatchObject({
      interactive: true,
      collection: true,
      virtualizable: true,
      async: false,
      privacy: 'internal',
    });
    expect(platform.root.semantics.eventNames).toEqual(['click']);
    expect(platform.slots.list.attributes).toMatchObject({
      'data-slot': 'list',
      'aria-setsize': 40,
    });
  });

  it('propagates target and capability hints from compiled recipes', () => {
    const recipe = compileComponentRecipe<{
      interactive?: boolean;
      items?: string[];
    }>({
      name: 'cross-platform-list',
      platforms: ['react', 'web-components', 'swiftui'],
      deriveState: (props) => ({
        interactive: Boolean(props.interactive),
        itemCount: props.items?.length ?? 0,
      }),
      deriveFeatures: ({ props }) => [
        'collection',
        props.interactive ? 'interactive' : undefined,
      ],
      platformHints: ({ state }) => ({
        virtualizable: typeof state.itemCount === 'number' && state.itemCount > 25,
      }),
      slots: {
        list: {
          props: ({ state }) => ({
            'aria-setsize': typeof state.itemCount === 'number' ? state.itemCount : 0,
          }),
        },
      },
    });

    const plan = recipe.createRenderPlan({
      props: {
        interactive: true,
        items: new Array(30).fill(null).map((_, index) => `Item ${index + 1}`),
      },
    });

    expect(recipe.platforms).toEqual(['react', 'web-components', 'swiftui']);
    expect(plan.platform.targets).toEqual(['react', 'web-components', 'swiftui']);
    expect(plan.platform.capabilities).toMatchObject({
      interactive: true,
      collection: true,
      virtualizable: true,
    });
    expect(plan.platform.root.attributes['data-component']).toBe('cross-platform-list');
  });
});
