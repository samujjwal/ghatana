import { describe, expect, it } from 'vitest';
import { renderBadgePreview } from '../src/index.js';

describe('generated artifact fixture', () => {
  it('renders a deterministic preview payload', () => {
    expect(renderBadgePreview({ label: 'Ready', tone: 'neutral' }))
      .toBe('<span data-tone="neutral">Ready</span>');
  });
});
