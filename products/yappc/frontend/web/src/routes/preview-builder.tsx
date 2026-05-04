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
} from '@ghatana/ui-builder/preview';
import { ComponentRenderer } from '../components/canvas/page/ComponentRenderer';

const PREVIEW_RUNTIME_VERSION = '1.1.0';

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
 */
export default function BuilderPreviewRoute() {
  const [document, setDocument] = useState<BuilderDocument | null>(null);
  const pendingCorrelationRef = useRef<string | null>(null);

  const handleMessage = useCallback((event: MessageEvent<unknown>): void => {
    // Security: Validate both source and origin to prevent spoofing
    const expectedOrigin = getExpectedParentOrigin();
    if (event.source !== window.parent || event.origin !== expectedOrigin) {
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
  }, []);

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

  if (!document) {
    return (
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          height: '100vh',
          color: '#6b7280',
          fontSize: '0.875rem',
          fontFamily: 'system-ui, sans-serif',
        }}
      >
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
          selectedNodeId={null}
        />
      ))}
    </div>
  );
}
