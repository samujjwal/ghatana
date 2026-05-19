/**
 * @fileoverview Typed preview host protocol and sandbox profile mapping.
 *
 * Defines the message protocol between the builder host frame and the preview
 * sandbox iframe, covering mount, update, teardown, and telemetry messages.
 */

import type { BuilderDocument } from '../core/builder-document.js';
import { createSafeMessageHandler } from './trust.js';

// ============================================================================
// Sandbox Profiles
// ============================================================================

/** Describes an isolated sandbox environment for preview rendering. */
export interface SandboxProfile {
  readonly id: string;
  readonly name: string;
  readonly viewport: Viewport;
  readonly theme: string;
  readonly locale: string;
  readonly featureFlags: Readonly<Record<string, boolean>>;
  readonly trustedOrigins: readonly string[];
}

export interface Viewport {
  readonly width: number;
  readonly height: number;
  readonly devicePixelRatio: number;
  readonly label: string;
}

export const PRESET_VIEWPORTS = {
  mobile: { width: 375, height: 812, devicePixelRatio: 3, label: 'Mobile (375px)' },
  tablet: { width: 768, height: 1024, devicePixelRatio: 2, label: 'Tablet (768px)' },
  desktop: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop (1440px)' },
  'desktop-xl': { width: 1920, height: 1080, devicePixelRatio: 1, label: 'Desktop XL (1920px)' },
} as const satisfies Record<string, Viewport>;

export type PresetViewportKey = keyof typeof PRESET_VIEWPORTS;

/** Creates a sandbox profile with sensible defaults. */
export function createSandboxProfile(
  overrides: Partial<SandboxProfile> & { id: string; name: string },
): SandboxProfile {
  return {
    viewport: PRESET_VIEWPORTS.desktop,
    theme: 'default',
    locale: 'en-US',
    featureFlags: {},
    trustedOrigins: [],
    ...overrides,
  };
}

// ============================================================================
// Host → Preview Messages
// ============================================================================

/** Discriminated union of all messages sent from host to preview iframe. */
export type HostToPreviewMessage =
  | MountDocumentMessage
  | UpdateDocumentMessage
  | TeardownMessage
  | SetViewportMessage
  | SetThemeMessage
  | SetLocaleMessage
  | PingMessage
  | SelectNodeMessage;

export interface MountDocumentMessage {
  readonly type: 'MOUNT_DOCUMENT';
  readonly document: BuilderDocument;
  readonly sandbox: SandboxProfile;
  readonly correlationId: string;
}

export interface UpdateDocumentMessage {
  readonly type: 'UPDATE_DOCUMENT';
  readonly document: BuilderDocument;
  readonly correlationId: string;
}

export interface TeardownMessage {
  readonly type: 'TEARDOWN';
  readonly correlationId: string;
}

export interface SetViewportMessage {
  readonly type: 'SET_VIEWPORT';
  readonly viewport: Viewport;
  readonly correlationId: string;
}

export interface SetThemeMessage {
  readonly type: 'SET_THEME';
  readonly theme: string;
  readonly correlationId: string;
}

export interface SetLocaleMessage {
  readonly type: 'SET_LOCALE';
  readonly locale: string;
  readonly correlationId: string;
}

export interface PingMessage {
  readonly type: 'PING';
  readonly correlationId: string;
}

export interface SelectNodeMessage {
  readonly type: 'SELECT_NODE';
  /** The node ID to highlight in the preview, or null to clear the highlight. */
  readonly nodeId: string | null;
}

// ============================================================================
// Preview → Host Messages
// ============================================================================

/** Discriminated union of all messages sent from preview iframe to host. */
export type PreviewToHostMessage =
  | ReadyMessage
  | MountedMessage
  | UpdatedMessage
  | ErrorMessage
  | ClickMessage
  | HoverMessage
  | PongMessage
  | TeardownAckMessage;

export interface ReadyMessage {
  readonly type: 'READY';
  readonly version: string;
  readonly correlationId?: string;
}

