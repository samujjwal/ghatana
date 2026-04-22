/**
 * @fileoverview Shared builder preview test harnesses.
 *
 * Provides mock factories and fixtures for testing code that consumes the
 * `@ghatana/ui-builder` preview protocol without requiring a real iframe or
 * browser sandbox environment.
 *
 * All types are defined structurally here so this package does not carry a
 * hard runtime dependency on `@ghatana/ui-builder`.
 */

import { vi } from 'vitest';

// ============================================================================
// Structural types (mirrors @ghatana/ui-builder/preview without importing it)
// ============================================================================

/** Minimal viewport shape matching @ghatana/ui-builder SandboxProfile.viewport. */
export interface PreviewViewport {
  readonly width: number;
  readonly height: number;
  readonly devicePixelRatio: number;
  readonly label: string;
}

/** Minimal SandboxProfile shape for testing. */
export interface PreviewSandboxProfile {
  readonly id: string;
  readonly name: string;
  readonly viewport: PreviewViewport;
  readonly theme: string;
  readonly locale: string;
  readonly featureFlags: Readonly<Record<string, boolean>>;
  readonly trustedOrigins: readonly string[];
}

/** Discriminated union covering the message types sent from host to preview. */
export type MockHostToPreviewMessage =
  | { readonly type: 'MOUNT_DOCUMENT'; readonly document: unknown; readonly sandbox: PreviewSandboxProfile; readonly correlationId: string }
  | { readonly type: 'UPDATE_DOCUMENT'; readonly document: unknown; readonly correlationId: string }
  | { readonly type: 'TEARDOWN'; readonly correlationId: string }
  | { readonly type: 'SET_VIEWPORT'; readonly viewport: PreviewViewport; readonly correlationId: string }
  | { readonly type: 'SET_THEME'; readonly theme: string; readonly correlationId: string }
  | { readonly type: 'SET_LOCALE'; readonly locale: string; readonly correlationId: string }
  | { readonly type: 'PING'; readonly correlationId: string };

/** Discriminated union covering the message types sent from preview to host. */
export type MockPreviewToHostMessage =
  | { readonly type: 'READY'; readonly version: string }
  | { readonly type: 'MOUNTED'; readonly correlationId: string; readonly durationMs: number }
  | { readonly type: 'UPDATED'; readonly correlationId: string; readonly durationMs: number }
  | { readonly type: 'ERROR'; readonly correlationId: string; readonly code: string; readonly message: string; readonly stack?: string }
  | { readonly type: 'ELEMENT_CLICK'; readonly nodeId: string; readonly coordinates: { readonly x: number; readonly y: number } }
  | { readonly type: 'ELEMENT_HOVER'; readonly nodeId: string | null }
  | { readonly type: 'PONG'; readonly correlationId: string };

/**
 * Mock interface for `PreviewHostService` — mirrors the public interface
 * exported by `@ghatana/ui-builder/preview`.
 */
export interface MockPreviewHostService {
  send: ReturnType<typeof vi.fn<[MockHostToPreviewMessage], void>>;
  onMessage: ReturnType<typeof vi.fn<[handler: (msg: MockPreviewToHostMessage) => void], () => void>>;
  mount: ReturnType<typeof vi.fn<[document: unknown, sandbox: PreviewSandboxProfile], Promise<void>>>;
  update: ReturnType<typeof vi.fn<[document: unknown], Promise<void>>>;
  teardown: ReturnType<typeof vi.fn<[], Promise<void>>>;
  /**
   * Utility: simulate a message arriving from the preview side.
   * Calls all currently registered handlers from `onMessage`.
   */
  simulateMessage: (message: MockPreviewToHostMessage) => void;
}

// ============================================================================
// Fixtures
// ============================================================================

/**
 * Builds a minimal `PreviewSandboxProfile` fixture for tests.
 * Defaults are chosen to represent a typical trusted local workspace profile.
 */
export function createSandboxProfileFixture(
  overrides: Partial<PreviewSandboxProfile> = {},
): PreviewSandboxProfile {
  return {
    id: 'fixture-profile',
    name: 'Fixture Profile',
    viewport: { width: 1440, height: 900, devicePixelRatio: 1, label: 'Desktop (1440px)' },
    theme: 'default',
    locale: 'en-US',
    featureFlags: {},
    trustedOrigins: ['https://preview.ghatana.dev'],
    ...overrides,
  };
}

/**
 * Builds a minimal `BuilderDocument`-like fixture for tests.
 * The shape matches the structural requirements of `BuilderDocument` without
 * importing from `@ghatana/ui-builder` at runtime.
 */
export function createBuilderDocumentFixture(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
  return {
    id: 'fixture-doc-id',
    schemaVersion: '1.0.0',
    metadata: {
      title: 'Fixture Document',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      trustLevel: 'TRUSTED_WORKSPACE',
      syncStatus: 'synced',
    },
    rootNodeIds: [],
    nodes: {},
    designSystem: {
      tokensVersion: '1.0.0',
      themeId: 'default',
      componentContracts: [],
    },
    ...overrides,
  };
}

// ============================================================================
// Mock factory
// ============================================================================

/**
 * Creates a fully-mocked `PreviewHostService` compatible with the interface
 * exported by `@ghatana/ui-builder/preview`.
 *
 * The `simulateMessage` utility on the returned object can be used to
 * trigger message handlers registered via `onMessage` without needing a real
 * iframe.
 *
 * @example
 * ```ts
 * const mockService = createMockPreviewHostService();
 * vi.mock('@ghatana/ui-builder/preview', () => ({ PreviewHostService: vi.fn(() => mockService) }));
 *
 * // Trigger a READY message from the preview side:
 * mockService.simulateMessage({ type: 'READY', version: '1.0.0' });
 * ```
 */
export function createMockPreviewHostService(): MockPreviewHostService {
  const handlers = new Set<(msg: MockPreviewToHostMessage) => void>();

  const mock: MockPreviewHostService = {
    send: vi.fn(),
    onMessage: vi.fn((handler: (msg: MockPreviewToHostMessage) => void) => {
      handlers.add(handler);
      return () => {
        handlers.delete(handler);
      };
    }),
    mount: vi.fn().mockResolvedValue(undefined),
    update: vi.fn().mockResolvedValue(undefined),
    teardown: vi.fn().mockImplementation(async () => {
      handlers.clear();
    }),
    simulateMessage(message: MockPreviewToHostMessage): void {
      for (const handler of handlers) {
        handler(message);
      }
    },
  };

  return mock;
}
