import { describe, expect, it } from 'vitest';
import * as kernelToolchains from '../index.js';

describe('kernel-toolchains public API', () => {
  it('does not expose FakeCommandRunner from production entrypoint', () => {
    expect('FakeCommandRunner' in kernelToolchains).toBe(false);
  });
});
