/**
 * Builder Preview Route — standalone full-page preview runtime.
 *
 * This page is loaded inside an iframe by LivePreviewPanel. It listens for
 * typed postMessages from the host (MOUNT_DOCUMENT, UPDATE_DOCUMENT, TEARDOWN,
 * SET_VIEWPORT, SET_THEME, SET_LOCALE, PING), renders the BuilderDocument with
 * ComponentRenderer, and posts typed response messages back (READY, MOUNTED,
 * UPDATED, ERROR, PONG).
 *
 * The route is intentionally outside the application shell layout so that the
 * iframe receives no navigation chrome and uses the full viewport.
 *
 * @doc.type route
 * @doc.purpose Embedded preview runtime for the page designer live preview
 * @doc.layer product
 * @doc.pattern Route Component
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import type { BuilderDocument } from '@ghatana/ui-builder';
import type {
  HostToPreviewMessage,
  MountedMessage,
  PongMessage,
  PreviewToHostMessage,
  ReadyMessage,
  UpdatedMessage,
  ClickMessage,
  ErrorMessage as PreviewErrorMessage,
  HoverMessage,
  Viewport,
} from '@ghatana/ui-builder/preview';
import { PRESET_VIEWPORTS } from '@ghatana/ui-builder/preview';
import { ComponentRenderer } from '../components/canvas/page/ComponentRenderer';
import { validatePreviewSessionToken } from '../services/preview/PreviewSessionApi';

const PREVIEW_RUNTIME_VERSION = '1.1.0';
const DEFAULT_PREVIEW_ENVIRONMENT = {
  viewport: PRESET_VIEWPORTS.desktop,
  theme: 'default',
  locale: 'en-US',
} as const;

interface PreviewRuntimeEnvironment {
  readonly viewport: Viewport;
  readonly theme: string;
  readonly locale: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function isViewport(value: unknown): value is Viewport {
  return isRecord(value) &&
    typeof value.width === 'number' &&
    Number.isFinite(value.width) &&
    value.width > 0 &&
    typeof value.height === 'number' &&
    Number.isFinite(value.height) &&
    value.height > 0 &&
    typeof value.devicePixelRatio === 'number' &&
    Number.isFinite(value.devicePixelRatio) &&
    value.devicePixelRatio > 0 &&
    isNonEmptyString(value.label);
}

function isPreviewSandboxProfile(value: unknown): value is { readonly viewport: Viewport; readonly theme: string; readonly locale: string } {
  return isRecord(value) &&
    isViewport(value.viewport) &&
    isNonEmptyString(value.theme) &&
    isNonEmptyString(value.locale);
}

function isBuilderDocument(value: unknown): value is BuilderDocument {
  return isRecord(value) &&
    isNonEmptyString(value.id) &&
    isNonEmptyString(value.version) &&
    isNonEmptyString(value.name) &&
    Array.isArray(value.rootNodes) &&
    value.rootNodes.every((nodeId) => typeof nodeId === 'string') &&
    value.nodes instanceof Map &&
    isRecord(value.designSystem) &&
    isRecord(value.metadata);
}

function sendPreviewError(correlationId: string, code: string, message: string): void {
  const error: PreviewErrorMessage = {
    type: 'ERROR',
    correlationId,
    code,
    message,
  };
  sendToHost(error);
}

function readSessionTokenFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search);
  return params.get('session');
}

/**
 * Derive the expected parent origin from document.referrer.
 * Falls back to window.location.origin if referrer is not available.
 * This is a security measure to prevent postMessage spoofing.
 */
function getExpectedParentOrigin(): string {
  try {
    const referrer = document.referrer;
    if (referrer) {
      const url = new URL(referrer);
      return url.origin;
    }
  } catch {
    // If referrer parsing fails, fall back to current origin
    // This is safe because the preview should be same-origin or explicitly configured
  }
  return window.location.origin;
}

/**
 * Send a message to the host with explicit origin targeting.
 * Never uses '*' to prevent postMessage to unintended origins.
 */
function sendToHost(message: PreviewToHostMessage): void {
  const expectedOrigin = getExpectedParentOrigin();
  window.parent.postMessage(message, expectedOrigin);
}

