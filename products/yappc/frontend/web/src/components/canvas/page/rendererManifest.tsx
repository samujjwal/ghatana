/**
 * Renderer Manifest System
 *
 * Defines the contract for component renderers, allowing dynamic registration
 * of new components without editing ComponentRenderer.tsx.
 *
 * @doc.type module
 * @doc.purpose Renderer manifest system for extensible component rendering
 * @doc.layer product
 * @doc.pattern Manifest Registry
 */

import React from 'react';
import type { ComponentInstance } from '@ghatana/ui-builder';
import type { ReactNode } from 'react';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

/**
 * Slot bag for rendering children in named slots.
 */
export interface SlotBag {
  readonly default: ReactNode;
  readonly actions: ReactNode;
}

/**
 * Render context for component rendering.
 */
export interface RenderContext {
  readonly mode: 'canvas' | 'preview';
  readonly selectedNodeId?: string | null;
}

/**
 * Preview policy for controlling component behavior in preview mode.
 */
export interface PreviewPolicy {
  readonly allowInteractive?: boolean;
  readonly allowDataBinding?: boolean;
  readonly requireReview?: boolean;
}

/**
 * Prop adapter for transforming props before rendering.
 */
export interface PropAdapter {
  readonly transform: (value: unknown, instance: ComponentInstance) => unknown;
  readonly validate?: (value: unknown) => boolean;
}

/**
 * Renderer manifest for a component contract.
 */
export interface BuilderRendererManifest {
  /** The contract name this manifest handles */
  readonly contractName: string;
  /** Render function for the component */
  readonly render: (
    instance: ComponentInstance,
    slots: SlotBag,
    context: RenderContext
  ) => ReactNode;
  /** Optional prop adapters for transforming props */
  readonly propAdapters?: Record<string, PropAdapter>;
  /** Optional preview policy */
  readonly previewPolicy?: PreviewPolicy;
  /** Whether this is a fallback renderer for unknown components */
  readonly isFallback?: boolean;
}

// ---------------------------------------------------------------------------
// Registry
// ---------------------------------------------------------------------------

/**
 * Global registry of renderer manifests.
 */
class RendererManifestRegistry {
  private readonly manifests = new Map<string, BuilderRendererManifest>();
  private fallbackRenderer: BuilderRendererManifest | null = null;

  /**
   * Registers a renderer manifest.
   */
  register(manifest: BuilderRendererManifest): void {
    this.manifests.set(manifest.contractName, manifest);
  }

  /**
   * Gets a renderer manifest by contract name.
   */
  get(contractName: string): BuilderRendererManifest | null {
    return this.manifests.get(contractName) ?? null;
  }

  /**
   * Gets all registered contract names.
   */
  getRegisteredContractNames(): ReadonlySet<string> {
    return new Set(this.manifests.keys());
  }

  /**
   * Sets a fallback renderer for unknown components.
   */
  setFallbackRenderer(manifest: BuilderRendererManifest): void {
    this.fallbackRenderer = manifest;
  }

  /**
   * Gets the fallback renderer.
   */
  getFallbackRenderer(): BuilderRendererManifest | null {
    return this.fallbackRenderer;
  }

  /**
   * Unregisters a renderer manifest.
   */
  unregister(contractName: string): void {
    this.manifests.delete(contractName);
  }

  /**
   * Clears all registered manifests.
   */
  clear(): void {
    this.manifests.clear();
    this.fallbackRenderer = null;
  }
}

// Export singleton instance
export const rendererManifestRegistry = new RendererManifestRegistry();

// ---------------------------------------------------------------------------
// Built-in Renderers
// ---------------------------------------------------------------------------

/**
 * Default fallback renderer for unknown components.
 */
export const createFallbackRenderer = (): BuilderRendererManifest => ({
  contractName: '__fallback__',
  isFallback: true,
  render: (instance, slots, context) => {
    const isReviewRequired = context.mode === 'preview';
    
    return (
      <div
        style={{
          padding: 16,
          border: '2px dashed #ef4444',
          borderRadius: 8,
          backgroundColor: '#fef2f2',
          color: '#991b1b',
        }}
        data-testid={`fallback-renderer-${instance.contractName}`}
      >
        <div style={{ fontWeight: 600, marginBottom: 8 }}>
          {isReviewRequired ? 'Review required: ' : 'Unknown component: '}
          {instance.contractName}
        </div>
        {isReviewRequired && (
          <div style={{ fontSize: 12, color: '#7f1d1d' }}>
            This component needs a registered renderer or custom implementation.
          </div>
        )}
        <div style={{ fontSize: 11, marginTop: 8, opacity: 0.8 }}>
          Props: {JSON.stringify(Object.keys(instance.props), null, 2)}
        </div>
        {slots.default}
      </div>
    );
  },
  previewPolicy: {
    allowInteractive: false,
    requireReview: true,
  },
});

// Initialize with fallback renderer
rendererManifestRegistry.setFallbackRenderer(createFallbackRenderer());
