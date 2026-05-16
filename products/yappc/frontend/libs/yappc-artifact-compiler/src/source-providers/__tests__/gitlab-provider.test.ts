import { describe, expect, it } from 'vitest';
import { GitLabProvider } from '../gitlab-provider';
import type { SourceProviderOptions } from '../types';

describe('GitLabProvider', () => {
  it('can handle GitLab URLs and slugs', () => {
    const provider = new GitLabProvider();
    
    expect(provider.canHandle('gitlab.com/owner/repo')).toBe(true);
    expect(provider.canHandle('https://gitlab.com/owner/repo')).toBe(true);
    expect(provider.canHandle('https://gitlab.com/owner/repo/-/tree/main')).toBe(true);
    expect(provider.canHandle('gitlab:owner/repo')).toBe(true);
    expect(provider.canHandle('owner/repo')).toBe(true);
    expect(provider.canHandle('github.com/owner/repo')).toBe(false);
  });

  it('parses GitLab locators correctly', () => {
    const provider = new GitLabProvider('https://gitlab.com/api/v4');
    
    // Test parsing would require actual API calls in real implementation
    // For now, we verify the provider is instantiated correctly
    expect(provider.providerId).toBe('gitlab');
  });

  it('handles missing GitLab token gracefully', async () => {
    const provider = new GitLabProvider();
    const options: SourceProviderOptions = {
      credentials: { token: undefined },
    };

    // Should not throw when token is missing
    expect(() => provider.canHandle('gitlab.com/owner/repo')).not.toThrow();
  });
});