export interface MountedMessage {
  readonly type: 'MOUNTED';
  readonly correlationId: string;
  readonly durationMs: number;
}

export interface UpdatedMessage {
  readonly type: 'UPDATED';
  readonly correlationId: string;
  readonly durationMs: number;
}

export interface ErrorMessage {
  readonly type: 'ERROR';
  readonly correlationId: string;
  readonly code: string;
  readonly message: string;
  readonly stack?: string;
}

export interface ClickMessage {
  readonly type: 'ELEMENT_CLICK';
  readonly nodeId: string;
  readonly coordinates: { readonly x: number; readonly y: number };
  readonly correlationId?: string;
}

export interface HoverMessage {
  readonly type: 'ELEMENT_HOVER';
  readonly nodeId: string | null;
  readonly correlationId?: string;
}

export interface PongMessage {
  readonly type: 'PONG';
  readonly correlationId: string;
}

export interface TeardownAckMessage {
  readonly type: 'TEARDOWN_ACK';
  readonly correlationId: string;
}

// ============================================================================
// Host Service Interface
// ============================================================================

export interface PreviewHostServiceCallbacks {
  readonly onReady?: (message: ReadyMessage) => void;
  readonly onMounted?: (message: MountedMessage) => void;
  readonly onUpdated?: (message: UpdatedMessage) => void;
  readonly onError?: (message: ErrorMessage) => void;
  /** Fired when the user clicks an element in the preview iframe. */
  readonly onElementClick?: (message: ClickMessage) => void;
  /** Fired when the user hovers over an element in the preview iframe (null = hover cleared). */
  readonly onElementHover?: (message: HoverMessage) => void;
}

/** Interface a builder host must implement to manage a preview iframe. */
export interface PreviewHostService {
  /** Send a message to the preview frame. */
  send(message: HostToPreviewMessage): void;

  /** Subscribe to messages coming from the preview frame. */
  onMessage(handler: (message: PreviewToHostMessage) => void): () => void;

  /** Mount a document into the sandbox. Returns a promise resolving when mounted. */
  mount(document: BuilderDocument, sandbox: SandboxProfile): Promise<void>;

  /** Push a document update without full remount. */
  update(document: BuilderDocument): Promise<void>;

  /** Tear down and release the sandbox. */
  teardown(): Promise<void>;
}

/**
 * Runtime implementation of the typed preview host service used by builder consumers.
 *
 * IMPORTANT: This implementation is acknowledgement-based. mount(), update(), and teardown()
 * wait for corresponding MOUNTED/UPDATED/TEARDOWN_ACK messages with matching correlationIds
 * before resolving. This ensures reliable message delivery and proper sequencing.
 */
export class PreviewHostService {
  private readonly messageHandlers = new Set<
    (message: PreviewToHostMessage) => void
  >();

  private readonly pendingAcknowledgements = new Map<
    string,
    { resolve: (value: void) => void; reject: (error: Error) => void }
  >();

  private handleMessage = (event: MessageEvent): void => {
    this.dispatchPreviewMessage(event);
  };

  private static readonly ACKNOWLEDGEMENT_TIMEOUT_MS = 5000;

  public constructor(
    private readonly iframe: HTMLIFrameElement,
    private sandbox: SandboxProfile,
    private readonly callbacks: PreviewHostServiceCallbacks = {},
  ) {
    this.handleMessage = (event: MessageEvent): void => {
      this.dispatchPreviewMessage(event);
    };
    window.addEventListener('message', this.handleMessage);
  }

  public send(message: HostToPreviewMessage): void {
    // Sandboxed srcdoc/about:blank previews do not expose an origin in jsdom
    // and local development. Prefer configured origins, but keep the legacy
    // wildcard fallback for source-checked preview frames.
    const targetOrigin = this.sandbox.trustedOrigins[0] ?? '*';
    this.iframe.contentWindow?.postMessage(message, targetOrigin);
  }

