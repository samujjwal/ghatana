import * as React from 'react';
import { cn } from '@ghatana/platform-utils';

export type PrimitivePrivacyLevel =
  | 'public'
  | 'internal'
  | 'confidential'
  | 'restricted'
  | 'pii'
  | 'sensitive';

export interface PrimitiveMetadata {
  component: string;
  slot?: string;
  variant?: string;
  state?: string;
  tone?: string;
  privacy?: PrimitivePrivacyLevel;
  observabilityId?: string;
}

export interface PrimitiveTelemetryOptions {
  metadata: PrimitiveMetadata;
  enabled?: boolean;
  target?: EventTarget | null;
}

type EventHandler = ((event: unknown) => void) | undefined;

export type PrimitiveProps = React.HTMLAttributes<HTMLElement> & {
  'data-testid'?: string;
};

const SAFE_TOKEN_PATTERN = /^[a-z0-9:_-]+$/i;

export function sanitizePrimitiveToken(value?: string): string | undefined {
  if (!value) return undefined;
  const normalized = value.trim().toLowerCase().replace(/\s+/g, '-');
  if (!normalized || !SAFE_TOKEN_PATTERN.test(normalized)) return undefined;
  return normalized;
}

function sanitizeTelemetryValue(value: unknown): string | number | boolean | null {
  if (value === null) return null;
  if (typeof value === 'boolean' || typeof value === 'number') return value;
  if (typeof value === 'string') {
    const normalized = sanitizePrimitiveToken(value);
    return normalized ?? '[redacted]';
  }
  return '[redacted]';
}

export function createPrimitiveAttributes(metadata: PrimitiveMetadata): Record<string, string> {
  const attrs: Record<string, string> = {};

  const component = sanitizePrimitiveToken(metadata.component);
  if (!component) {
    throw new Error('Primitive metadata requires a safe component name.');
  }

  attrs['data-component'] = component;
  attrs['data-scope'] = component;

  const slot = sanitizePrimitiveToken(metadata.slot);
  if (slot) {
    attrs['data-slot'] = slot;
    attrs['data-part'] = slot;
  }

  const variant = sanitizePrimitiveToken(metadata.variant);
  if (variant) attrs['data-variant'] = variant;

  const state = sanitizePrimitiveToken(metadata.state);
  if (state) attrs['data-state'] = state;

  const tone = sanitizePrimitiveToken(metadata.tone);
  if (tone) attrs['data-tone'] = tone;

  const privacy = sanitizePrimitiveToken(metadata.privacy);
  if (privacy) attrs['data-privacy'] = privacy;

  const observabilityId = sanitizePrimitiveToken(metadata.observabilityId);
  if (observabilityId) attrs['data-o11y-id'] = observabilityId;

  return attrs;
}

function composeEventHandlers(ours?: EventHandler, theirs?: EventHandler) {
  if (!ours) return theirs;
  if (!theirs) return ours;

  return (event: unknown) => {
    ours(event);
    theirs(event);
  };
}

function mergeDescribedBy(values: Array<string | undefined>): string | undefined {
  const tokens = values
    .flatMap((value) => (value ? value.split(/\s+/) : []))
    .map((token) => token.trim())
    .filter(Boolean);

  if (tokens.length === 0) return undefined;
  return Array.from(new Set(tokens)).join(' ');
}

export function mergePrimitiveProps<T extends PrimitiveProps>(
  ...sources: Array<T | undefined>
): T {
  const result: Record<string, unknown> = {};

  for (const source of sources) {
    if (!source) continue;

    for (const [key, value] of Object.entries(source)) {
      if (value === undefined) continue;

      if (key === 'className') {
        result.className = cn(result.className as string | undefined, value as string | undefined);
        continue;
      }

      if (key === 'style') {
        result.style = {
          ...(result.style as React.CSSProperties | undefined),
          ...(value as React.CSSProperties),
        };
        continue;
      }

      if (key === 'aria-describedby') {
        result['aria-describedby'] = mergeDescribedBy([
          result['aria-describedby'] as string | undefined,
          value as string | undefined,
        ]);
        continue;
      }

      if (/^on[A-Z]/.test(key) && typeof value === 'function') {
        result[key] = composeEventHandlers(result[key] as EventHandler, value as EventHandler);
        continue;
      }

      result[key] = value;
    }
  }

  return result as T;
}

export function createSlotProps<T extends PrimitiveProps>(
  metadata: PrimitiveMetadata,
  baseProps?: T,
  overrideProps?: T
): T {
  return mergePrimitiveProps(
    createPrimitiveAttributes(metadata) as unknown as T,
    baseProps,
    overrideProps
  );
}

export function useComponentTelemetry({
  metadata,
  enabled = true,
  target,
}: PrimitiveTelemetryOptions) {
  return React.useCallback((eventName: string, detail: Record<string, unknown> = {}) => {
    if (!enabled || typeof window === 'undefined' || typeof CustomEvent === 'undefined') {
      return;
    }

    const safeEventName = sanitizePrimitiveToken(eventName);
    if (!safeEventName) return;

    const safeDetail = Object.fromEntries(
      Object.entries(detail).map(([key, value]) => [key, sanitizeTelemetryValue(value)])
    );

    const event = new CustomEvent('ghatana:component-event', {
      detail: {
        event: safeEventName,
        metadata: createPrimitiveAttributes(metadata),
        payload: safeDetail,
      },
    });

    (target ?? document).dispatchEvent(event);
  }, [enabled, metadata, target]);
}
