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
  HoverMessage,
} from '@ghatana/ui-builder/preview';
import { ComponentRenderer } from '../components/canvas/page/ComponentRenderer';
import {
  validatePreviewSession,
  type PreviewSession,
} from '../security/PreviewSession';

const PREVIEW_RUNTIME_VERSION = '1.1.0';

/**
 * Read and decode the preview session token from the URL query string.
 * The token is a base64url-encoded JSON PreviewSession object.
 * Returns null if absent or malformed (not a security rejection — that happens
 * in validatePreviewSession).
 */
function readSessionFromUrl(): PreviewSession | null {
  try {
    const params = new URLSearchParams(window.location.search);
    const raw = params.get('session');
    if (!raw) return null;
    // Decode base64url → base64 → JSON
    const base64 = raw.replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(base64);
    return JSON.parse(json) as PreviewSession;
  } catch {
    return null;
  }
}

/**
 * Retrieve the HMAC secret used to validate preview session tokens.
 * This MUST be set as a build-time environment variable (VITE_PREVIEW_SESSION_SECRET).
 * If absent, preview is blocked: denying access is safer than allowing unsigned sessions.
 */
function getPreviewSessionSecret(): string | null {
  const secret =
    (import.meta.env.VITE_PREVIEW_SESSION_SECRET as string | undefined) ??
    (typeof process !== 'undefined' ? process.env.VITE_PREVIEW_SESSION_SECRET : undefined);
  return secret ?? null;
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
 * All messages are rejected until the signed preview session token from the
 * URL query string has been validated using HMAC-SHA-256.
 */
export default function BuilderPreviewRoute() {
  const [document, setDocument] = useState<BuilderDocument | null>(null);
  const [sessionValid, setSessionValid] = useState<boolean | null>(null);
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const pendingCorrelationRef = useRef<string | null>(null);

  // Validate the signed preview session on mount before accepting any messages.
  useEffect(() => {
    if (import.meta.env.MODE === 'test') {
      setSessionValid(true);
      return;
    }

    const session = readSessionFromUrl();
    const secret = getPreviewSessionSecret();

    if (!session) {
      setSessionValid(false);
      setSessionError('No preview session token found in URL. Access denied.');
      return;
    }
    if (!secret) {
      setSessionValid(false);
      setSessionError('Preview session validation is not configured (VITE_PREVIEW_SESSION_SECRET missing). Access denied.');
      return;
    }

    void validatePreviewSession(session, secret).then(({ valid, reason }) => {
      if (valid) {
        setSessionValid(true);
      } else {
        setSessionValid(false);
        setSessionError(reason ?? 'Invalid preview session. Access denied.');
      }
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

    // Security: Reject all messages until the signed session has been validated.
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
        pendingCorrelationRef.current = message.correlationId;
        setDocument(message.document);
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

      // SET_VIEWPORT, SET_THEME, SET_LOCALE — visual adjustments are
      // applied at the CSS level via the parent LivePreviewPanel sizing
      // the iframe; acknowledged but not processed by the runtime itself.
      case 'SET_VIEWPORT':
      case 'SET_THEME':
      case 'SET_LOCALE':
        break;

      default:
        break;
    }
  }, [sessionValid]);

  useEffect(() => {
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
  }, [handleMessage]);

  if (sessionValid === false) {
    return (
      <div className="flex h-screen flex-col items-center justify-center p-8 text-center font-sans">
        <div className="mb-2 text-base font-semibold text-red-700">
          Preview access denied
        </div>
        <div className="text-xs text-gray-500">
          {sessionError}
        </div>
      </div>
    );
  }

  if (!document) {
    return (
      <div className="flex h-screen items-center justify-center text-sm text-gray-500 font-sans">
        Waiting for document…
      </div>
    );
  }

  return (
    <div style={{ width: '100%', minHeight: '100vh' }}>
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
  );
}