  public onMessage(
    handler: (message: PreviewToHostMessage) => void,
  ): () => void {
    this.messageHandlers.add(handler);
    return () => {
      this.messageHandlers.delete(handler);
    };
  }

  private dispatchPreviewMessage(event: MessageEvent): void {
    if (event.source !== this.iframe.contentWindow) {
      return;
    }

    // SECURITY: Only accept messages from configured trusted origins
    if (event.origin && !this.sandbox.trustedOrigins.includes(event.origin)) {
      console.error(`Preview message rejected: origin ${event.origin} not in trusted origins`);
      return;
    }

    const safeHandler = createSafeMessageHandler(this.sandbox, (message) => {
      // Handle acknowledgements for pending requests
      const correlationId = message.correlationId;
      if (correlationId) {
        const pending = this.pendingAcknowledgements.get(correlationId);
        if (pending) {
          switch (message.type) {
            case 'MOUNTED':
            case 'UPDATED':
            case 'TEARDOWN_ACK':
              this.pendingAcknowledgements.delete(correlationId);
              pending.resolve();
              break;
            case 'ERROR':
              this.pendingAcknowledgements.delete(correlationId);
              pending.reject(
                new Error(`Preview error [${message.code}]: ${message.message}`),
              );
              break;
          }
        }
      }

      // Dispatch to callbacks
      switch (message.type) {
        case 'READY':
          this.callbacks.onReady?.(message);
          break;
        case 'MOUNTED':
          this.callbacks.onMounted?.(message);
          break;
        case 'UPDATED':
          this.callbacks.onUpdated?.(message);
          break;
        case 'ERROR':
          this.callbacks.onError?.(message);
          break;
        case 'ELEMENT_CLICK':
          this.callbacks.onElementClick?.(message);
          break;
        case 'ELEMENT_HOVER':
          this.callbacks.onElementHover?.(message);
          break;
        default:
          break;
      }

      this.messageHandlers.forEach((handler) => {
        handler(message);
      });
    });

    safeHandler(event);
  }

  public async mount(
    document: BuilderDocument,
    sandbox: SandboxProfile,
  ): Promise<void> {
    this.sandbox = sandbox;
    window.removeEventListener('message', this.handleMessage);
    this.handleMessage = (event: MessageEvent): void => {
      this.dispatchPreviewMessage(event);
    };
    window.addEventListener('message', this.handleMessage);

    const correlationId = this.createCorrelationId();
    this.send({
      type: 'MOUNT_DOCUMENT',
      document,
      sandbox,
      correlationId,
    });

    // Await acknowledgement from preview frame
    await this.waitForAcknowledgement(correlationId);
  }

  public async mountDocument(document: BuilderDocument): Promise<void> {
    await this.mount(document, this.sandbox);
  }

  public async update(document: BuilderDocument): Promise<void> {
    const correlationId = this.createCorrelationId();

    this.send({
      type: 'UPDATE_DOCUMENT',
      document,
      correlationId,
    });

    // Await acknowledgement from preview frame
    await this.waitForAcknowledgement(correlationId);
  }

  public async updateDocument(document: BuilderDocument): Promise<void> {
    await this.update(document);
  }

  public async teardown(): Promise<void> {
    const correlationId = this.createCorrelationId();

    this.send({
      type: 'TEARDOWN',
      correlationId,
    });

    // Await acknowledgement from preview frame
    await this.waitForAcknowledgement(correlationId);

    this.messageHandlers.clear();
    window.removeEventListener('message', this.handleMessage);
  }

  private async waitForAcknowledgement(
    correlationId: string,
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pendingAcknowledgements.delete(correlationId);
        reject(
          new Error(
            `Preview acknowledgement timeout for correlation ID: ${correlationId}`,
          ),
        );
      }, PreviewHostService.ACKNOWLEDGEMENT_TIMEOUT_MS);

      this.pendingAcknowledgements.set(correlationId, {
        resolve: () => {
          clearTimeout(timeout);
          resolve();
        },
        reject: (error: Error) => {
          clearTimeout(timeout);
          reject(error);
        },
      });
    });
  }

  private createCorrelationId(): string {
    return globalThis.crypto?.randomUUID?.() ?? `preview-${Date.now()}`;
  }
}

