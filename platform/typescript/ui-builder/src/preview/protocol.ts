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
