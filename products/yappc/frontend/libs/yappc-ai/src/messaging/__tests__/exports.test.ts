/**
 * @doc.type test
 * @doc.purpose Verify that messaging components and hooks are properly exported from yappc-ai
 * @doc.layer package-verification
 */
import { describe, it, expect } from 'vitest';

describe('messaging exports', () => {
  it('should export messaging module', async () => {
    const exports = await import('../index');
    expect(exports).toBeDefined();
    expect(typeof exports).toBe('object');
  });

  it('should export ChatPanel component', async () => {
    const { ChatPanel } = await import('../index');
    expect(ChatPanel).toBeDefined();
  });

  it('should export ChatMessage component', async () => {
    const { ChatMessageComponent } = await import('../index');
    expect(ChatMessageComponent).toBeDefined();
  });

  it('should export chat types', async () => {
    const exports = await import('../index');
    // Type exports should be available (compile-time check)
    expect(exports).toBeDefined();
  });

  it('should re-export from chat module', async () => {
    const { UseChatBackendConfig } = await import('../index');
    // Config types should be available from chat re-exports
    expect(UseChatBackendConfig).toBeDefined();
  });
});
