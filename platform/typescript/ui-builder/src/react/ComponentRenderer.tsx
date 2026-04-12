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
import type {
  ComponentInstance,
  BuilderDocument,
  NodeId,
  Binding,
} from '../core/types';

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
  className,
}: ComponentRendererProps): React.ReactElement {
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

  // Render slot children
  const slotChildren = useMemo(() => {
    const children: Record<string, React.ReactNode> = {};

    for (const [slotName, childIds] of Object.entries(instance.slots)) {
      children[slotName] = childIds.map((childId: NodeId) => {
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
          />
        );
      });
    }

    return children;
  }, [instance.slots, document, bindingContext, eventContext, componentRegistry]);

  return (
    <Component
      {...resolvedProps}
      {...eventHandlers}
      {...slotChildren}
      data-builder-id={instance.id}
      data-builder-contract={instance.contractName}
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
