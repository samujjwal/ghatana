/**
 * @fileoverview React renderer for BuilderDocument components.
 *
 * Renders ComponentInstance nodes as React components with proper
 * prop binding, slot rendering, and event handling.
 *
 * @doc.type component
 * @doc.purpose React rendering of BuilderDocument instances
 * @doc.layer platform
 */

import React, { useMemo } from 'react';
import type { ComponentContract } from '@ghatana/ds-schema';
import type {
  ComponentInstance,
  BuilderDocument,
  NodeId,
  Binding,
} from '../core/types';
import {
  type ManifestLookup,
  projectInstanceToPlatformPlan,
  type BuilderPlatformSlotPlan,
  type ContractLookup,
} from '../core/platform-plan';

export interface ComponentRendererProps {
  /** The component instance to render */
  instance: ComponentInstance;
  /** The full document for resolving slot children */
  document: BuilderDocument;
  /** Data binding context for resolving bindings */
  bindingContext?: Record<string, unknown>;
  /** Event handler context for resolving event bindings */
  eventContext?: Record<string, (event: Event) => void>;
  /** Custom component registry mapping contract names to React components */
  componentRegistry?: Record<string, React.ComponentType<any>>;
  /** Optional contract lookup for platform-aware rendering metadata */
  contracts?: ContractLookup;
  /** Optional component manifest lookup from design-system recipes */
  manifests?: ManifestLookup;
  /** CSS class name to apply */
  className?: string;
}

/**
 * Renders a ComponentInstance as a React component.
 *
 * Uses the component registry to map contract names to actual React components.
 * Falls back to a generic div wrapper if no component is registered.
 */
export function ComponentRenderer({
  instance,
  document,
  bindingContext = {},
  eventContext = {},
  componentRegistry = {},
  contracts,
  manifests,
  className,
}: ComponentRendererProps): React.ReactElement {
  const contractMap = useMemo(() => {
    if (!contracts) {
      return new Map<string, ComponentContract>();
    }
    if (contracts instanceof Map) {
      return contracts;
    }
    if (Array.isArray(contracts)) {
      return new Map(contracts.map((contract) => [contract.name, contract]));
    }
    return new Map<string, ComponentContract>();
  }, [contracts]);

  // Resolve props with data bindings
  const resolvedProps = useMemo(() => {
    const props: Record<string, unknown> = { ...instance.props };

    for (const binding of instance.bindings) {
      if (binding.type === 'data') {
        const value = resolveDataBinding(binding, bindingContext);
        if (value !== undefined) {
          props[binding.target] = value;
        }
      }
    }

    if (className) {
      props.className = className;
    }

    return props;
  }, [instance.props, instance.bindings, bindingContext, className]);

  // Resolve event handlers
  const eventHandlers = useMemo(() => {
    const handlers: Record<string, (event: Event) => void> = {};

    for (const binding of instance.bindings) {
      if (binding.type === 'event') {
        const handler = eventContext[binding.source];
        if (handler) {
          handlers[binding.target] = handler;
        }
      }
    }

    return handlers;
  }, [instance.bindings, eventContext]);

  // Get the React component from registry
  const Component = componentRegistry[instance.contractName] || GenericComponent;
  const platformPlan = useMemo(
    () => {
      const manifestMap = manifests instanceof Map
        ? manifests
        : Array.isArray(manifests)
          ? new Map(manifests.map((manifest) => [manifest.name, manifest]))
          : new Map();
      return projectInstanceToPlatformPlan(
        instance,
        contractMap.get(instance.contractName),
        manifestMap.get(instance.contractName),
      );
    },
    [contractMap, instance, manifests]
  );

  // Render slot children
  const slotChildren = useMemo(() => {
    const children: Record<string, React.ReactNode> = {};

    for (const slotPlan of platformPlan.slots) {
      const renderedChildren = slotPlan.childIds.map((childId: NodeId) => {
        const child = document.nodes.get(childId);
        if (!child) return null;

        return (
          <ComponentRenderer
            key={childId}
            instance={child}
            document={document}
            bindingContext={bindingContext}
            eventContext={eventContext}
            componentRegistry={componentRegistry}
            contracts={contracts}
            manifests={manifests}
          />
        );
      });

      assignSlotContent(
        children,
        slotPlan,
        renderedChildren,
        resolvedProps.children as React.ReactNode,
      );
    }

    return children;
  }, [
    bindingContext,
    componentRegistry,
    contracts,
    document,
    eventContext,
    manifests,
    platformPlan.slots,
    resolvedProps.children,
  ]);

  return (
    <Component
      {...resolvedProps}
      {...eventHandlers}
      {...slotChildren}
      data-builder-id={instance.id}
      data-builder-contract={instance.contractName}
      data-builder-platforms={platformPlan.targets.join(' ')}
      data-builder-signature={platformPlan.signature}
      data-builder-features={platformPlan.features.join(' ')}
      data-builder-classification={platformPlan.dataClassification}
      data-builder-review-required={platformPlan.reviewRequired || undefined}
    />
  );
}

/**
 * Generic fallback component for unregistered contracts.
 */
function GenericComponent({ children, ...props }: any): React.ReactElement {
  return (
    <div
      {...props}
      data-builder-generic="true"
      className="border border-dashed border-gray-300 p-2 m-2"
    >
      {children}
      <span className="text-xs text-gray-500">
        Unregistered component: {props['data-builder-contract']}
      </span>
    </div>
  );
}

/**
 * Resolves a data binding to its value.
 */
function resolveDataBinding(
  binding: Binding,
  context: Record<string, unknown>,
): unknown {
  if (binding.type !== 'data') return undefined;

  const parts = binding.source.split('.');
  let value: unknown = context;

  for (const part of parts) {
    if (value && typeof value === 'object' && part in value) {
      value = (value as Record<string, unknown>)[part];
    } else {
      return undefined;
    }
  }

  return value;
}

function assignSlotContent(
  target: Record<string, React.ReactNode>,
  slotPlan: BuilderPlatformSlotPlan,
  children: React.ReactNode[],
  existingChildren?: React.ReactNode,
): void {
  const slotContent = collapseSlotChildren(children);
  if (slotPlan.exposure === 'children') {
    target.children = mergeChildren(existingChildren, slotContent);
    return;
  }

  if (slotContent !== undefined) {
    target[slotPlan.name] = slotContent;
  }
}

function collapseSlotChildren(children: React.ReactNode[]): React.ReactNode | undefined {
  const filteredChildren = children.filter((child) => child !== null && child !== undefined);
  if (filteredChildren.length === 0) return undefined;
  if (filteredChildren.length === 1) return filteredChildren[0];
  return <>{filteredChildren}</>;
}

function mergeChildren(
  existingChildren: React.ReactNode,
  slotChildren: React.ReactNode | undefined,
): React.ReactNode | undefined {
  if (existingChildren === undefined || existingChildren === null) {
    return slotChildren;
  }
  if (slotChildren === undefined) {
    return existingChildren;
  }
  return (
    <>
      {existingChildren}
      {slotChildren}
    </>
  );
}
