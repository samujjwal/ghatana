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
import type { PluginRuntimeEnvironment } from '../../../services/plugins/PluginRuntimePolicy';
import { assessComponentSafety } from '../../../security/UnsafeComponentHandler';

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
  /**
   * Active plugin runtime environment for sandboxed package renderers.
   * Present only when rendering a component loaded through ComponentPluginLoader.
   * Plugin renderers should use env.fetch / env.useStorage / env.useBrowserAPI
   * instead of browser globals to stay within their declared policy.
   */
  readonly pluginEnvironment?: PluginRuntimeEnvironment;
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
  /**
   * Optional TSX source code for the component. When provided, the registry
   * runs a security assessment before allowing registration. Manifests with
   * unsafe code (eval, Function, inline scripts, etc.) are rejected.
   */
  readonly sourceCode?: string;
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
   *
   * If the manifest carries `sourceCode`, a security assessment is performed
   * before registration. Components flagged as `unsafe` are rejected to
   * prevent dangerous APIs (eval, network exfiltration, etc.) from running
   * inside the canvas runtime.
   *
   * Built-in renderers and fallback renderers are exempt from this check
   * because they are authored within the trusted application bundle.
   */
  register(manifest: BuilderRendererManifest): void {
    if (!manifest.isFallback && manifest.sourceCode) {
      const assessment = assessComponentSafety(
        manifest.sourceCode,
        manifest.contractName,
      );
      if (assessment.recommendedAction === 'block') {
        throw new Error(
          `Component '${manifest.contractName}' was blocked by the security assessment. ` +
            `Risk factors: ${assessment.riskFactors.join(', ')}`,
        );
      }
    }
    if (manifest.isFallback) {
      this.fallbackRenderer = manifest;
    } else {
      this.manifests.set(manifest.contractName, manifest);
    }
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
   * Convenience wrapper around `register()` with `isFallback: true`.
   */
  setFallbackRenderer(manifest: BuilderRendererManifest): void {
    this.register({ ...manifest, isFallback: true });
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
    const metadata = instance.metadata as Record<string, unknown>;
    const sourceLocation = typeof metadata.sourceLocation === 'string' ? metadata.sourceLocation : null;
    const residualReason = typeof metadata.residualReason === 'string'
      ? metadata.residualReason
      : 'No registered renderer exists for this component contract.';
    const confidence = typeof metadata.confidence === 'number' ? metadata.confidence : null;
    const suggestedContractName = typeof metadata.suggestedContractName === 'string'
      ? metadata.suggestedContractName
      : null;
    
    return (
      <div
        style={{
          padding: 16,
          border: '2px dashed var(--color-destructive-border, #ef4444)',
          borderRadius: 8,
          backgroundColor: 'var(--color-destructive-bg, #fef2f2)',
          color: 'var(--color-destructive, #991b1b)',
        }}
        data-testid={`fallback-renderer-${instance.contractName}`}
        role="group"
        aria-label={`Review unknown component ${instance.contractName}`}
      >
        <div style={{ fontWeight: 600, marginBottom: 8 }}>
          {isReviewRequired ? 'Review required: ' : 'Unknown component: '}
          {instance.contractName}
        </div>
        <div style={{ fontSize: 12, color: 'var(--color-destructive, #7f1d1d)' }}>
          {residualReason}
        </div>
        {sourceLocation ? (
          <div style={{ fontSize: 12, marginTop: 6 }}>
            Source: {sourceLocation}
          </div>
        ) : null}
        {confidence !== null ? (
          <div style={{ fontSize: 12, marginTop: 6 }}>
            Confidence: {Math.round(confidence * 100)}%
          </div>
        ) : null}
        {suggestedContractName ? (
          <div style={{ fontSize: 12, marginTop: 6 }}>
            Suggested registry contract: {suggestedContractName}
          </div>
        ) : null}
        <div style={{ fontSize: 11, marginTop: 8, opacity: 0.8 }}>
          Props: {JSON.stringify(Object.keys(instance.props), null, 2)}
        </div>
        <div style={{ fontSize: 11, marginTop: 8, fontWeight: 600 }}>
          Action needed: register a renderer, map to a known contract, or mark this residual island as intentionally unsupported.
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
