import { describe, expect, it } from 'vitest';
import { renderBadgePreview } from '../fixtures/generated-project/src/index.js';

describe('generated artifact fixture validation', () => {
  it('renders fixture preview content', () => {
    expect(renderBadgePreview({ label: 'Ready', tone: 'neutral' }))
      .toBe('<span data-tone="neutral">Ready</span>');
  });
});