// ============================================================================
// Preview Request / Result Protocol
// ============================================================================

/**
 * A reference to an artifact produced during a preview render or export pass.
 * Artifacts are immutable once produced and are identified by a stable ID.
 */
export interface PreviewArtifactReference {
  /** Stable identifier for this artifact. */
  readonly id: string;
  /** The binary kind of the artifact. */
  readonly kind: 'image' | 'pdf' | 'html' | 'json';
  /** URL where the artifact can be fetched (may be a data: URI or object URL). */
  readonly url: string;
  /** MIME content-type of the artifact. */
  readonly contentType: string;
  /** Byte size of the artifact, if known. */
  readonly size?: number;
  /** ISO-8601 timestamp when this artifact was generated. */
  readonly generatedAt: string;
}

/**
 * The outcome of a security policy evaluation for a preview request.
 *
 * - `allow`   – the document may be rendered with full capabilities.
 * - `deny`    – the document is rejected; `violatedPolicies` lists the reasons.
 * - `sandbox` – the document may be rendered under additional `restrictions`.
 */
export type PreviewSecurityDecision =
  | {
      readonly decision: 'allow';
      readonly policyId: string;
      readonly reason?: string;
    }
  | {
      readonly decision: 'deny';
      readonly reason: string;
      readonly violatedPolicies: readonly string[];
    }
  | {
      readonly decision: 'sandbox';
      readonly restrictions: readonly string[];
      readonly policyId: string;
    };

/**
 * Operational diagnostics collected during a single preview render cycle.
 * Used by the builder host to surface performance and correctness signals.
 */
export interface PreviewDiagnostics {
  /** Wall-clock render time in milliseconds. */
  readonly renderTimeMs: number;
  /** Total number of component nodes in the rendered document. */
  readonly nodeCount: number;
  /** Total number of active bindings resolved during render. */
  readonly bindingCount: number;
  /** Non-fatal warnings emitted by the preview runtime. */
  readonly warnings: readonly string[];
  /** Errors emitted during rendering (present even when `success: true` for partial renders). */
  readonly errors: readonly string[];
}

/**
 * A request sent from the builder host to the preview runtime to initiate
 * a render or export pass.
 */
export interface PreviewRequest {
  /** Stable correlation ID linking this request to its {@link PreviewResult}. */
  readonly requestId: string;
  /** The document ID to preview. */
  readonly documentId: string;
  /** Sandbox profile that controls the rendering environment. */
  readonly profile: SandboxProfile;
  /** The intent of this preview pass. */
  readonly mode: 'preview' | 'inspection' | 'export';
  /** Target output format (for `export` mode). */
  readonly format?: 'web' | 'print' | 'mobile';
  /** Requested export artifact kind (for `export` mode). */
  readonly exportTarget?: 'image' | 'pdf' | 'html';
}

/**
 * The result returned by the preview runtime after completing a {@link PreviewRequest}.
 */
export interface PreviewResult {
  /** Matches {@link PreviewRequest.requestId}. */
  readonly requestId: string;
  /** The document ID that was rendered. */
  readonly documentId: string;
  /** Whether the render completed without fatal errors. */
  readonly success: boolean;
  /** Artifacts produced during the pass (non-empty for `export` mode). */
  readonly artifacts: readonly PreviewArtifactReference[];
  /** Result of the security policy evaluation for this request. */
  readonly security: PreviewSecurityDecision;
  /** Runtime diagnostics for this render cycle. */
  readonly diagnostics: PreviewDiagnostics;
  /** ISO-8601 timestamp when this result was produced. */
  readonly renderedAt: string;
}
