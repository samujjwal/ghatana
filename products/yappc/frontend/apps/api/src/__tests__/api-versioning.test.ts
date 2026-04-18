import { describe, expect, it } from 'vitest';

import {
  buildVersionErrorBody,
  isSupportedVersion,
  isVersionedApiPath,
  resolveRequestedVersion,
} from '../middleware/apiVersioning';

describe('apiVersioning middleware utilities', () => {
  it('detects versioned API route prefixes', () => {
    expect(isVersionedApiPath('/api/v1/workspaces')).toBe(true);
    expect(isVersionedApiPath('/v1/workspaces')).toBe(true);
    expect(isVersionedApiPath('/health')).toBe(false);
  });

  it('extracts request version from x-api-version header', () => {
    const requested = resolveRequestedVersion({
      headers: {
        'x-api-version': 'v1',
      },
    } as never);

    expect(requested).toBe('v1');
    expect(isSupportedVersion(requested!)).toBe(true);
  });

  it('extracts request version from accept-version header', () => {
    const requested = resolveRequestedVersion({
      headers: {
        'accept-version': '1',
      },
    } as never);

    expect(requested).toBe('1');
    expect(isSupportedVersion(requested!)).toBe(true);
  });

  it('builds unsupported-version response payload', () => {
    expect(buildVersionErrorBody('v2')).toMatchObject({
      error: 'Unsupported API version',
      requestedVersion: 'v2',
      currentVersion: 'v1',
      supportedVersions: ['v1'],
    });
  });
});
