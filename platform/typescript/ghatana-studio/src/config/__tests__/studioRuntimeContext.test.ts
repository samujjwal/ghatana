/**
 * studioRuntimeContext.test.ts
 *
 * Tests for Studio runtime context resolution and validation.
 */

import { describe, it, expect } from 'vitest';
import {
  resolveStudioRuntimeContext,
  resolveStudioUserId,
  type StudioRuntimeContextState,
} from '../studioRuntimeContext';

describe('resolveStudioRuntimeContext', () => {
  it('returns configured state when all required env vars are present', () => {
    const env = {
      VITE_GHATANA_KERNEL_API_BASE_URL: 'https://api.ghatana.dev',
      VITE_STUDIO_TENANT_ID: 'tenant-1',
      VITE_STUDIO_WORKSPACE_ID: 'workspace-1',
      VITE_STUDIO_PROJECT_ID: 'project-1',
      VITE_STUDIO_AUTH_TOKEN: 'auth-token-123',
      VITE_STUDIO_USER_ID: 'user-1',
    };

    const context = resolveStudioRuntimeContext(env);

    expect(context).toEqual({
      status: 'configured',
      identity: {
        baseUrl: 'https://api.ghatana.dev',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        authToken: 'auth-token-123',
        userId: 'user-1',
      },
    });
  });

  it('trims whitespace from env var values', () => {
    const env = {
      VITE_GHATANA_KERNEL_API_BASE_URL: '  https://api.ghatana.dev  ',
      VITE_STUDIO_TENANT_ID: '  tenant-1  ',
      VITE_STUDIO_WORKSPACE_ID: '  workspace-1  ',
      VITE_STUDIO_PROJECT_ID: '  project-1  ',
      VITE_STUDIO_AUTH_TOKEN: '  auth-token-123  ',
      VITE_STUDIO_USER_ID: '  user-1  ',
    };

    const context = resolveStudioRuntimeContext(env);

    expect(context.status).toBe('configured');
    if (context.status === 'configured') {
      expect(context.identity.baseUrl).toBe('https://api.ghatana.dev');
      expect(context.identity.tenantId).toBe('tenant-1');
    }
  });

  it('returns unconfigured state when any required env var is missing', () => {
    const env = {
      VITE_GHATANA_KERNEL_API_BASE_URL: 'https://api.ghatana.dev',
      VITE_STUDIO_TENANT_ID: 'tenant-1',
      VITE_STUDIO_WORKSPACE_ID: 'workspace-1',
      VITE_STUDIO_PROJECT_ID: 'project-1',
      VITE_STUDIO_AUTH_TOKEN: 'auth-token-123',
      // VITE_STUDIO_USER_ID missing
    };

    const context = resolveStudioRuntimeContext(env);

    expect(context.status).toBe('unconfigured');
    if (context.status === 'unconfigured') {
      expect(context.missingFields).toContain('VITE_STUDIO_USER_ID');
    }
  });

  it('returns unconfigured state when any required env var is empty string', () => {
    const env = {
      VITE_GHATANA_KERNEL_API_BASE_URL: 'https://api.ghatana.dev',
      VITE_STUDIO_TENANT_ID: 'tenant-1',
      VITE_STUDIO_WORKSPACE_ID: 'workspace-1',
      VITE_STUDIO_PROJECT_ID: '', // empty
      VITE_STUDIO_AUTH_TOKEN: 'auth-token-123',
      VITE_STUDIO_USER_ID: 'user-1',
    };

    const context = resolveStudioRuntimeContext(env);

    expect(context.status).toBe('unconfigured');
    if (context.status === 'unconfigured') {
      expect(context.missingFields).toContain('VITE_STUDIO_PROJECT_ID');
    }
  });

  it('returns unconfigured state when env object is undefined', () => {
    const context = resolveStudioRuntimeContext(undefined);

    expect(context.status).toBe('unconfigured');
    if (context.status === 'unconfigured') {
      expect(context.missingFields).toEqual([
        'VITE_GHATANA_KERNEL_API_BASE_URL',
        'VITE_STUDIO_TENANT_ID',
        'VITE_STUDIO_WORKSPACE_ID',
        'VITE_STUDIO_PROJECT_ID',
        'VITE_STUDIO_AUTH_TOKEN',
        'VITE_STUDIO_USER_ID',
      ]);
    }
  });

  it('returns unconfigured state when env object is empty', () => {
    const context = resolveStudioRuntimeContext({});

    expect(context.status).toBe('unconfigured');
    if (context.status === 'unconfigured') {
      expect(context.missingFields).toEqual([
        'VITE_GHATANA_KERNEL_API_BASE_URL',
        'VITE_STUDIO_TENANT_ID',
        'VITE_STUDIO_WORKSPACE_ID',
        'VITE_STUDIO_PROJECT_ID',
        'VITE_STUDIO_AUTH_TOKEN',
        'VITE_STUDIO_USER_ID',
      ]);
    }
  });
});

describe('resolveStudioUserId', () => {
  it('returns userId when context is configured', () => {
    const context: StudioRuntimeContextState = {
      status: 'configured',
      identity: {
        baseUrl: 'https://api.ghatana.dev',
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        authToken: 'auth-token-123',
        userId: 'user-1',
      },
    };

    const userId = resolveStudioUserId(context);

    expect(userId).toBe('user-1');
  });

  it('returns undefined when context is unconfigured', () => {
    const context: StudioRuntimeContextState = {
      status: 'unconfigured',
      missingFields: ['VITE_STUDIO_USER_ID'],
    };

    const userId = resolveStudioUserId(context);

    expect(userId).toBeUndefined();
  });
});
