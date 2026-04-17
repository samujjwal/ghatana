import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { EmailAlertChannel } from '../monitoring';
import type { Alert } from '../monitoring';

const alert: Alert = {
  id: 'alert-1',
  name: 'High Error Rate',
  status: 'firing',
  severity: 'critical',
  message: 'HTTP errors exceeded threshold',
  labels: {},
  annotations: {},
  triggeredAt: new Date('2026-04-16T10:00:00.000Z'),
};

describe('EmailAlertChannel', () => {
  beforeEach(() => {
    process.env.RESEND_API_KEY = 'resend-test-key';
    global.fetch = vi.fn() as typeof fetch;
  });

  afterEach(() => {
    delete process.env.RESEND_API_KEY;
    vi.restoreAllMocks();
  });

  it('delivers alerts through Resend', async () => {
    vi.mocked(global.fetch).mockResolvedValue({
      ok: true,
      status: 200,
      text: async () => '',
    } as Response);

    const channel = new EmailAlertChannel({
      smtp: {
        host: 'smtp.example.com',
        port: 587,
        secure: false,
        auth: { user: 'user', pass: 'pass' },
      },
      from: 'alerts@tutorputor.local',
      to: ['ops@tutorputor.local'],
    });

    await channel.send(alert);

    expect(global.fetch).toHaveBeenCalledWith(
      'https://api.resend.com/emails',
      expect.objectContaining({
        method: 'POST',
      }),
    );
  });

  it('fails loudly when Resend rejects the request', async () => {
    vi.mocked(global.fetch).mockResolvedValue({
      ok: false,
      status: 500,
      text: async () => 'resend failure',
    } as Response);

    const channel = new EmailAlertChannel({
      smtp: {
        host: 'smtp.example.com',
        port: 587,
        secure: false,
        auth: { user: 'user', pass: 'pass' },
      },
      from: 'alerts@tutorputor.local',
      to: ['ops@tutorputor.local'],
    });

    await expect(channel.send(alert)).rejects.toThrow(
      'Email alert delivery failed with status 500',
    );
  });
});