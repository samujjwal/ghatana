import { describe, expect, it } from 'vitest';
import { getImprovementSuggestions } from '../AutoApplyImprovements';

describe('AutoApplyImprovements', () => {
  it('applies object improvements to non-object values without throwing', () => {
    const [ariaImprovement] = getImprovementSuggestions({ missingAriaLabels: true });

    expect(ariaImprovement?.apply('legacy-value')).toEqual({ ariaLabel: '' });
  });

  it('preserves existing object fields when applying safe improvements', () => {
    const [imageImprovement] = getImprovementSuggestions({ unoptimizedImages: true });

    expect(imageImprovement?.apply({ src: '/hero.png' })).toEqual({
      src: '/hero.png',
      loading: 'lazy',
    });
  });
});
