import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  createPlatformError,
  getAudioVideoPlatformMetrics,
  incrementAudioVideoPlatformMetric,
  normalizeAudioVideoRuntimeConfig,
} from './audioVideoPlatform';

function readFixture(): {
  runtimeConfig: {
    languageTag: string;
    requestTimeoutMs?: number;
    syncToleranceMs: number;
    ttsVoiceId: string;
  };
  error: {
    code: string;
    category: 'validation' | 'runtime';
    retryable: boolean;
    message: string;
  };
} {
  const currentDir = dirname(fileURLToPath(import.meta.url));
  const fixturePath = join(currentDir, '../../../../test-fixtures/media-contract-fixtures.json');
  return JSON.parse(readFileSync(fixturePath, 'utf8')) as {
    runtimeConfig: {
      languageTag: string;
      requestTimeoutMs?: number;
      syncToleranceMs: number;
      ttsVoiceId: string;
    };
    error: {
      code: string;
      category: 'validation' | 'runtime';
      retryable: boolean;
      message: string;
    };
  };
}

describe('audioVideoPlatform', () => {
  it('normalizes runtime configuration safely', () => {
    const config = normalizeAudioVideoRuntimeConfig({
      requestTimeoutMs: 100,
      syncToleranceMs: 5,
      languageTag: '  ',
      ttsVoiceId: ' narrator ',
    });

    expect(config.requestTimeoutMs).toBe(1000);
    expect(config.syncToleranceMs).toBe(20);
    expect(config.languageTag).toBe('en-US');
    expect(config.ttsVoiceId).toBe('narrator');
  });

  it('creates shared platform errors with stable fields', () => {
    const error = createPlatformError('media.processing_failed', 'runtime', true, 'fallback failed');

    expect(error.code).toBe('media.processing_failed');
    expect(error.category).toBe('runtime');
    expect(error.retryable).toBe(true);
    expect(error.message).toBe('fallback failed');
  });

  it('tracks shared runtime metrics', () => {
    const before = getAudioVideoPlatformMetrics();
    incrementAudioVideoPlatformMetric('sttFallbackRequests');
    incrementAudioVideoPlatformMetric('fallbackFailures');
    const after = getAudioVideoPlatformMetrics();

    expect(after.sttFallbackRequests).toBe(before.sttFallbackRequests + 1);
    expect(after.fallbackFailures).toBe(before.fallbackFailures + 1);
  });

  it('matches the shared media contract fixture', () => {
    const fixture = readFixture();
    const config = normalizeAudioVideoRuntimeConfig(fixture.runtimeConfig);
    const error = createPlatformError(
      fixture.error.code,
      fixture.error.category,
      fixture.error.retryable,
      fixture.error.message,
    );

    expect(config.languageTag).toBe('en-GB');
    expect(config.syncToleranceMs).toBe(55);
    expect(config.ttsVoiceId).toBe('piper-en-gb');
    expect(error.code).toBe('media.temporarily_unavailable');
    expect(error.retryable).toBe(true);
  });
});