import { describe, expect, it } from 'vitest';

import { ALERTS_UNSUPPORTED_MESSAGE, alertsService } from '@/api/alerts.service';

describe('alertsService unsupported boundaries', () => {
  it('fails explicitly for unsupported alert management routes', async () => {
    await expect(alertsService.getAlerts()).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.getAlertGroups()).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.getResolutionSuggestions()).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.acknowledgeAlert('alert-1')).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.resolveAlert('alert-1')).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.resolveGroup('group-1')).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.applySuggestion('suggestion-1')).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.listAlertRules()).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
    await expect(alertsService.createAlertRule({
      name: 'Critical backlog',
      description: 'Track consumer lag',
      severity: 'critical',
      conditionType: 'threshold',
      metric: 'queue_depth',
      operator: 'gt',
      threshold: 1000,
      duration: 5,
      channels: ['email'],
      enabled: true,
    })).rejects.toThrow(ALERTS_UNSUPPORTED_MESSAGE);
  });

  it('returns an inert closed event stream when live alerts are unsupported', () => {
    const stream = alertsService.openStream();

    expect(stream.readyState).toBe(EventSource.CLOSED);
    expect(typeof stream.addEventListener).toBe('function');
    expect(typeof stream.close).toBe('function');
  });
});