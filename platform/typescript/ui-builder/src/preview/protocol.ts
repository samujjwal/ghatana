/**
 * @fileoverview Typed preview host protocol and sandbox profile mapping.
 *
 * Defines the message protocol between the builder host frame and the preview
 * sandbox iframe, covering mount, update, teardown, and telemetry messages.
 */

import type { BuilderDocument } from '../core/types.js';

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
  | PingMessage;

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
  | PongMessage;

export interface ReadyMessage {
  readonly type: 'READY';
  readonly version: string;
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
}

export interface HoverMessage {
  readonly type: 'ELEMENT_HOVER';
  readonly nodeId: string | null;
}

export interface PongMessage {
  readonly type: 'PONG';
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
 */
export class PreviewHostService {
  private readonly messageHandlers = new Set<
    (message: PreviewToHostMessage) => void
  >();

  private readonly handleMessage = (event: MessageEvent): void => {
    if (event.source !== this.iframe.contentWindow) {
      return;
    }

    if (!event.data || typeof event.data !== 'object') {
      return;
    }

    const maybeMessage = event.data as Record<string, unknown>;
    if (typeof maybeMessage.type !== 'string') {
      return;
    }

    const message = maybeMessage as unknown as PreviewToHostMessage;

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
      default:
        break;
    }

    this.messageHandlers.forEach((handler) => {
      handler(message);
    });
  };

  public constructor(
    private readonly iframe: HTMLIFrameElement,
    private sandbox: SandboxProfile,
    private readonly callbacks: PreviewHostServiceCallbacks = {},
  ) {
    window.addEventListener('message', this.handleMessage);
  }

  public send(message: HostToPreviewMessage): void {
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

  public async mount(
    document: BuilderDocument,
    sandbox: SandboxProfile,
  ): Promise<void> {
    this.sandbox = sandbox;
    this.send({
      type: 'MOUNT_DOCUMENT',
      document,
      sandbox,
      correlationId: this.createCorrelationId(),
    });
  }

  public async mountDocument(document: BuilderDocument): Promise<void> {
    await this.mount(document, this.sandbox);
  }

  public async update(document: BuilderDocument): Promise<void> {
    this.send({
      type: 'UPDATE_DOCUMENT',
      document,
      correlationId: this.createCorrelationId(),
    });
  }

  public async updateDocument(document: BuilderDocument): Promise<void> {
    await this.update(document);
  }

  public async teardown(): Promise<void> {
    this.send({
      type: 'TEARDOWN',
      correlationId: this.createCorrelationId(),
    });
    this.messageHandlers.clear();
    window.removeEventListener('message', this.handleMessage);
  }

  private createCorrelationId(): string {
    return globalThis.crypto?.randomUUID?.() ?? `preview-${Date.now()}`;
  }
}