/**
 * Standalone builder preview runtime.
 *
 * Mounted inside an iframe by LivePreviewPanel. Communicates via the typed
 * HostToPreviewMessage / PreviewToHostMessage protocol defined in
 * @ghatana/ui-builder/preview.
 *
 * All messages are rejected until the server-issued preview session token from
 * the URL query string has passed server validation.
 */
export default function BuilderPreviewRoute() {
  const [document, setDocument] = useState<BuilderDocument | null>(null);
  const [sessionValid, setSessionValid] = useState<boolean | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [runtimeEnvironment, setRuntimeEnvironment] = useState<PreviewRuntimeEnvironment>(DEFAULT_PREVIEW_ENVIRONMENT);
  const pendingCorrelationRef = useRef<string | null>(null);

  // Validate the server-issued preview session on mount before accepting any messages.
  useEffect(() => {
    const sessionToken = readSessionTokenFromUrl();
    if (!sessionToken) {
      setSessionValid(false);
      setSessionError('No preview session token found in URL. Access denied.');
      return;
    }

    void validatePreviewSessionToken(sessionToken)
      .then(({ valid, reason }) => {
        if (valid) {
          setSessionValid(true);
        } else {
          setSessionValid(false);
          setSessionError(reason ?? 'Invalid preview session. Access denied.');
        }
      })
      .catch((error: unknown) => {
        setSessionValid(false);
        setSessionError(error instanceof Error ? error.message : 'Preview session validation failed.');
      });
  }, []);

  const handleMessage = useCallback((event: MessageEvent<unknown>): void => {
    // Security: Validate both source and origin to prevent spoofing
    const expectedOrigin = getExpectedParentOrigin();
    if (event.origin !== expectedOrigin) {
      return;
    }
    if (event.source != null && event.source !== window.parent) {
      return;
    }

    // Security: Reject all messages until the preview session has been validated.
    // sessionValid === null means validation is still in progress; wait.
    if (sessionValid !== true) {
      return;
    }

    if (
      typeof event.data !== 'object' ||
      event.data === null ||
      !('type' in event.data)
    ) {
      return;
    }

    const message = event.data as HostToPreviewMessage;
    const start = performance.now();

    switch (message.type) {
      case 'MOUNT_DOCUMENT': {
        if (!isBuilderDocument(message.document) || !isPreviewSandboxProfile(message.sandbox)) {
          sendPreviewError(message.correlationId, 'INVALID_PREVIEW_DOCUMENT', 'Preview document or sandbox profile failed runtime validation.');
          break;
        }

        pendingCorrelationRef.current = message.correlationId;
        setDocument(message.document);
        setRuntimeEnvironment({
          viewport: message.sandbox.viewport,
          theme: message.sandbox.theme,
          locale: message.sandbox.locale,
        });
        // MOUNTED is sent after the state update triggers a render.
        // We use a microtask so the render has a chance to flush.
        const correlationId = message.correlationId;
        void Promise.resolve().then(() => {
          const mounted: MountedMessage = {
            type: 'MOUNTED',
            correlationId,
            durationMs: Math.round(performance.now() - start),
          };
          sendToHost(mounted);
        });
        break;
      }

      case 'UPDATE_DOCUMENT': {
        if (!isBuilderDocument(message.document)) {
          sendPreviewError(message.correlationId, 'INVALID_PREVIEW_DOCUMENT', 'Preview document update failed runtime validation.');
          break;
        }

        setDocument(message.document);
        const correlationId = message.correlationId;
        void Promise.resolve().then(() => {
          const updated: UpdatedMessage = {
            type: 'UPDATED',
            correlationId,
            durationMs: Math.round(performance.now() - start),
          };
          sendToHost(updated);
        });
        break;
      }

      case 'TEARDOWN': {
        setDocument(null);
        setSelectedNodeId(null);
        break;
      }

      case 'SELECT_NODE': {
        setSelectedNodeId(message.nodeId);
        break;
      }

      case 'PING': {
        const pong: PongMessage = {
          type: 'PONG',
          correlationId: message.correlationId,
        };
        sendToHost(pong);
        break;
      }

      case 'SET_VIEWPORT': {
        if (!isViewport(message.viewport)) {
          sendPreviewError(message.correlationId, 'INVALID_PREVIEW_VIEWPORT', 'Preview viewport failed runtime validation.');
          break;
        }

        setRuntimeEnvironment((current) => ({
          ...current,
          viewport: message.viewport,
        }));
        break;
      }

      case 'SET_THEME': {
        if (!isNonEmptyString(message.theme)) {
          sendPreviewError(message.correlationId, 'INVALID_PREVIEW_THEME', 'Preview theme failed runtime validation.');
          break;
        }

        setRuntimeEnvironment((current) => ({
          ...current,
          theme: message.theme,
        }));
        break;
      }

      case 'SET_LOCALE': {
        if (!isNonEmptyString(message.locale)) {
          sendPreviewError(message.correlationId, 'INVALID_PREVIEW_LOCALE', 'Preview locale failed runtime validation.');
          break;
        }

        setRuntimeEnvironment((current) => ({
          ...current,
          locale: message.locale,
        }));
        break;
      }

      default:
        break;
    }
  }, [sessionValid]);

  useEffect(() => {
    if (sessionValid !== true) {
      return;
    }

    window.addEventListener('message', handleMessage);

    // Announce readiness to the host.
    const ready: ReadyMessage = {
      type: 'READY',
      version: PREVIEW_RUNTIME_VERSION,
    };
    sendToHost(ready);

    return () => {
      window.removeEventListener('message', handleMessage);
    };
  }, [handleMessage, sessionValid]);

  if (sessionValid === false) {
    return (
      <div className="flex h-screen flex-col items-center justify-center p-8 text-center font-sans">
        <div className="mb-2 text-base font-semibold text-destructive">
          Preview access denied
        </div>
        <div className="text-xs text-fg-muted">
          {sessionError}
        </div>
      </div>
    );
  }

  if (!document) {
    return (
      <div
        className="flex h-screen items-center justify-center text-sm text-fg-muted font-sans"
        lang={runtimeEnvironment.locale}
      >
        Waiting for document…
      </div>
    );
  }

  const isHighContrast = runtimeEnvironment.theme === 'contrast';
  const textDirection = /^(ar|fa|he|ur)(-|$)/i.test(runtimeEnvironment.locale) ? 'rtl' : 'ltr';

  return (
    <div
      className="min-h-screen transition-colors duration-200"
      data-preview-theme={runtimeEnvironment.theme}
      data-preview-locale={runtimeEnvironment.locale}
      data-preview-viewport-width={runtimeEnvironment.viewport.width}
      data-preview-viewport-height={runtimeEnvironment.viewport.height}
      data-testid="preview-runtime-shell"
      dir={textDirection}
      lang={runtimeEnvironment.locale}
      style={{
        backgroundColor: isHighContrast ? '#000000' : '#ffffff',
        color: isHighContrast ? '#ffffff' : '#111827',
      }}
    >
      <div
        data-testid="preview-runtime-viewport"
        style={{
          marginInline: 'auto',
          maxWidth: `${runtimeEnvironment.viewport.width}px`,
          minHeight: `${runtimeEnvironment.viewport.height}px`,
          outline: isHighContrast ? '2px solid #facc15' : '1px solid transparent',
          width: '100%',
        }}
      >
        {document.rootNodes.map((nodeId) => (
          <ComponentRenderer
            key={nodeId}
            document={document}
            nodeId={nodeId}
            selectedNodeId={selectedNodeId}
            onSelect={(nodeId) => {
              setSelectedNodeId(nodeId);
            }}
            onNodeClick={(nodeId, coordinates) => {
              setSelectedNodeId(nodeId);
              const click: ClickMessage = {
                type: 'ELEMENT_CLICK',
                nodeId,
                coordinates,
              };
              sendToHost(click);
            }}
            onNodeHover={(nodeId) => {
              const hover: HoverMessage = {
                type: 'ELEMENT_HOVER',
                nodeId,
              };
              sendToHost(hover);
            }}
          />
        ))}
      </div>
    </div>
  );
}
