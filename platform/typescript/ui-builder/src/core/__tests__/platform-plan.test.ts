import { describe, expect, it } from 'vitest';

import type { ComponentContract } from '@ghatana/ds-schema';
import {
  createDocumentId,
  createNodeId,
  projectDocumentToPlatformPlan,
  projectInstanceToPlatformPlan,
  type BuilderDocument,
  type ComponentInstance,
} from '../index';
import type { BuilderComponentManifest } from '@ghatana/ds-schema';

const buttonContract: ComponentContract = {
  name: 'Button',
  version: '1.0.0',
  metadata: {
    category: 'input',
    status: 'stable',
    platforms: ['web', 'ios', 'android'],
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

describe('@ghatana/ui-builder/core - Platform Plans', () => {
  it('projects a component instance into a platform-neutral plan', () => {
    const instance: ComponentInstance = {
      id: createNodeId(),
      contractName: 'Button',
      props: {
        id: 'save-btn',
        title: 'Save',
        className: 'primary',
        style: { padding: 12, backgroundColor: 'red' },
      },
      slots: {},
      bindings: [
        {
          id: 'binding-1',
          type: 'event',
          source: 'saveAction',
          target: 'onClick',
        },
      ],
      metadata: {
        dataClassification: 'internal',
      },
    };

    const plan = projectInstanceToPlatformPlan(instance, buttonContract);

    expect(plan.targets).toEqual(['react', 'html', 'web-components', 'swiftui', 'react-native', 'jetpack-compose']);
    expect(plan.features).toContain('interactive');
    expect(plan.features).toContain('keyboard-accessible');
    expect(plan.dataClassification).toBe('internal');
    expect(plan.props.className).toBe('primary');
    expect(plan.props.style).toMatchObject({ padding: 12, backgroundColor: 'red' });
    expect(plan.props.semantics).toMatchObject({
      role: 'button',
      title: 'Save',
      id: 'save-btn',
    });
    expect(plan.props.semantics.eventNames).toContain('click');
  });

  it('projects a whole document into a platform manifest', () => {
    const buttonId = createNodeId();
    const instance: ComponentInstance = {
      id: buttonId,
      contractName: 'Button',
      props: { children: 'Save' },
      slots: {},
      bindings: [],
      metadata: {},
    };

    const document: BuilderDocument = {
      id: createDocumentId(),
      version: '1',
      name: 'Test Document',
      designSystem: {
        id: 'test-ds',
        name: 'Test DS',
        version: '1.0.0',
        tokenSetIds: [],
        componentContracts: [buttonContract],
        themeId: 'default',
      },
      rootNodes: [buttonId],
      nodes: new Map([[buttonId, instance]]),
      metadata: {
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    };

    const plan = projectDocumentToPlatformPlan(document);

    expect(plan.targets).toContain('react');
    expect(plan.targets).toContain('swiftui');
    expect(plan.nodes.get(buttonId)?.contractName).toBe('Button');
  });

  it('prefers explicit component manifests over inferred platform metadata', () => {
    const instance: ComponentInstance = {
      id: createNodeId(),
      contractName: 'Button',
      props: { children: 'Save' },
      slots: {
        icon: [],
      },
      bindings: [],
      metadata: {},
    };

    const manifest: BuilderComponentManifest = {
      name: 'Button',
      version: '1.0.0',
      targets: ['react', 'swiftui'],
      features: ['manifest-driven', 'interactive'],
      semantics: {
        role: 'button',
        eventNames: ['click'],
      },
      slots: [
        {
          name: 'icon',
          allowsMultiple: false,
          required: false,
          features: ['decorative'],
          exposure: 'prop',
          semantics: {
            eventNames: [],
          },
        },
      ],
      capabilities: {
        interactive: true,
        collection: false,
        virtualizable: false,
        async: false,
        privacy: 'internal',
        optimizedFor: ['builder-handoff'],
      },
      dataClassification: 'internal',
      reviewRequired: true,
    };

    const plan = projectInstanceToPlatformPlan(instance, buttonContract, manifest);

    expect(plan.targets).toEqual(['react', 'swiftui']);
    expect(plan.features).toContain('manifest-driven');
    expect(plan.dataClassification).toBe('internal');
    expect(plan.reviewRequired).toBe(true);
    expect(plan.slots[0]?.allowsMultiple).toBe(false);
    expect(plan.slots[0]?.exposure).toBe('prop');
    expect(plan.slots[0]?.features).toEqual(['decorative']);
  });

  it('keeps declared slot intent even when a slot is currently empty', () => {
    const instance: ComponentInstance = {
      id: createNodeId(),
      contractName: 'Button',
      props: { children: 'Save' },
      slots: {},
      bindings: [],
      metadata: {},
    };

    const manifest: BuilderComponentManifest = {
      name: 'Button',
      version: '1.0.0',
      targets: ['react'],
      features: [],
      semantics: {
        role: 'button',
        eventNames: [],
      },
      slots: [
        {
          name: 'default',
          allowsMultiple: true,
          required: true,
          features: ['content'],
          exposure: 'children',
          semantics: {
            eventNames: [],
          },
        },
        {
          name: 'trailingAction',
          allowsMultiple: false,
          required: false,
          features: ['action'],
          exposure: 'prop',
          semantics: {
            eventNames: ['click'],
          },
        },
      ],
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

    const plan = projectInstanceToPlatformPlan(instance, buttonContract, manifest);
    expect(plan.slots.map((slot) => slot.name)).toEqual(['default', 'trailingAction']);
    expect(plan.slots[0]?.exposure).toBe('children');
    expect(plan.slots[1]?.childIds).toEqual([]);
    expect(plan.slots[1]?.semantics.eventNames).toEqual(['click']);
  });
});
