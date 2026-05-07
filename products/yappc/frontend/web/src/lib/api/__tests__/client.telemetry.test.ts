import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  evaluateTelemetryConsent,
  telemetry,
  type FrontendErrorReport,
  type TelemetryConsentContext,
} from '../client';

const report: FrontendErrorReport = {
  message: 'Preview crashed',
  stack: 'Error: Preview crashed',
  componentName: 'LivePreviewPanel',
  url: 'http://localhost:7000/p/proj-1/observe',
  userAgent: 'vitest',
};

const consent: TelemetryConsentContext = {
  tenantTelemetryConsent: true,
  userTelemetryConsent: true,
  dataClassification: 'INTERNAL',
  tenantId: 'tenant-1',
  userId: 'user-1',
};

describe('telemetry REST client consent policy', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ accepted: true }), {
          status: 202,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('blocks frontend error reporting when tenant consent is disabled', async () => {
    const result = await telemetry.reportError(report, {
      ...consent,
      tenantTelemetryConsent: false,
    });

    expect(result).toEqual({
      accepted: false,
      blockedReason: 'Tenant telemetry consent is not enabled.',
    });
    expect(fetch).not.toHaveBeenCalled();
  });

  it('blocks sensitive frontend error reporting unless explicitly allowed', async () => {
    const result = evaluateTelemetryConsent({
      ...consent,
      dataClassification: 'SENSITIVE',
    });

    expect(result).toEqual({
      accepted: false,
      blockedReason: 'Telemetry blocked for sensitive data classification.',
    });
  });

  it('posts consented internal frontend errors with consent scope and classification', async () => {
    const result = await telemetry.reportError(report, consent);

    expect(result).toEqual({ accepted: true });
    expect(fetch).toHaveBeenCalledWith(
      '/api/telemetry/frontend-errors',
      expect.objectContaining({
        method: 'POST',
        credentials: 'same-origin',
      }),
    );

    const request = vi.mocked(fetch).mock.calls[0]?.[1];
    const body = JSON.parse(String(request?.body)) as FrontendErrorReport;
    expect(body).toMatchObject({
      message: 'Preview crashed',
      dataClassification: 'INTERNAL',
      tenantId: 'tenant-1',
      userId: 'user-1',
    });
  });
});
