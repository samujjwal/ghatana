/**
 * Preview Runtime Security Tests
 *
 * Tests for postMessage origin validation, spoofing prevention,
 * and message handling security in the preview-builder route.
 *
 * @doc.type test
 * @doc.purpose Validate postMessage security hardening in preview runtime
 * @doc.layer product
 * @doc.pattern Security Test
 */

import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import BuilderPreviewRoute from '../preview-builder';
import type { BuilderDocument, HostToPreviewMessage } from '@ghatana/ui-builder';

describe('BuilderPreviewRoute - Security', () => {
  let originalReferrer: string;
  let originalLocation: Location;

  beforeEach(() => {
    // Store original values
    originalReferrer = document.referrer;
    originalLocation = window.location;

    // Mock window.location.origin
    Object.defineProperty(window, 'location', {
      writable: true,
      value: { origin: 'https://app.example.com' },
    });

    // Mock document.referrer
    Object.defineProperty(document, 'referrer', {
      writable: true,
      value: 'https://app.example.com/canvas',
    });

    // Mock window.parent
    Object.defineProperty(window, 'parent', {
      writable: true,
      value: { postMessage: vi.fn() },
    });
  });

  afterEach(() => {
    // Restore original values
    Object.defineProperty(document, 'referrer', {
      writable: true,
      value: originalReferrer,
    });
    Object.defineProperty(window, 'location', {
      writable: true,
      value: originalLocation,
    });
    vi.clearAllMocks();
  });

  describe('origin validation', () => {
    it('should reject messages from spoofed origins', () => {
      render(<BuilderPreviewRoute />);

      const maliciousMessage: HostToPreviewMessage = {
        type: 'MOUNT_DOCUMENT',
        correlationId: 'test-123',
        document: {
          rootNodes: [],
          nodes: new Map(),
          metadata: { name: 'Test' },
        } as BuilderDocument,
      };

      // Simulate message from wrong origin
      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://malicious.example.com',
          data: maliciousMessage,
        }),
      );

      // Should not send READY to malicious origin
      expect(window.parent.postMessage).not.toHaveBeenCalled();
    });

    it('should reject messages from wrong source even with correct origin', () => {
      render(<BuilderPreviewRoute />);

      const message: HostToPreviewMessage = {
        type: 'MOUNT_DOCUMENT',
        correlationId: 'test-123',
        document: {
          rootNodes: [],
          nodes: new Map(),
          metadata: { name: 'Test' },
        } as BuilderDocument,
      };

      // Simulate message from wrong source (not parent)
      const wrongSource = { postMessage: vi.fn() };
      window.dispatchEvent(
        new MessageEvent('message', {
          source: wrongSource,
          origin: 'https://app.example.com',
          data: message,
        }),
      );

      // Should not process message from non-parent source
      expect(window.parent.postMessage).not.toHaveBeenCalledWith(
        expect.objectContaining({ type: 'MOUNTED' }),
        expect.any(String),
      );
    });

    it('should accept messages from valid parent with correct origin', async () => {
      render(<BuilderPreviewRoute />);

      const message: HostToPreviewMessage = {
        type: 'MOUNT_DOCUMENT',
        correlationId: 'test-123',
        document: {
          rootNodes: [],
          nodes: new Map(),
          metadata: { name: 'Test' },
        } as BuilderDocument,
      };

      // Simulate valid message
      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: message,
        }),
      );

      await waitFor(() => {
        expect(window.parent.postMessage).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'MOUNTED' }),
          'https://app.example.com',
        );
      });
    });

    it('should send messages with explicit origin, never wildcard', async () => {
      render(<BuilderPreviewRoute />);

      await waitFor(() => {
        expect(window.parent.postMessage).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'READY' }),
          'https://app.example.com',
        );
      });

      // Verify no wildcard was used
      const calls = vi.mocked(window.parent.postMessage).mock.calls;
      calls.forEach(([, origin]) => {
        expect(origin).not.toBe('*');
        expect(origin).toBe('https://app.example.com');
      });
    });

    it('should fall back to window.location.origin when referrer is missing', async () => {
      Object.defineProperty(document, 'referrer', {
        writable: true,
        value: '',
      });

      render(<BuilderPreviewRoute />);

      const message: HostToPreviewMessage = {
        type: 'PING',
        correlationId: 'ping-123',
      };

      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: message,
        }),
      );

      await waitFor(() => {
        expect(window.parent.postMessage).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'PONG' }),
          'https://app.example.com',
        );
      });
    });

    it('should handle referrer parsing errors gracefully', async () => {
      Object.defineProperty(document, 'referrer', {
        writable: true,
        value: 'not-a-valid-url',
      });

      render(<BuilderPreviewRoute />);

      // Should not throw, should fall back to location.origin
      const message: HostToPreviewMessage = {
        type: 'PING',
        correlationId: 'ping-123',
      };

      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: message,
        }),
      );

      await waitFor(() => {
        expect(window.parent.postMessage).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'PONG' }),
          'https://app.example.com',
        );
      });
    });
  });

  describe('message validation', () => {
    it('should reject malformed messages', () => {
      render(<BuilderPreviewRoute />);

      // Send null data
      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: null,
        }),
      );

      // Send non-object data
      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: 'string',
        }),
      );

      // Send object without type
      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: { correlationId: 'test' },
        }),
      );

      // Should not process any of these
      expect(window.parent.postMessage).toHaveBeenCalledTimes(1); // Only READY message
    });

    it('should reject unknown message types', () => {
      render(<BuilderPreviewRoute />);

      const unknownMessage = {
        type: 'UNKNOWN_TYPE',
        correlationId: 'test-123',
      } as unknown as HostToPreviewMessage;

      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: unknownMessage,
        }),
      );

      // Should not crash, should ignore unknown type
      expect(window.parent.postMessage).toHaveBeenCalledTimes(1); // Only READY message
    });
  });

  describe('CSP and sandbox behavior', () => {
    it('should work when document.referrer is empty (sandboxed iframe)', async () => {
      Object.defineProperty(document, 'referrer', {
        writable: true,
        value: '',
      });

      render(<BuilderPreviewRoute />);

      const message: HostToPreviewMessage = {
        type: 'PING',
        correlationId: 'ping-123',
      };

      window.dispatchEvent(
        new MessageEvent('message', {
          source: window.parent,
          origin: 'https://app.example.com',
          data: message,
        }),
      );

      await waitFor(() => {
        expect(window.parent.postMessage).toHaveBeenCalledWith(
          expect.objectContaining({ type: 'PONG' }),
          'https://app.example.com',
        );
      });
    });
  });
});
