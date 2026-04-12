/**
 * @fileoverview Compatibility gates for design system component contract versioning.
 */

import type { ComponentContract } from '@ghatana/ds-schema';

export type CompatibilityResult =
  | { readonly compatible: true }
  | { readonly compatible: false; readonly breakingChanges: readonly BreakingChange[] };

export interface BreakingChange {
  readonly type:
    | 'removed-prop'
    | 'type-changed'
    | 'required-added'
    | 'removed-slot'
    | 'removed-event';
  readonly path: string;
  readonly description: string;
}

/**
 * Checks if a new contract is backward compatible with the previous version.
 * Breaking changes are reported; additive changes are allowed.
 */
export function checkContractCompatibility(
  previous: ComponentContract,
  next: ComponentContract,
): CompatibilityResult {
  const breaking: BreakingChange[] = [];

  const prevPropsMap = new Map(previous.props.map((p) => [p.name, p]));
  const nextPropsMap = new Map(next.props.map((p) => [p.name, p]));

  // Detect removed props
  for (const [name] of prevPropsMap) {
    if (!nextPropsMap.has(name)) {
      breaking.push({
        type: 'removed-prop',
        path: `props.${name}`,
        description: `Prop "${name}" was removed`,
      });
    }
  }

  // Detect type changes and newly-required props
  for (const [name, nextProp] of nextPropsMap) {
    const prevProp = prevPropsMap.get(name);
    if (!prevProp) continue; // addition, not a break

    if (prevProp.type !== nextProp.type) {
      breaking.push({
        type: 'type-changed',
        path: `props.${name}`,
        description: `Prop "${name}" type changed from "${prevProp.type}" to "${nextProp.type}"`,
      });
    }

    if (!prevProp.required && nextProp.required) {
      breaking.push({
        type: 'required-added',
        path: `props.${name}`,
        description: `Prop "${name}" became required`,
      });
    }
  }

  // Detect removed slots
  const prevSlotNames = new Set(previous.slots.map((s) => s.name));
  for (const slot of next.slots) {
    prevSlotNames.delete(slot.name);
  }
  for (const removedSlot of prevSlotNames) {
    breaking.push({
      type: 'removed-slot',
      path: `slots.${removedSlot}`,
      description: `Slot "${removedSlot}" was removed`,
    });
  }

  // Detect removed events
  const prevEventNames = new Set(previous.events.map((e) => e.name));
  for (const event of next.events) {
    prevEventNames.delete(event.name);
  }
  for (const removedEvent of prevEventNames) {
    breaking.push({
      type: 'removed-event',
      path: `events.${removedEvent}`,
      description: `Event "${removedEvent}" was removed`,
    });
  }

  if (breaking.length === 0) {
    return { compatible: true };
  }

  return { compatible: false, breakingChanges: breaking };
}
