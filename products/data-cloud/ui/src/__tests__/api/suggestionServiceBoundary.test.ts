import { describe, expect, it } from 'vitest';
import { SUGGESTION_SERVICE_BOUNDARY_MESSAGE } from '@/lib/runtime-boundaries';

import { suggestionService } from '../../api/suggestion.service';

describe('suggestionService', () => {
  it('fails explicitly for unsupported workflow suggestion endpoints', async () => {
    await expect(
      suggestionService.getSuggestions('wf-1', 'filter high value orders', 'field'),
    ).rejects.toThrow(SUGGESTION_SERVICE_BOUNDARY_MESSAGE);

    await expect(
      suggestionService.acceptSuggestion('wf-1', 'sugg-1'),
    ).rejects.toThrow(SUGGESTION_SERVICE_BOUNDARY_MESSAGE);

    await expect(
      suggestionService.rejectSuggestion('wf-1', 'sugg-1', 'not applicable'),
    ).rejects.toThrow(SUGGESTION_SERVICE_BOUNDARY_MESSAGE);
  });
});